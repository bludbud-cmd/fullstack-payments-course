import { QueryClient } from '@tanstack/react-query';
import React from 'react';
import { Linking } from 'react-native';
import {
  act,
  fireEvent,
  render,
  screen,
  userEvent,
  waitFor,
} from '@testing-library/react-native';
import App from '../App';
import { codeChallenge } from '../src/auth/pkce';
import { installFakeApi } from '../testing/fakeApi';

type FakeSocket = {
  url: string;
  sent: string[];
  closed: boolean;
  receive: (text: string) => void;
};
declare const global: {
  fakeKeychain: Map<string, string>;
  fakeSockets: FakeSocket[];
};
const KEYCHAIN_SERVICE = 'dev.paydeck.refresh-token';

let api: ReturnType<typeof installFakeApi>;
let queryClient: QueryClient;

beforeEach(() => {
  global.fakeKeychain.clear();
  global.fakeSockets.length = 0;
  jest.mocked(Linking.openURL).mockClear();
  jest.mocked(Linking.addEventListener).mockClear();
  api = installFakeApi();
  // No retries and no clean-up timers, so tests are quick and exit cleanly.
  queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: Infinity },
      mutations: { gcTime: Infinity },
    },
  });
});

afterEach(() => queryClient.clear());

/** Starts the app as someone who signed in on an earlier launch. */
async function startSignedIn(name = 'Asha', balanceCents = 124_350) {
  const { user, account } = api.seedUser(name, 'USD', balanceCents);
  api.seedCard(account!.id, 'Everyday');
  global.fakeKeychain.set(KEYCHAIN_SERVICE, api.refreshTokenFor(user.id));
  await render(<App queryClient={queryClient} />);
  await screen.findByText(`Hi, ${name}`);
  return { user, account: account! };
}

describe('signing in', () => {
  it('creates an account and opens a wallet', async () => {
    const user = userEvent.setup();
    await render(<App queryClient={queryClient} />);
    await user.press(
      await screen.findByRole('button', { name: 'Create an account' }),
    );
    await user.type(screen.getByLabelText('Your name'), 'Sam');
    await user.type(screen.getByLabelText('Email'), 'sam@example.com');
    await user.type(screen.getByLabelText('Password'), 'a long password');
    await user.press(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Welcome, Sam')).toBeOnTheScreen();
    await user.press(screen.getByRole('button', { name: 'EUR' }));
    await user.press(screen.getByRole('button', { name: 'Open my wallet' }));

    expect(await screen.findByText('Hi, Sam')).toBeOnTheScreen();
    expect(screen.getByTestId('balance')).toHaveTextContent('€0.00');
    // The refresh token is kept in the Keychain, not in plain storage.
    expect(global.fakeKeychain.get(KEYCHAIN_SERVICE)).toMatch(/^refresh-/);
  });

  it('checks the form before sending it', async () => {
    const user = userEvent.setup();
    await render(<App queryClient={queryClient} />);
    await user.press(
      await screen.findByRole('button', { name: 'Create an account' }),
    );
    await user.type(screen.getByLabelText('Email'), 'not an email');
    await user.type(screen.getByLabelText('Password'), 'short');
    await user.press(screen.getByRole('button', { name: 'Create account' }));

    expect(screen.getByText('Tell us your name')).toBeOnTheScreen();
    expect(
      screen.getByText('That does not look like an email address'),
    ).toBeOnTheScreen();
    expect(screen.getByText('Use at least 10 characters')).toBeOnTheScreen();
    expect(api.calls).toHaveLength(0);
  });

  it('signs in with a password', async () => {
    const user = userEvent.setup();
    api.seedUser('Asha', 'USD', 500);
    await render(<App queryClient={queryClient} />);
    await user.type(await screen.findByLabelText('Email'), 'Asha@Example.com');
    await user.type(screen.getByLabelText('Password'), 'correct horse battery');
    await user.press(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Hi, Asha')).toBeOnTheScreen();
  });

  it('shows the API’s message for a wrong password', async () => {
    const user = userEvent.setup();
    api.seedUser('Asha');
    await render(<App queryClient={queryClient} />);
    await user.type(await screen.findByLabelText('Email'), 'asha@example.com');
    await user.type(screen.getByLabelText('Password'), 'wrong password');
    await user.press(screen.getByRole('button', { name: 'Sign in' }));
    expect(
      await screen.findByText('That email and password do not match.'),
    ).toBeOnTheScreen();
  });

  it('says so when the API cannot be reached', async () => {
    const user = userEvent.setup();
    api.goOffline();
    await render(<App queryClient={queryClient} />);
    await user.type(await screen.findByLabelText('Email'), 'asha@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct horse battery');
    await user.press(screen.getByRole('button', { name: 'Sign in' }));
    expect(
      await screen.findByText(
        'Could not reach PayDeck. Check that the API is running.',
      ),
    ).toBeOnTheScreen();
  });

  it('signs in with Google through the browser', async () => {
    const user = userEvent.setup();
    const { user: asha } = api.seedUser('Asha');
    await render(<App queryClient={queryClient} />);
    await user.press(
      await screen.findByRole('button', { name: 'Continue with Google' }),
    );

    // The app opened the API's Google link with a PKCE challenge.
    const opened = jest.mocked(Linking.openURL).mock.calls[0][0];
    expect(opened).toContain(
      '/api/auth/google?redirect_uri=paydeck%3A%2F%2Fauth',
    );
    const challenge = opened.match(/code_challenge=([^&]+)/)![1];

    // Pretend Google said yes: the browser sends the person back with a code
    // that only works with the verifier behind that challenge.
    const verifier = findVerifierFor(challenge);
    const code = api.googleCodeFor(asha.id, verifier);
    await openLink(`paydeck://auth?code=${code}`);

    expect(await screen.findByText('Hi, Asha')).toBeOnTheScreen();
  });

  it('explains a cancelled Google sign-in', async () => {
    const user = userEvent.setup();
    await render(<App queryClient={queryClient} />);
    await user.press(
      await screen.findByRole('button', { name: 'Continue with Google' }),
    );
    await openLink('paydeck://auth?error=access_denied');
    expect(
      await screen.findByText('Google sign-in was cancelled.'),
    ).toBeOnTheScreen();
  });
});

describe('staying signed in', () => {
  it('signs straight back in on the next launch', async () => {
    await startSignedIn();
    expect(screen.getByTestId('balance')).toHaveTextContent('$1,243.50');
  });

  it('gets a new access token after signing in with a password', async () => {
    const user = userEvent.setup();
    api.seedUser('Asha', 'USD', 5_000);
    await render(<App queryClient={queryClient} />);
    await user.type(await screen.findByLabelText('Email'), 'asha@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct horse battery');
    await user.press(screen.getByRole('button', { name: 'Sign in' }));
    await screen.findByText('Hi, Asha');
    api.expireAccessTokens();

    await user.press(screen.getByRole('tab', { name: 'Activity' }));

    expect(await screen.findByText('Opening balance')).toBeOnTheScreen();
    const refreshes = api.calls.filter(
      call => call.path === '/api/auth/refresh',
    );
    expect(refreshes).toHaveLength(1);
    // The expired access token must not go to the refresh endpoint: the API
    // refuses any request that carries a token it does not accept.
    expect(refreshes[0].headers.Authorization).toBeUndefined();
  });

  it('gets a new access token when the old one expires', async () => {
    const user = userEvent.setup();
    await startSignedIn();
    api.expireAccessTokens();

    await user.press(screen.getByRole('tab', { name: 'Activity' }));

    expect(await screen.findByText('Opening balance')).toBeOnTheScreen();
    expect(
      api.calls.filter(call => call.path === '/api/auth/refresh'),
    ).toHaveLength(2);
  });

  it('signs out, and tells the API to forget the session', async () => {
    const user = userEvent.setup();
    await startSignedIn();
    const refreshToken = global.fakeKeychain.get(KEYCHAIN_SERVICE)!;

    await user.press(screen.getByRole('tab', { name: 'You' }));
    await user.press(await screen.findByRole('button', { name: 'Sign out' }));

    expect(await screen.findByText('Sign in to PayDeck')).toBeOnTheScreen();
    expect(global.fakeKeychain.has(KEYCHAIN_SERVICE)).toBe(false);
    expect(api.isRefreshTokenValid(refreshToken)).toBe(false);
  });
});

describe('the wallet', () => {
  it('tops up with an idempotency key', async () => {
    const user = userEvent.setup();
    await startSignedIn();
    await user.press(screen.getByRole('button', { name: 'Top up' }));
    await user.type(screen.getByLabelText('Amount (USD)'), '25.50');
    await user.press(
      screen.getByRole('button', { name: 'Add $25.50 of pretend money' }),
    );

    expect(await screen.findByText('$1,269.00')).toBeOnTheScreen();
    const topUp = api.calls.find(call => call.path.endsWith('/top-ups'))!;
    expect(topUp.body).toEqual({ amountCents: 2550, description: 'Top-up' });
    expect(topUp.headers['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/);
    expect(topUp.headers.Authorization).toMatch(/^Bearer access-/);
  });

  it('explains what is wrong with an invalid amount', async () => {
    const user = userEvent.setup();
    await startSignedIn();
    await user.press(screen.getByRole('button', { name: 'Top up' }));
    await user.type(screen.getByLabelText('Amount (USD)'), 'lots');
    await user.press(screen.getByRole('button', { name: 'Pay by card' }));
    expect(
      screen.getByText('Enter an amount like 25 or 25.50'),
    ).toBeOnTheScreen();
  });

  it('sends the same key again when a top-up is retried', async () => {
    const user = userEvent.setup();
    const { account } = await startSignedIn();
    api.failNext(`/api/accounts/${account.id}/top-ups`, 500, 'Try again later');
    await user.press(screen.getByRole('button', { name: 'Top up' }));
    await user.press(screen.getByRole('button', { name: '10' }));
    const add = { name: 'Add $10.00 of pretend money' };
    await user.press(screen.getByRole('button', add));
    await screen.findByText('Try again later');
    await user.press(screen.getByRole('button', add));
    await screen.findByText('$1,253.50');

    const keys = api.calls
      .filter(call => call.path.endsWith('/top-ups'))
      .map(call => call.headers['Idempotency-Key']);
    expect(keys).toHaveLength(2);
    expect(keys[0]).toBe(keys[1]);
  });

  it('tops up by card through Stripe’s payment page', async () => {
    const user = userEvent.setup();
    const { account } = await startSignedIn();
    await user.press(screen.getByRole('button', { name: 'Top up' }));
    await user.press(screen.getByRole('button', { name: '25' }));
    await user.press(
      screen.getByRole('button', { name: 'Pay $25.00 by card' }),
    );

    const checkout = api.calls.find(call => call.path.endsWith('/checkout'))!;
    expect(checkout.path).toBe(`/api/accounts/${account.id}/top-ups/checkout`);
    expect(checkout.body).toEqual({ amountCents: 2500 });
    expect(Linking.openURL).toHaveBeenCalledWith(
      'https://checkout.stripe.com/c/pay/cs_test_1',
    );
    expect(
      await screen.findByText(/Finish paying in your browser/),
    ).toBeOnTheScreen();

    // Stripe's page sends the person back to the app.
    await openLink('paydeck://top-up?result=success');
    expect(await screen.findByText('Thank you')).toBeOnTheScreen();
  });
});

describe('cards', () => {
  it('freezes a card from its settings', async () => {
    const user = userEvent.setup();
    await startSignedIn();
    await user.press(screen.getByRole('tab', { name: 'Cards' }));
    await user.press(
      await screen.findByLabelText('Everyday card ending 1000, Active'),
    );
    await user.press(screen.getByRole('button', { name: 'Freeze card' }));
    expect(
      await screen.findByLabelText('Everyday card ending 1000, Frozen'),
    ).toBeOnTheScreen();
    expect(
      screen.getByRole('button', { name: 'Unfreeze card' }),
    ).toBeOnTheScreen();
  });

  it('issues a card for someone else', async () => {
    const user = userEvent.setup();
    api.seedUser('Ben', null);
    await startSignedIn();
    await user.press(screen.getByRole('tab', { name: 'Cards' }));
    await user.press(
      await screen.findByRole('button', { name: 'Issue a new card' }),
    );
    await user.type(screen.getByLabelText('Card name'), 'For Ben');
    await user.type(
      screen.getByLabelText('Give this card to (optional)'),
      'ben@example.com',
    );
    await user.press(screen.getByRole('button', { name: 'Issue card' }));

    expect(await screen.findByText('For Ben')).toBeOnTheScreen();
    const issued = api.calls.find(
      call => call.path.endsWith('/cards') && call.method === 'POST',
    )!;
    expect(issued.body.cardholderEmail).toBe('ben@example.com');
  });

  it('lets a cardholder freeze a shared card, and nothing more', async () => {
    const user = userEvent.setup();
    const { account: ashas } = api.seedUser('Asha');
    const { user: ben } = api.seedUser('Ben', 'USD');
    api.seedCard(ashas!.id, 'For Ben', ben.id);
    global.fakeKeychain.set(KEYCHAIN_SERVICE, api.refreshTokenFor(ben.id));
    await render(<App queryClient={queryClient} />);
    await screen.findByText('Hi, Ben');

    await user.press(screen.getByRole('tab', { name: 'Cards' }));
    expect(await screen.findByText('Shared with you')).toBeOnTheScreen();
    await user.press(screen.getByLabelText('For Ben card ending 1000, Active'));
    expect(screen.queryByRole('button', { name: 'Cancel card' })).toBeNull();
    await user.press(screen.getByRole('button', { name: 'Freeze card' }));
    expect(
      await screen.findByLabelText('For Ben card ending 1000, Frozen'),
    ).toBeOnTheScreen();
  });

  it('pays a shop with a card, and says why one was declined', async () => {
    const user = userEvent.setup();
    await startSignedIn('Asha', 5_000);
    await user.press(screen.getByRole('tab', { name: 'Cards' }));
    await user.press(
      await screen.findByLabelText('Everyday card ending 1000, Active'),
    );
    await user.press(screen.getByRole('button', { name: 'Pay a shop' }));
    await user.type(screen.getByLabelText('Amount (USD)'), '12');
    await user.press(screen.getByRole('button', { name: 'Pay' }));
    expect(await screen.findByText('Approved')).toBeOnTheScreen();

    // $38 left: $40 is too much.
    await user.press(screen.getByRole('button', { name: 'Pay again' }));
    await user.type(screen.getByLabelText('Amount (USD)'), '40');
    await user.press(screen.getByRole('button', { name: 'Pay' }));
    expect(await screen.findByText('Declined')).toBeOnTheScreen();
    expect(
      screen.getByText('There is not enough money in the wallet.'),
    ).toBeOnTheScreen();

    // Back on the card, both attempts are listed, newest first.
    await user.press(screen.getByRole('button', { name: 'Back' }));
    expect(await screen.findByText('Recent payments')).toBeOnTheScreen();
    expect(
      screen.getByText('Declined: There is not enough money in the wallet.'),
    ).toBeOnTheScreen();
  });
});

describe('live updates', () => {
  it('signs in to the WebSocket and listens to the right topics', async () => {
    const { user, account } = await startSignedIn();
    const socket = await connectedSocket();

    expect(socket.url).toBe('ws://localhost:8080/ws');
    expect(socket.sent[0]).toMatch(/^CONNECT\n/);
    expect(socket.sent[0]).toMatch(/\nAuthorization:Bearer access-/);
    expect(socket.sent[0].endsWith('\0')).toBe(true);
    expect(socket.sent.slice(1)).toEqual([
      `SUBSCRIBE\nid:sub-0\ndestination:/topic/accounts/${account.id}\n\n\0`,
      `SUBSCRIBE\nid:sub-1\ndestination:/topic/users/${user.id}\n\n\0`,
    ]);
  });

  it('shows what happened, and refreshes the balance', async () => {
    const { account } = await startSignedIn();
    const socket = await connectedSocket();

    // Money arrives some other way, and the API tells the app.
    api.seedTopUp(account.id, 10_000);
    await act(async () =>
      socket.receive(
        stompMessage({
          accountId: account.id,
          kind: 'MONEY_IN',
          title: 'Money in',
          body: '$100.00 added to your wallet',
          cardId: null,
          balanceCents: 134_350,
          at: '2026-10-21T12:00:00Z',
        }),
      ),
    );

    expect(await screen.findByText('Money in')).toBeOnTheScreen();
    expect(screen.getByText('$100.00 added to your wallet')).toBeOnTheScreen();
    expect(await screen.findByText('$1,343.50')).toBeOnTheScreen();
  });

  it('disconnects on sign-out', async () => {
    const user = userEvent.setup();
    await startSignedIn();
    const socket = await connectedSocket();
    await user.press(screen.getByRole('tab', { name: 'You' }));
    await user.press(await screen.findByRole('button', { name: 'Sign out' }));
    await screen.findByText('Sign in to PayDeck');
    expect(socket.closed).toBe(true);
  });
});

describe('notification settings', () => {
  it('turns declined-payment notifications off', async () => {
    const user = userEvent.setup();
    const { user: asha } = await startSignedIn();
    await user.press(screen.getByRole('tab', { name: 'You' }));
    const declines = await screen.findByLabelText('Declined card payments');
    expect(declines).toHaveProp('value', true);

    await act(async () => fireEvent(declines, 'valueChange', false));

    // The switch follows once the API has saved the change.
    await waitFor(() =>
      expect(screen.getByLabelText('Declined card payments')).toHaveProp(
        'value',
        false,
      ),
    );
    expect(api.notificationSettingsFor(asha.id)).toEqual({
      payments: true,
      declines: false,
    });
  });
});

/** Waits for the app's WebSocket, then plays the server saying "connected". */
async function connectedSocket(): Promise<FakeSocket> {
  await waitFor(() => expect(global.fakeSockets).toHaveLength(1));
  const socket = global.fakeSockets[0];
  await waitFor(() => expect(socket.sent).toHaveLength(1));
  await act(async () => socket.receive('CONNECTED\nversion:1.2\n\n\0'));
  return socket;
}

function stompMessage(body: object): string {
  return `MESSAGE\ndestination:/topic/x\ncontent-type:application/json\n\n${JSON.stringify(
    body,
  )}\0`;
}

/** The verifier is random, so read back the one the app made, and check it matches. */
function findVerifierFor(challenge: string): string {
  expect(codeChallenge(mockLastVerifier!)).toBe(challenge);
  return mockLastVerifier!;
}

/** Pretends the operating system opened a link in the app. */
async function openLink(url: string) {
  const handler = jest
    .mocked(Linking.addEventListener)
    .mock.calls.filter(([event]) => event === 'url')
    .pop()![1] as (event: { url: string }) => void;
  await act(async () => handler({ url }));
}

// Jest only lets mock factories use outside variables whose names start with "mock".
let mockLastVerifier: string | null = null;
jest.mock('../src/auth/pkce', () => {
  const actual = jest.requireActual('../src/auth/pkce');
  return {
    ...actual,
    newCodeVerifier: () => {
      mockLastVerifier = actual.newCodeVerifier();
      return mockLastVerifier;
    },
  };
});
