import type { Currency } from '../money';
import type { Account, Activity, Card } from '../types';
import { request } from './client';

// One function per API endpoint. Screens never call fetch directly.

/** Opens an account for the signed-in person. */
export const openAccount = (currency: Currency) =>
  request<Account>('/api/accounts', { method: 'POST', body: { currency } });

export const getAccount = (accountId: string) =>
  request<Account>(`/api/accounts/${accountId}`);

export const getActivity = (accountId: string) =>
  request<Activity[]>(`/api/accounts/${accountId}/transactions`);

export const topUp = (
  accountId: string,
  amountCents: number,
  idempotencyKey: string,
) =>
  request<unknown>(`/api/accounts/${accountId}/top-ups`, {
    method: 'POST',
    body: { amountCents, description: 'Top-up' },
    idempotencyKey,
  });

export const listCards = (accountId: string) =>
  request<Card[]>(`/api/accounts/${accountId}/cards`);

export const issueCard = (
  accountId: string,
  nickname: string,
  spendingLimitCents: number | null,
  cardholderEmail: string | null,
) =>
  request<Card>(`/api/accounts/${accountId}/cards`, {
    method: 'POST',
    body: { nickname, spendingLimitCents, cardholderEmail },
  });

export const updateCard = (
  cardId: string,
  nickname: string,
  spendingLimitCents: number | null,
) =>
  request<Card>(`/api/cards/${cardId}`, {
    method: 'PATCH',
    body: {
      nickname,
      spendingLimitCents,
      removeSpendingLimit: spendingLimitCents === null,
    },
  });

export const freezeCard = (cardId: string) =>
  request<Card>(`/api/cards/${cardId}/freeze`, { method: 'POST' });

export const unfreezeCard = (cardId: string) =>
  request<Card>(`/api/cards/${cardId}/unfreeze`, { method: 'POST' });

export const cancelCard = (cardId: string) =>
  request<void>(`/api/cards/${cardId}`, { method: 'DELETE' });
