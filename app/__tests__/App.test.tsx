import AsyncStorage from '@react-native-async-storage/async-storage';
import { QueryClient } from '@tanstack/react-query';
import React from 'react';
import { render, screen, userEvent } from '@testing-library/react-native';
import App from '../App';
import { installFakeApi } from '../testing/fakeApi';

let api: ReturnType<typeof installFakeApi>;
let queryClient: QueryClient;

beforeEach(async () => {
  await AsyncStorage.clear();
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

/** Starts the app as someone who opened an account on an earlier launch. */
async function startWithAccount() {
  const account = api.seedAccount('Asha', 'USD', 124_350);
  api.seedCard(account.id, 'Everyday');
  await AsyncStorage.setItem('paydeck.accountId.v1', account.id);
  await render(<App queryClient={queryClient} />);
  await screen.findByText('Hi, Asha');
  return account;
}

it('opens an account on first launch', async () => {
  const user = userEvent.setup();
  await render(<App queryClient={queryClient} />);
  await user.type(await screen.findByLabelText('Your name'), 'Sam');
  await user.press(screen.getByRole('button', { name: 'EUR' }));
  await user.press(screen.getByRole('button', { name: 'Open my account' }));

  expect(await screen.findByText('Hi, Sam')).toBeOnTheScreen();
  expect(screen.getByTestId('balance')).toHaveTextContent('€0.00');
  expect(await AsyncStorage.getItem('paydeck.accountId.v1')).toBeTruthy();
});

it('asks for a name before opening an account', async () => {
  const user = userEvent.setup();
  await render(<App queryClient={queryClient} />);
  await user.press(
    await screen.findByRole('button', { name: 'Open my account' }),
  );
  expect(screen.getByText('Tell us your name')).toBeOnTheScreen();
  expect(api.calls).toHaveLength(0);
});

it('says so when the API cannot be reached', async () => {
  const user = userEvent.setup();
  api.goOffline();
  await render(<App queryClient={queryClient} />);
  await user.type(await screen.findByLabelText('Your name'), 'Sam');
  await user.press(screen.getByRole('button', { name: 'Open my account' }));
  expect(
    await screen.findByText(
      'Could not reach PayDeck. Check that the API is running.',
    ),
  ).toBeOnTheScreen();
});

it('shows the balance from the API', async () => {
  await startWithAccount();
  expect(screen.getByTestId('balance')).toHaveTextContent('$1,243.50');
  expect(screen.getByText('1 active card')).toBeOnTheScreen();
});

it('tops up the wallet with an idempotency key', async () => {
  const user = userEvent.setup();
  await startWithAccount();
  await user.press(screen.getByRole('button', { name: 'Top up' }));
  await user.type(screen.getByLabelText('Amount (USD)'), '25.50');
  await user.press(screen.getByRole('button', { name: 'Add $25.50' }));

  expect(await screen.findByText('$1,269.00')).toBeOnTheScreen();
  const topUp = api.calls.find(call => call.path.endsWith('/top-ups'))!;
  expect(topUp.body).toEqual({ amountCents: 2550, description: 'Top-up' });
  expect(topUp.headers['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/);
});

it('explains what is wrong with an invalid amount', async () => {
  const user = userEvent.setup();
  await startWithAccount();
  await user.press(screen.getByRole('button', { name: 'Top up' }));
  await user.type(screen.getByLabelText('Amount (USD)'), 'lots');
  await user.press(screen.getByRole('button', { name: 'Add money' }));
  expect(
    screen.getByText('Enter an amount like 25 or 25.50'),
  ).toBeOnTheScreen();
});

it('shows the message the API sends back when a top-up is refused', async () => {
  const user = userEvent.setup();
  const account = await startWithAccount();
  api.failNext(
    `/api/accounts/${account.id}/top-ups`,
    422,
    'Top-ups are paused',
    'PAUSED',
  );
  await user.press(screen.getByRole('button', { name: 'Top up' }));
  await user.press(screen.getByRole('button', { name: '10' }));
  await user.press(screen.getByRole('button', { name: 'Add $10.00' }));
  expect(await screen.findByText('Top-ups are paused')).toBeOnTheScreen();
});

it('sends the same key again when a top-up is retried', async () => {
  const user = userEvent.setup();
  const account = await startWithAccount();
  api.failNext(`/api/accounts/${account.id}/top-ups`, 500, 'Try again later');
  await user.press(screen.getByRole('button', { name: 'Top up' }));
  await user.press(screen.getByRole('button', { name: '10' }));
  await user.press(screen.getByRole('button', { name: 'Add $10.00' }));
  await screen.findByText('Try again later');
  await user.press(screen.getByRole('button', { name: 'Add $10.00' }));
  await screen.findByText('$1,253.50');

  const keys = api.calls
    .filter(call => call.path.endsWith('/top-ups'))
    .map(call => call.headers['Idempotency-Key']);
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBe(keys[1]);
});

it('freezes a card from its settings', async () => {
  const user = userEvent.setup();
  await startWithAccount();
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

it('issues a new card', async () => {
  const user = userEvent.setup();
  await startWithAccount();
  await user.press(screen.getByRole('tab', { name: 'Cards' }));
  await user.press(
    await screen.findByRole('button', { name: 'Issue a new card' }),
  );
  await user.type(screen.getByLabelText('Card name'), 'Travel');
  await user.press(screen.getByRole('button', { name: 'Issue card' }));
  expect(await screen.findByText('Travel')).toBeOnTheScreen();
});
