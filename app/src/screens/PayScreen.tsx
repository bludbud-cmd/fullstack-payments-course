import React, { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { newIdempotencyKey } from '../api/client';
import { useCards, useMe, usePayWithCard } from '../api/hooks';
import { BackButton } from '../components/BackButton';
import { Button } from '../components/Button';
import { ErrorText } from '../components/ErrorText';
import { Field } from '../components/Field';
import { PaymentCardView } from '../components/PaymentCardView';
import { Screen } from '../components/Screen';
import { formatMoney, parseMoneyInput } from '../money';
import { spacing, useTheme } from '../theme';
import { topUpError } from '../validation';

/**
 * A pretend shop's card machine. In real life the shop would ask the card
 * network, which would ask PayDeck; here the app asks PayDeck directly.
 */
export function PayScreen({ cardId }: { cardId: string }) {
  const theme = useTheme();
  const me = useMe();
  const cards = useCards();
  const pay = usePayWithCard(cardId);
  const [merchant, setMerchant] = useState('Corner Café');
  const [amount, setAmount] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [key, setKey] = useState(newIdempotencyKey);

  const card =
    cards.data?.find(c => c.id === cardId) ??
    me.data?.heldCards.find(c => c.id === cardId);
  const cents = parseMoneyInput(amount);
  const errors = {
    merchant: merchant.trim() === '' ? 'Name the shop' : null,
    amount: topUpError(amount),
  };

  const submit = () => {
    setSubmitted(true);
    if (!errors.merchant && !errors.amount && cents && !pay.isPending) {
      pay.mutate({
        amountCents: cents,
        merchant: merchant.trim(),
        idempotencyKey: key,
      });
    }
  };

  const again = () => {
    pay.reset();
    setKey(newIdempotencyKey());
    setAmount('');
    setSubmitted(false);
  };

  const result = pay.data;
  if (result && card) {
    const approved = result.status === 'APPROVED';
    return (
      <Screen
        title={approved ? 'Approved' : 'Declined'}
        leading={<BackButton />}
      >
        <View
          accessibilityRole="summary"
          style={[
            styles.result,
            { borderColor: approved ? theme.positive : theme.negative },
          ]}
        >
          <Text style={[styles.amount, { color: theme.text }]}>
            {formatMoney(result.amountCents, card.currency)}
          </Text>
          <Text style={{ color: theme.muted }}>at {result.merchant}</Text>
          {!approved && (
            <Text style={{ color: theme.negative }}>
              {result.declineMessage}
            </Text>
          )}
        </View>
        <Button title="Pay again" variant="secondary" onPress={again} />
      </Screen>
    );
  }

  return (
    <Screen title="Pay a shop" leading={<BackButton />}>
      {card && <PaymentCardView card={card} />}
      <Field
        label="Shop"
        value={merchant}
        onChangeText={setMerchant}
        maxLength={100}
        error={submitted ? errors.merchant : null}
      />
      <Field
        label={`Amount (${card?.currency ?? 'USD'})`}
        value={amount}
        onChangeText={text => {
          setAmount(text);
          setKey(newIdempotencyKey());
        }}
        keyboardType="decimal-pad"
        placeholder="0.00"
        error={submitted ? errors.amount : null}
      />
      <ErrorText error={pay.error} />
      <Button
        title={pay.isPending ? 'Asking PayDeck…' : 'Pay'}
        onPress={submit}
        disabled={pay.isPending}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  result: {
    borderWidth: 2,
    borderRadius: 16,
    padding: spacing.l,
    gap: spacing.xs,
  },
  amount: { fontSize: 32, fontWeight: '800' },
});
