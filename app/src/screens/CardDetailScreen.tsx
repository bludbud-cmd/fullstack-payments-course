import React, { useState } from 'react';
import { Alert, StyleSheet, Text, View } from 'react-native';
import {
  useCancelCard,
  useCardPayments,
  useCards,
  useFreezeCard,
  useMe,
  useUnfreezeCard,
  useUpdateCard,
} from '../api/hooks';
import { BackButton } from '../components/BackButton';
import { Button } from '../components/Button';
import { ErrorText } from '../components/ErrorText';
import { Field } from '../components/Field';
import { PaymentCardView } from '../components/PaymentCardView';
import { QueryState } from '../components/QueryState';
import { Screen } from '../components/Screen';
import { useNavigation } from '../navigation/NavigationContext';
import type { Card } from '../types';
import { spacing, useTheme } from '../theme';
import { limitError, nicknameError, parseLimit } from '../validation';
import { formatMoney } from '../money';

export function CardDetailScreen({ cardId }: { cardId: string }) {
  const theme = useTheme();
  const me = useMe();
  const cards = useCards();

  if (!me.data || !cards.data) {
    return (
      <Screen title="Card" leading={<BackButton />}>
        <QueryState queries={[me, cards]} />
      </Screen>
    );
  }
  const own = cards.data.find(c => c.id === cardId);
  const shared = me.data.heldCards.find(c => c.id === cardId);
  if (own) {
    return <CardSettings card={own} />;
  }
  if (shared) {
    return <SharedCard card={shared} />;
  }
  return (
    <Screen title="Card not found" leading={<BackButton />}>
      <Text style={{ color: theme.muted }}>This card no longer exists.</Text>
    </Screen>
  );
}

/** A card someone else gave you: you can freeze it if it is lost, and that is all. */
function SharedCard({ card }: { card: Card }) {
  const theme = useTheme();
  const freeze = useFreezeCard();
  return (
    <Screen title={card.nickname} leading={<BackButton />}>
      <PaymentCardView card={card} />
      <Text style={{ color: theme.muted }}>
        This card was shared with you. If you lose it, freeze it straight away.
        Only the account owner can unfreeze, change or cancel it.
      </Text>
      <PayAndPayments card={card} />
      {card.status === 'ACTIVE' && (
        <Button
          title="Freeze card"
          variant="secondary"
          onPress={() => freeze.mutate(card.id)}
        />
      )}
      <ErrorText error={freeze.error} />
    </Screen>
  );
}

/** A card on your own account: everything can be changed. */
function CardSettings({ card }: { card: Card }) {
  const { goBack } = useNavigation();
  const update = useUpdateCard();
  const freeze = useFreezeCard();
  const unfreeze = useUnfreezeCard();
  const cancel = useCancelCard();

  const [nickname, setNickname] = useState(card.nickname);
  const [limit, setLimit] = useState(
    card.spendingLimitCents != null
      ? String(card.spendingLimitCents / 100)
      : '',
  );
  const [saved, setSaved] = useState(false);
  const errors = {
    nickname: nicknameError(nickname),
    limit: limitError(limit),
  };
  const failure =
    update.error ?? freeze.error ?? unfreeze.error ?? cancel.error;

  const save = () => {
    if (!errors.nickname && !errors.limit) {
      update.mutate(
        {
          cardId: card.id,
          nickname: nickname.trim(),
          spendingLimitCents: parseLimit(limit),
        },
        { onSuccess: () => setSaved(true) },
      );
    }
  };

  const confirmCancel = () =>
    Alert.alert(
      'Cancel this card?',
      'A cancelled card can never be used again.',
      [
        { text: 'Keep card', style: 'cancel' },
        {
          text: 'Cancel card',
          style: 'destructive',
          onPress: () => cancel.mutate(card.id, { onSuccess: goBack }),
        },
      ],
    );

  return (
    <Screen title={card.nickname} leading={<BackButton />}>
      <PaymentCardView card={card} />
      <PayAndPayments card={card} />

      {card.status === 'ACTIVE' && (
        <Button
          title="Freeze card"
          variant="secondary"
          onPress={() => freeze.mutate(card.id)}
        />
      )}
      {card.status === 'FROZEN' && (
        <Button
          title="Unfreeze card"
          onPress={() => unfreeze.mutate(card.id)}
        />
      )}

      <View style={{ gap: spacing.m }}>
        <Field
          label="Card name"
          value={nickname}
          onChangeText={text => {
            setNickname(text);
            setSaved(false);
          }}
          maxLength={50}
          error={errors.nickname}
        />
        <Field
          label="Spending limit per payment"
          value={limit}
          onChangeText={text => {
            setLimit(text);
            setSaved(false);
          }}
          keyboardType="decimal-pad"
          placeholder="No limit"
          error={errors.limit}
        />
        <Button
          title={
            update.isPending ? 'Saving…' : saved ? 'Saved' : 'Save changes'
          }
          variant="secondary"
          onPress={save}
          disabled={update.isPending}
        />
      </View>

      <ErrorText error={failure} />
      <Button title="Cancel card" variant="danger" onPress={confirmCancel} />
    </Screen>
  );
}

/** A button to try the card at a pretend shop, and the card's last few payments. */
function PayAndPayments({ card }: { card: Card }) {
  const theme = useTheme();
  const { navigate } = useNavigation();
  const payments = useCardPayments(card.id);
  const recent = payments.data?.slice(0, 5) ?? [];
  return (
    <View style={{ gap: spacing.s }}>
      {card.status !== 'CANCELLED' && (
        <Button
          title="Pay a shop"
          onPress={() => navigate({ name: 'pay', cardId: card.id })}
        />
      )}
      {recent.length > 0 && (
        <Text style={[styles.heading, { color: theme.text }]}>
          Recent payments
        </Text>
      )}
      {recent.map(payment => (
        <View key={payment.id} style={styles.payment}>
          <View style={styles.paymentText}>
            <Text style={{ color: theme.text }}>{payment.merchant}</Text>
            {payment.status === 'DECLINED' && (
              <Text style={{ color: theme.negative }}>
                Declined: {payment.declineMessage}
              </Text>
            )}
            {payment.status === 'REFUNDED' && (
              <Text style={{ color: theme.muted }}>Refunded</Text>
            )}
          </View>
          <Text
            style={{
              color: payment.status === 'APPROVED' ? theme.text : theme.muted,
            }}
          >
            {formatMoney(payment.amountCents, card.currency)}
          </Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  heading: { fontSize: 17, fontWeight: '700', marginTop: spacing.s },
  payment: { flexDirection: 'row', alignItems: 'center', gap: spacing.m },
  paymentText: { flex: 1, gap: 2 },
});
