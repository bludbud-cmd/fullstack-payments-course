import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAccountId } from '../state/AccountContext';
import { getMe } from './auth';
import * as api from './paydeck';
import * as payments from './payments';

// TanStack Query fetches data, caches it, and refetches it when it may have
// changed. Each "key" names one piece of cached data.
export const keys = {
  me: ['me'] as const,
  account: (id: string) => ['account', id] as const,
  activity: (id: string) => ['activity', id] as const,
  cards: (id: string) => ['cards', id] as const,
};

/** The signed-in person, their accounts, and cards shared with them. */
export function useMe() {
  return useQuery({ queryKey: keys.me, queryFn: getMe });
}

export function useAccount() {
  const accountId = useAccountId();
  return useQuery({
    queryKey: keys.account(accountId),
    queryFn: () => api.getAccount(accountId),
  });
}

export function useActivity() {
  const accountId = useAccountId();
  return useQuery({
    queryKey: keys.activity(accountId),
    queryFn: () => api.getActivity(accountId),
  });
}

export function useCards() {
  const accountId = useAccountId();
  return useQuery({
    queryKey: keys.cards(accountId),
    queryFn: () => api.listCards(accountId),
  });
}

export function useTopUp() {
  const accountId = useAccountId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      amountCents,
      idempotencyKey,
    }: {
      amountCents: number;
      idempotencyKey: string;
    }) => api.topUp(accountId, amountCents, idempotencyKey),
    // The balance and the activity list are now out of date: fetch them again.
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: keys.account(accountId) }),
        queryClient.invalidateQueries({ queryKey: keys.activity(accountId) }),
      ]),
  });
}

/** Every change to a card refreshes the card list, and the cards shared with you. */
function useCardMutation<T>(change: (input: T) => Promise<unknown>) {
  const accountId = useAccountId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: change,
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: keys.cards(accountId) }),
        queryClient.invalidateQueries({ queryKey: keys.me }),
      ]),
  });
}

export function useIssueCard() {
  const accountId = useAccountId();
  return useCardMutation(
    (input: {
      nickname: string;
      spendingLimitCents: number | null;
      cardholderEmail: string | null;
    }) =>
      api.issueCard(
        accountId,
        input.nickname,
        input.spendingLimitCents,
        input.cardholderEmail,
      ),
  );
}

export const useUpdateCard = () =>
  useCardMutation(
    (input: {
      cardId: string;
      nickname: string;
      spendingLimitCents: number | null;
    }) =>
      api.updateCard(input.cardId, input.nickname, input.spendingLimitCents),
  );
export const useFreezeCard = () => useCardMutation(api.freezeCard);
export const useUnfreezeCard = () => useCardMutation(api.unfreezeCard);
export const useCancelCard = () => useCardMutation(api.cancelCard);

export function useCardPayments(cardId: string) {
  return useQuery({
    queryKey: ['cardPayments', cardId],
    queryFn: () => payments.listCardPayments(cardId),
  });
}

export function usePayWithCard(cardId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      amountCents: number;
      merchant: string;
      idempotencyKey: string;
    }) =>
      payments.payWithCard(
        cardId,
        input.amountCents,
        input.merchant,
        input.idempotencyKey,
      ),
    // Money may have left a wallet: refresh everything that shows it.
    onSuccess: () => queryClient.invalidateQueries(),
  });
}

export function useNotificationSettings() {
  return useQuery({
    queryKey: ['notificationSettings'],
    queryFn: payments.getNotificationSettings,
  });
}

export function useSetNotificationSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: payments.setNotificationSettings,
    onSuccess: saved =>
      queryClient.setQueryData(['notificationSettings'], saved),
  });
}
