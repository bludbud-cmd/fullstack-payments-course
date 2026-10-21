import type { Currency } from './money';

// These match the JSON the PayDeck API sends, so the screens will not need
// to change when they move from sample data to the real API in Part 7.

export type Account = {
  id: string;
  ownerName: string;
  currency: Currency;
  balanceCents: number;
  createdAt: string;
};

export type CardStatus = 'ACTIVE' | 'FROZEN' | 'CANCELLED';

export type Card = {
  id: string;
  accountId: string;
  nickname: string;
  last4: string;
  status: CardStatus;
  spendingLimitCents: number | null;
  createdAt: string;
};

export type TransactionType = 'TOP_UP' | 'TRANSFER';

export type Activity = {
  transactionId: string;
  type: TransactionType;
  description: string;
  amountCents: number;
  createdAt: string;
};
