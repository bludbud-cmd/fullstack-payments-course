import React, { useEffect, useState } from 'react';
import { Linking, StyleSheet, Text, View } from 'react-native';
import { newIdempotencyKey } from '../api/client';
import { useAccount, useTopUp } from '../api/hooks';
import { startCardTopUp } from '../api/payments';
import { queryParams } from '../auth/AuthContext';
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
  const [card, setCard] = useState<{
    state: 'idle' | 'opening' | 'waiting' | 'paid' | 'cancelled';
    error: Error | null;
  }>({ state: 'idle', error: null });

  const currency = account.data?.currency ?? 'USD';
  const error = topUpError(amount);
  const cents = parseMoneyInput(amount);
  const ready = error === null && cents !== null;

  // Stripe's page sends the browser to paydeck://top-up?result=... when done.
  useEffect(() => {
    const subscription = Linking.addEventListener('url', ({ url }) => {
      if (url.startsWith('paydeck://top-up')) {
        const paid = queryParams(url).result === 'success';
        setCard({ state: paid ? 'paid' : 'cancelled', error: null });
      }
    });
    return () => subscription.remove();
  }, []);

  const changeAmount = (text: string) => {
    setAmount(text);
    setIdempotencyKey(newIdempotencyKey());
  };

  const payByCard = async () => {
    setSubmitted(true);
    if (!ready || !account.data || card.state === 'opening') {
      return;
    }
    setCard({ state: 'opening', error: null });
    try {
      const { url } = await startCardTopUp(
        account.data.id,
        cents,
        idempotencyKey,
      );
      await Linking.openURL(url);
      setCard({ state: 'waiting', error: null });
    } catch (e) {
      setCard({ state: 'idle', error: e as Error });
    }
  };

  const addPretendMoney = () => {
    setSubmitted(true);
    if (ready && !topUp.isPending) {
      topUp.mutate(
        { amountCents: cents, idempotencyKey },
        { onSuccess: goBack },
      );
    }
  };

  if (card.state === 'paid') {
    return (
      <Screen title="Thank you" leading={<BackButton />}>
        <Text style={{ color: theme.text }}>
          Stripe has your payment. The money appears in your wallet as soon as
          Stripe confirms it, usually within a few seconds.
        </Text>
        <Button title="Back to wallet" onPress={goBack} />
      </Screen>
    );
  }

  return (
    <Screen title="Top up" leading={<BackButton />}>
      <Field
        label={`Amount (${currency})`}
        value={amount}
        onChangeText={changeAmount}
        keyboardType="decimal-pad"
        placeholder="0.00"
        error={submitted ? error : null}
        onSubmitEditing={payByCard}
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

      <Button
        title={
          card.state === 'opening'
            ? 'Opening Stripe…'
            : ready
            ? `Pay ${formatMoney(cents, currency)} by card`
            : 'Pay by card'
        }
        onPress={payByCard}
        disabled={card.state === 'opening'}
      />
      {card.state === 'waiting' && (
        <Text style={{ color: theme.muted }}>
          Finish paying in your browser. In test mode, use the card 4242 4242
          4242 4242 with any future date.
        </Text>
      )}
      {card.state === 'cancelled' && (
        <Text style={{ color: theme.muted }}>
          The payment was cancelled. Nothing was charged.
        </Text>
      )}
      <ErrorText error={card.error} />

      <Text style={{ color: theme.muted }}>
        Building and testing? Add pretend money instead. It works until the
        server switches it off.
      </Text>
      <ErrorText error={topUp.error} />
      <Button
        title={
          topUp.isPending
            ? 'Adding…'
            : ready
            ? `Add ${formatMoney(cents, currency)} of pretend money`
            : 'Add pretend money'
        }
        variant="secondary"
        onPress={addPretendMoney}
        disabled={topUp.isPending}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  quick: { flexDirection: 'row', gap: spacing.s },
  quickButton: { flex: 1 },
});
