/* eslint-env jest */
import type { Account, Activity, Card, User } from '../src/types';

type Call = {
  method: string;
  path: string;
  headers: Record<string, string>;
  body: any;
};

type StoredUser = User & { password: string };

/**
 * A pretend PayDeck API that lives in memory. Tests install it in place of
 * fetch, so screens talk to it exactly as they would to the real server.
 */
export function installFakeApi() {
  const users = new Map<string, StoredUser>();
  const accounts = new Map<string, Account & { ownerId: string }>();
  const cards = new Map<string, Card>();
  const activity = new Map<string, Activity[]>();
  const accessTokens = new Map<string, string>(); // token -> user ID
  const refreshTokens = new Map<string, string>();
  const loginCodes = new Map<string, string>();
  const calls: Call[] = [];
  let nextId = 1;
  const id = () =>
    `00000000-0000-4000-8000-${String(nextId++).padStart(12, '0')}`;
  const now = () => new Date('2026-10-21T12:00:00Z').toISOString();

  const reply = (status: number, body?: unknown) => ({
    status,
    ok: status >= 200 && status < 300,
    json: async () => body,
  });
  const problem = (
    status: number,
    detail: string,
    code?: string,
    fields?: Record<string, string>,
  ) => reply(status, { title: 'Problem', detail, code, fields });

  const publicUser = ({ password: _password, ...user }: StoredUser): User =>
    user;

  function signedIn(user: StoredUser, status = 200) {
    const accessToken = `access-${id()}`;
    const refreshToken = `refresh-${id()}`;
    accessTokens.set(accessToken, user.id);
    refreshTokens.set(refreshToken, user.id);
    return reply(status, {
      accessToken,
      refreshToken,
      tokenType: 'Bearer',
      expiresIn: 900,
      user: publicUser(user),
    });
  }

  function addUser(displayName: string, email: string, password: string) {
    const user: StoredUser = {
      id: id(),
      email: email.toLowerCase(),
      displayName,
      role: 'CUSTOMER',
      password,
    };
    users.set(user.id, user);
    return user;
  }

  function openAccount(ownerId: string, currency: Account['currency']) {
    const account = {
      id: id(),
      ownerId,
      ownerName: users.get(ownerId)!.displayName,
      currency,
      balanceCents: 0,
      createdAt: now(),
    };
    accounts.set(account.id, account);
    activity.set(account.id, []);
    return account;
  }

  function issueCard(
    accountId: string,
    nickname: string,
    cardholderId: string | null = null,
  ) {
    const card: Card = {
      id: id(),
      accountId,
      currency: accounts.get(accountId)!.currency,
      nickname,
      last4: String(1000 + cards.size),
      status: 'ACTIVE',
      spendingLimitCents: null,
      cardholderId,
      createdAt: now(),
    };
    cards.set(card.id, card);
    return card;
  }

  const accountView = ({
    ownerId: _owner,
    ...account
  }: Account & { ownerId: string }) => account;

  function handle(
    method: string,
    path: string,
    body: any,
    userId: string | null,
  ) {
    let match: RegExpMatchArray | null;

    // Signing in: no token needed.
    if (method === 'POST' && path === '/api/auth/register') {
      if ([...users.values()].some(u => u.email === body.email.toLowerCase())) {
        return problem(
          409,
          'An account with this email already exists. Try signing in.',
          'EMAIL_TAKEN',
        );
      }
      return signedIn(
        addUser(body.displayName, body.email, body.password),
        201,
      );
    }
    if (method === 'POST' && path === '/api/auth/login') {
      const user = [...users.values()].find(
        u => u.email === body.email.toLowerCase(),
      );
      return user && user.password === body.password
        ? signedIn(user)
        : problem(
            401,
            'That email and password do not match.',
            'INVALID_CREDENTIALS',
          );
    }
    if (method === 'POST' && path === '/api/auth/refresh') {
      const owner = refreshTokens.get(body.refreshToken);
      refreshTokens.delete(body.refreshToken); // each refresh token works once
      return owner
        ? signedIn(users.get(owner)!)
        : problem(
            401,
            'Your session has ended. Please sign in again.',
            'INVALID_CREDENTIALS',
          );
    }
    if (method === 'POST' && path === '/api/auth/logout') {
      refreshTokens.delete(body.refreshToken);
      return reply(204);
    }
    if (method === 'POST' && path === '/api/auth/token') {
      const owner = loginCodes.get(`${body.code}:${body.codeVerifier}`);
      return owner
        ? signedIn(users.get(owner)!)
        : problem(
            401,
            'This sign-in link has expired. Please try again.',
            'INVALID_CREDENTIALS',
          );
    }

    // Everything else needs a valid access token.
    if (!userId) {
      return problem(401, 'Sign in to continue.', 'NOT_SIGNED_IN');
    }
    if (method === 'GET' && path === '/api/me') {
      return reply(200, {
        user: publicUser(users.get(userId)!),
        accounts: [...accounts.values()]
          .filter(a => a.ownerId === userId)
          .map(accountView),
        heldCards: [...cards.values()].filter(c => c.cardholderId === userId),
      });
    }
    if (method === 'POST' && path === '/api/accounts') {
      return reply(201, accountView(openAccount(userId, body.currency)));
    }
    if ((match = path.match(/^\/api\/accounts\/([^/]+)/))) {
      const account = accounts.get(match[1]);
      if (!account || account.ownerId !== userId) {
        return problem(404, 'Account not found');
      }
    }
    if ((match = path.match(/^\/api\/accounts\/([^/]+)$/))) {
      return reply(200, accountView(accounts.get(match[1])!));
    }
    if ((match = path.match(/^\/api\/accounts\/([^/]+)\/transactions$/))) {
      return reply(200, activity.get(match[1]) ?? []);
    }
    if ((match = path.match(/^\/api\/accounts\/([^/]+)\/top-ups$/))) {
      const account = accounts.get(match[1])!;
      account.balanceCents += body.amountCents;
      activity.get(account.id)!.unshift({
        transactionId: id(),
        type: 'TOP_UP',
        description: body.description,
        amountCents: body.amountCents,
        createdAt: now(),
      });
      return reply(201, { transactionId: id() });
    }
    if ((match = path.match(/^\/api\/accounts\/([^/]+)\/cards$/))) {
      if (method === 'GET') {
        return reply(
          200,
          [...cards.values()].filter(card => card.accountId === match![1]),
        );
      }
      let holder: string | null = null;
      if (body.cardholderEmail) {
        const found = [...users.values()].find(
          u => u.email === body.cardholderEmail.toLowerCase(),
        );
        if (!found) {
          return problem(
            422,
            'Nobody with that email uses PayDeck yet. Ask them to sign up first.',
            'CARDHOLDER_NOT_FOUND',
          );
        }
        holder = found.id;
      }
      const card = issueCard(match[1], body.nickname, holder);
      card.spendingLimitCents = body.spendingLimitCents;
      return reply(201, card);
    }
    if ((match = path.match(/^\/api\/cards\/([^/]+)/))) {
      const card = cards.get(match[1]);
      const isOwner = card && accounts.get(card.accountId)!.ownerId === userId;
      const isHolder = card && card.cardholderId === userId;
      if (!card || !(isOwner || isHolder)) {
        return problem(404, 'Card not found');
      }
      const action = path.split('/')[4] ?? method;
      if (!isOwner && action !== 'freeze' && action !== 'GET') {
        return problem(
          403,
          'You are not allowed to do this to this card.',
          'FORBIDDEN',
        );
      }
      if (action === 'freeze' || action === 'unfreeze') {
        card.status = action === 'freeze' ? 'FROZEN' : 'ACTIVE';
        return reply(200, card);
      }
      if (method === 'DELETE') {
        card.status = 'CANCELLED';
        return reply(204);
      }
      card.nickname = body.nickname;
      card.spendingLimitCents = body.removeSpendingLimit
        ? null
        : body.spendingLimitCents;
      return reply(200, card);
    }
    return problem(404, `No fake for ${method} ${path}`);
  }

  /** Make the next request to this path fail with the given status and message. */
  const failures: {
    path: string;
    status: number;
    detail: string;
    code?: string;
  }[] = [];

  const fetchMock = jest.fn(async (url: string, init: RequestInit = {}) => {
    const method = init.method ?? 'GET';
    const path = url.replace(/^https?:\/\/[^/]+/, '');
    const body = init.body ? JSON.parse(String(init.body)) : undefined;
    const headers = (init.headers ?? {}) as Record<string, string>;
    calls.push({ method, path, headers, body });
    const failure = failures.findIndex(f => f.path === path);
    if (failure >= 0) {
      const [f] = failures.splice(failure, 1);
      return problem(f.status, f.detail, f.code);
    }
    const token = headers.Authorization?.replace('Bearer ', '');
    return handle(
      method,
      path,
      body,
      (token && accessTokens.get(token)) || null,
    );
  });
  globalThis.fetch = fetchMock as unknown as typeof fetch;

  return {
    calls,
    /** Signs someone up and opens an account, as if they had done it on an earlier launch. */
    seedUser(
      displayName: string,
      currency: Account['currency'] | null = 'USD',
      balanceCents = 0,
    ) {
      const user = addUser(
        displayName,
        `${displayName.toLowerCase()}@example.com`,
        'correct horse battery',
      );
      const account = currency ? openAccount(user.id, currency) : null;
      if (account && balanceCents > 0) {
        account.balanceCents = balanceCents;
        activity.get(account.id)!.push({
          transactionId: id(),
          type: 'TOP_UP',
          description: 'Opening balance',
          amountCents: balanceCents,
          createdAt: now(),
        });
      }
      return { user, account };
    },
    /** A refresh token for this user, as the Keychain would hold after an earlier sign-in. */
    refreshTokenFor(userId: string) {
      const token = `refresh-${id()}`;
      refreshTokens.set(token, userId);
      return token;
    },
    seedCard(
      accountId: string,
      nickname: string,
      cardholderId: string | null = null,
    ) {
      return issueCard(accountId, nickname, cardholderId);
    },
    /** What the API would hand the app after Google said yes. */
    googleCodeFor(userId: string, codeVerifier: string) {
      const code = `code-${id()}`;
      loginCodes.set(`${code}:${codeVerifier}`, userId);
      return code;
    },
    /** Every access token stops working, as if 15 minutes had passed. */
    expireAccessTokens() {
      accessTokens.clear();
    },
    isRefreshTokenValid(token: string) {
      return refreshTokens.has(token);
    },
    failNext(path: string, status: number, detail: string, code?: string) {
      failures.push({ path, status, detail, code });
    },
    goOffline() {
      fetchMock.mockImplementation(async () => {
        throw new TypeError('Network request failed');
      });
    },
  };
}
