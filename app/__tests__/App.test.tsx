import React from 'react';
import { render, screen, userEvent } from '@testing-library/react-native';
import App from '../App';

it('shows the wallet balance on the first screen', async () => {
  await render(<App />);
  expect(screen.getByText('$1,243.50')).toBeOnTheScreen();
  expect(screen.getByText('Hi, Asha')).toBeOnTheScreen();
});

it('tops up the wallet', async () => {
  const user = userEvent.setup();
  await render(<App />);
  await user.press(screen.getByRole('button', { name: 'Top up' }));
  await user.type(screen.getByLabelText('Amount (USD)'), '25.50');
  await user.press(screen.getByRole('button', { name: 'Add $25.50' }));
  expect(screen.getByText('$1,269.00')).toBeOnTheScreen();
});

it('explains what is wrong with an invalid amount', async () => {
  const user = userEvent.setup();
  await render(<App />);
  await user.press(screen.getByRole('button', { name: 'Top up' }));
  await user.type(screen.getByLabelText('Amount (USD)'), 'lots');
  await user.press(screen.getByRole('button', { name: 'Add money' }));
  expect(
    screen.getByText('Enter an amount like 25 or 25.50'),
  ).toBeOnTheScreen();
});

it('freezes a card from its settings', async () => {
  const user = userEvent.setup();
  await render(<App />);
  await user.press(screen.getByRole('tab', { name: 'Cards' }));
  await user.press(screen.getByLabelText('Everyday card ending 4821, Active'));
  await user.press(screen.getByRole('button', { name: 'Freeze card' }));
  expect(
    screen.getByLabelText('Everyday card ending 4821, Frozen'),
  ).toBeOnTheScreen();
  expect(
    screen.getByRole('button', { name: 'Unfreeze card' }),
  ).toBeOnTheScreen();
});

it('issues a new card', async () => {
  const user = userEvent.setup();
  await render(<App />);
  await user.press(screen.getByRole('tab', { name: 'Cards' }));
  await user.press(screen.getByRole('button', { name: 'Issue a new card' }));
  await user.type(screen.getByLabelText('Card name'), 'Travel');
  await user.press(screen.getByRole('button', { name: 'Issue card' }));
  expect(screen.getByText('Travel')).toBeOnTheScreen();
});
