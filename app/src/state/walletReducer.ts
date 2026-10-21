import type { Account, Activity, Card } from '../types';

/** Everything the app knows about the signed-in person's money. */
export type WalletState = {
  account: Account;
  cards: Card[];
  activity: Activity[];
};

export type WalletAction =
  | { type: 'topUp'; amountCents: number; at: string; id: string }
  | {
      type: 'issueCard';
      nickname: string;
      spendingLimitCents: number | null;
      at: string;
      id: string;
      last4: string;
    }
  | { type: 'renameCard'; cardId: string; nickname: string }
  | {
      type: 'setSpendingLimit';
      cardId: string;
      spendingLimitCents: number | null;
    }
  | { type: 'freezeCard'; cardId: string }
  | { type: 'unfreezeCard'; cardId: string }
  | { type: 'cancelCard'; cardId: string };

/**
 * A reducer takes the current state and an action, and returns the next
 * state. It never changes the old state; it builds a new one. That makes
 * every change easy to follow and easy to test.
 */
export function walletReducer(
  state: WalletState,
  action: WalletAction,
): WalletState {
  switch (action.type) {
    case 'topUp':
      return {
        ...state,
        account: {
          ...state.account,
          balanceCents: state.account.balanceCents + action.amountCents,
        },
        activity: [
          {
            transactionId: action.id,
            type: 'TOP_UP',
            description: 'Top-up',
            amountCents: action.amountCents,
            createdAt: action.at,
          },
          ...state.activity,
        ],
      };
    case 'issueCard':
      return {
        ...state,
        cards: [
          ...state.cards,
          {
            id: action.id,
            accountId: state.account.id,
            nickname: action.nickname,
            last4: action.last4,
            status: 'ACTIVE',
            spendingLimitCents: action.spendingLimitCents,
            createdAt: action.at,
          },
        ],
      };
    case 'renameCard':
      return updateCard(state, action.cardId, card =>
        card.status === 'CANCELLED'
          ? card
          : { ...card, nickname: action.nickname },
      );
    case 'setSpendingLimit':
      return updateCard(state, action.cardId, card =>
        card.status === 'CANCELLED'
          ? card
          : { ...card, spendingLimitCents: action.spendingLimitCents },
      );
    case 'freezeCard':
      return updateCard(state, action.cardId, card =>
        card.status === 'ACTIVE' ? { ...card, status: 'FROZEN' } : card,
      );
    case 'unfreezeCard':
      return updateCard(state, action.cardId, card =>
        card.status === 'FROZEN' ? { ...card, status: 'ACTIVE' } : card,
      );
    case 'cancelCard':
      return updateCard(state, action.cardId, card => ({
        ...card,
        status: 'CANCELLED',
      }));
  }
}

function updateCard(
  state: WalletState,
  cardId: string,
  change: (card: Card) => Card,
): WalletState {
  return {
    ...state,
    cards: state.cards.map(card => (card.id === cardId ? change(card) : card)),
  };
}
