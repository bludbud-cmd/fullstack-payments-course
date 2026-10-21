/* eslint-env jest */
import type { Account, Activity, Card } from '../src/types';

type Call = {
  method: string;
  path: string;
  headers: Record<string, string>;
  body: any;
};

/**
 * A pretend PayDeck API that lives in memory. Tests install it in place of
 * fetch, so screens talk to it exactly as they would to the real server.
 */
export function installFakeApi() {
  const accounts = new Map<string, Account>();
  const cards = new Map<string, Card>();
  const activity = new Map<string, Activity[]>();
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
  const problem = (status: number, detail: string, code?: string) =>
    reply(status, { title: 'Problem', detail, code });

  function handle(method: string, path: string, body: any) {
    let match: RegExpMatchArray | null;
    if (method === 'POST' && path === '/api/accounts') {
      const account: Account = {
        id: id(),
        ownerName: body.ownerName,
        currency: body.currency,
        balanceCents: 0,
        createdAt: now(),
      };
      accounts.set(account.id, account);
      activity.set(account.id, []);
      return reply(201, account);
    }
    if (
      (match = path.match(/^\/api\/accounts\/([^/]+)$/)) &&
      method === 'GET'
    ) {
      const account = accounts.get(match[1]);
      return account ? reply(200, account) : problem(404, 'Account not found');
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
      const card: Card = {
        id: id(),
        accountId: match[1],
        nickname: body.nickname,
        last4: String(1000 + cards.size),
        status: 'ACTIVE',
        spendingLimitCents: body.spendingLimitCents,
        createdAt: now(),
      };
      cards.set(card.id, card);
      return reply(201, card);
    }
    if ((match = path.match(/^\/api\/cards\/([^/]+)\/(freeze|unfreeze)$/))) {
      const card = cards.get(match[1])!;
      card.status = match[2] === 'freeze' ? 'FROZEN' : 'ACTIVE';
      return reply(200, card);
    }
    if ((match = path.match(/^\/api\/cards\/([^/]+)$/))) {
      const card = cards.get(match[1])!;
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
    calls.push({
      method,
      path,
      headers: (init.headers ?? {}) as Record<string, string>,
      body,
    });
    const failure = failures.findIndex(f => f.path === path);
    if (failure >= 0) {
      const [f] = failures.splice(failure, 1);
      return problem(f.status, f.detail, f.code);
    }
    return handle(method, path, body);
  });
  globalThis.fetch = fetchMock as unknown as typeof fetch;

  return {
    calls,
    /** Opens an account directly, as if the person had done it on an earlier launch. */
    seedAccount(
      ownerName: string,
      currency: Account['currency'],
      balanceCents = 0,
    ) {
      const response = handle('POST', '/api/accounts', { ownerName, currency });
      const account = accounts.get([...accounts.keys()].pop()!) as Account;
      if (balanceCents > 0) {
        handle('POST', `/api/accounts/${account.id}/top-ups`, {
          amountCents: balanceCents,
          description: 'Opening balance',
        });
      }
      return response.status === 201 ? account : null!;
    },
    seedCard(accountId: string, nickname: string) {
      handle('POST', `/api/accounts/${accountId}/cards`, {
        nickname,
        spendingLimitCents: null,
      });
      return [...cards.values()].pop()!;
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
