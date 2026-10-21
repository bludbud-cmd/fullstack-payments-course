import React, { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { newIdempotencyKey } from '../api/client';
import { useAccount, useTopUp } from '../api/hooks';
import { BackButton } from '../components/BackButton';
import { Button } from '../components/Button';
import { ErrorText } from '../components/ErrorText';
import { Field } from '../components/Field';
import { Screen } from '../components/Screen';
import { formatMoney, parseMoneyInput } from '../money';
import { useNavigation } from '../navigation/NavigationContext';
import { spacing, useTheme } from '../theme';
import { topUpError } from '../validation';

const QUICK_AMOUNTS = ['10', '25', '50', '100'];

export function TopUpScreen() {
  const theme = useTheme();
  const { goBack } = useNavigation();
  const account = useAccount();
  const topUp = useTopUp();
  const [amount, setAmount] = useState('');
  const [submitted, setSubmitted] = useState(false);
  // One key per amount. If the network drops and the person taps again, the
  // same key is sent, and the API makes sure the money only arrives once.
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);

  const currency = account.data?.currency ?? 'USD';
  const error = topUpError(amount);
  const cents = parseMoneyInput(amount);

  const changeAmount = (text: string) => {
    setAmount(text);
    setIdempotencyKey(newIdempotencyKey());
  };

  const confirm = () => {
    setSubmitted(true);
    if (error === null && cents !== null && !topUp.isPending) {
      topUp.mutate(
        { amountCents: cents, idempotencyKey },
        { onSuccess: goBack },
      );
    }
  };

  return (
    <Screen title="Top up" leading={<BackButton />}>
      <Text style={{ color: theme.muted }}>
        Add money to your wallet. In Part 9 this will charge a card through
        Stripe; for now it is pretend money.
      </Text>
      <Field
        label={`Amount (${currency})`}
        value={amount}
        onChangeText={changeAmount}
        keyboardType="decimal-pad"
        placeholder="0.00"
        error={submitted ? error : null}
        onSubmitEditing={confirm}
        returnKeyType="done"
      />
      <View style={styles.quick}>
        {QUICK_AMOUNTS.map(value => (
          <View key={value} style={styles.quickButton}>
            <Button
              title={value}
              variant="secondary"
              onPress={() => changeAmount(value)}
            />
          </View>
        ))}
      </View>
      <ErrorText error={topUp.error} />
      <Button
        title={
          topUp.isPending
            ? 'Adding…'
            : cents && !error
            ? `Add ${formatMoney(cents, currency)}`
            : 'Add money'
        }
        onPress={confirm}
        disabled={topUp.isPending}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  quick: { flexDirection: 'row', gap: spacing.s },
  quickButton: { flex: 1 },
});
