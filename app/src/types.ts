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

export type Role = 'CUSTOMER' | 'SUPPORT' | 'ADMIN';

export type User = {
  id: string;
  email: string;
  displayName: string;
  role: Role;
};

export type CardStatus = 'ACTIVE' | 'FROZEN' | 'CANCELLED';

export type Card = {
  id: string;
  accountId: string;
  /** The currency of the account the card spends from. */
  currency: Currency;
  nickname: string;
  last4: string;
  status: CardStatus;
  spendingLimitCents: number | null;
  /** Someone the owner gave this card to, or null if the owner carries it. */
  cardholderId: string | null;
  createdAt: string;
};

export type TransactionType = 'TOP_UP' | 'TRANSFER' | 'CARD_PAYMENT' | 'REFUND';

export type Activity = {
  transactionId: string;
  type: TransactionType;
  description: string;
  amountCents: number;
  createdAt: string;
};

export type DeclineReason =
  | 'CARD_FROZEN'
  | 'CARD_CANCELLED'
  | 'OVER_LIMIT'
  | 'INSUFFICIENT_FUNDS';

/** One attempt to pay a shop with a card. */
export type CardPayment = {
  id: string;
  cardId: string;
  merchant: string;
  amountCents: number;
  status: 'APPROVED' | 'DECLINED' | 'REFUNDED';
  declineReason: DeclineReason | null;
  /** Why it was declined, in words for people. */
  declineMessage: string | null;
  createdAt: string;
};

export type NotificationSettings = { payments: boolean; declines: boolean };

/** What the API sends over the WebSocket when something happens. */
export type LiveMessage = {
  accountId: string;
  kind:
    | 'MONEY_IN'
    | 'MONEY_OUT'
    | 'CARD_PAYMENT'
    | 'CARD_DECLINED'
    | 'REFUND'
    | 'CARD_UPDATED';
  title: string;
  body: string;
  cardId: string | null;
  balanceCents: number;
  at: string;
};
