import type { Account, Activity, Card } from '../types';

// Made-up data so we can build every screen before the backend exists.

export const sampleAccount: Account = {
  id: 'acc-1',
  ownerName: 'Asha',
  currency: 'USD',
  balanceCents: 124_350,
  createdAt: '2026-09-01T09:00:00Z',
};

export const sampleCards: Card[] = [
  {
    id: 'card-1',
    accountId: 'acc-1',
    nickname: 'Everyday',
    last4: '4821',
    status: 'ACTIVE',
    spendingLimitCents: null,
    createdAt: '2026-09-01T09:05:00Z',
  },
  {
    id: 'card-2',
    accountId: 'acc-1',
    nickname: 'Online shopping',
    last4: '1937',
    status: 'FROZEN',
    spendingLimitCents: 20_000,
    createdAt: '2026-09-12T18:20:00Z',
  },
];

export const sampleActivity: Activity[] = [
  {
    transactionId: 'tx-4',
    type: 'TRANSFER',
    description: 'Dinner with Ben',
    amountCents: -4_250,
    createdAt: '2026-10-20T19:42:00Z',
  },
  {
    transactionId: 'tx-3',
    type: 'TRANSFER',
    description: 'Rent share from Priya',
    amountCents: 60_000,
    createdAt: '2026-10-18T08:10:00Z',
  },
  {
    transactionId: 'tx-2',
    type: 'TRANSFER',
    description: 'Groceries',
    amountCents: -6_400,
    createdAt: '2026-10-15T17:30:00Z',
  },
  {
    transactionId: 'tx-1',
    type: 'TOP_UP',
    description: 'Top-up',
    amountCents: 75_000,
    createdAt: '2026-10-01T12:00:00Z',
  },
];
