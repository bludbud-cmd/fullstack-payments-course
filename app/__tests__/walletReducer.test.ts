import {
  sampleAccount,
  sampleActivity,
  sampleCards,
} from '../src/data/sampleData';
import { walletReducer, type WalletState } from '../src/state/walletReducer';

const start: WalletState = {
  account: sampleAccount,
  cards: sampleCards,
  activity: sampleActivity,
};
const at = '2026-10-21T10:00:00Z';

it('starts with a balance that matches its transactions', () => {
  const total = start.activity.reduce((sum, item) => sum + item.amountCents, 0);
  expect(start.account.balanceCents).toBe(total);
});

it('adds a top-up to the balance and to the top of the activity list', () => {
  const next = walletReducer(start, {
    type: 'topUp',
    amountCents: 2_500,
    at,
    id: 'tx-new',
  });
  expect(next.account.balanceCents).toBe(start.account.balanceCents + 2_500);
  expect(next.activity[0]).toMatchObject({
    transactionId: 'tx-new',
    type: 'TOP_UP',
    amountCents: 2_500,
  });
  expect(start.account.balanceCents).toBe(124_350); // The old state is untouched
});

it('issues an active card', () => {
  const next = walletReducer(start, {
    type: 'issueCard',
    nickname: 'Travel',
    spendingLimitCents: null,
    at,
    id: 'card-new',
    last4: '0042',
  });
  expect(next.cards.at(-1)).toMatchObject({
    id: 'card-new',
    status: 'ACTIVE',
    last4: '0042',
  });
});

it('only freezes active cards and only unfreezes frozen ones', () => {
  const frozen = walletReducer(start, { type: 'freezeCard', cardId: 'card-1' });
  expect(frozen.cards[0].status).toBe('FROZEN');
  const stillFrozen = walletReducer(start, {
    type: 'unfreezeCard',
    cardId: 'card-1',
  });
  expect(stillFrozen.cards[0].status).toBe('ACTIVE');
  expect(
    walletReducer(frozen, { type: 'unfreezeCard', cardId: 'card-1' }).cards[0]
      .status,
  ).toBe('ACTIVE');
});

it('never changes a cancelled card', () => {
  const cancelled = walletReducer(start, {
    type: 'cancelCard',
    cardId: 'card-1',
  });
  const renamed = walletReducer(cancelled, {
    type: 'renameCard',
    cardId: 'card-1',
    nickname: 'New',
  });
  const unfrozen = walletReducer(renamed, {
    type: 'freezeCard',
    cardId: 'card-1',
  });
  expect(unfrozen.cards[0]).toMatchObject({
    status: 'CANCELLED',
    nickname: 'Everyday',
  });
});
