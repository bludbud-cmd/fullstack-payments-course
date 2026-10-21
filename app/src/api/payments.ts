import type { CardPayment, NotificationSettings } from '../types';
import { request } from './client';

// Part 9: real card top-ups, paying shops, and notifications.

/** Asks the API for a Stripe payment page. Open `url` in the browser. */
export const startCardTopUp = (
  accountId: string,
  amountCents: number,
  idempotencyKey: string,
) =>
  request<{ checkoutId: string; url: string }>(
    `/api/accounts/${accountId}/top-ups/checkout`,
    { method: 'POST', body: { amountCents }, idempotencyKey },
  );

/** Plays the shop's card machine: approved or declined, the answer says why. */
export const payWithCard = (
  cardId: string,
  amountCents: number,
  merchant: string,
  idempotencyKey: string,
) =>
  request<CardPayment>(`/api/cards/${cardId}/payments`, {
    method: 'POST',
    body: { amountCents, merchant },
    idempotencyKey,
  });

export const listCardPayments = (cardId: string) =>
  request<CardPayment[]>(`/api/cards/${cardId}/payments`);

export const getNotificationSettings = () =>
  request<NotificationSettings>('/api/me/notifications');

export const setNotificationSettings = (settings: NotificationSettings) =>
  request<NotificationSettings>('/api/me/notifications', {
    method: 'PUT',
    body: settings,
  });

export const registerDevice = (token: string) =>
  request<void>('/api/devices', {
    method: 'POST',
    body: { platform: 'ANDROID', token },
  });

export const unregisterDevice = (token: string) =>
  request<void>('/api/devices/unregister', {
    method: 'POST',
    body: { token },
  });
