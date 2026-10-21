import React, { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { BackButton } from '../components/BackButton';
import { Button } from '../components/Button';
import { Field } from '../components/Field';
import { Screen } from '../components/Screen';
import { formatMoney, parseMoneyInput } from '../money';
import { useNavigation } from '../navigation/NavigationContext';
import { useWallet } from '../state/WalletContext';
import { spacing, useTheme } from '../theme';
import { topUpError } from '../validation';

const QUICK_AMOUNTS = ['10', '25', '50', '100'];

export function TopUpScreen() {
  const theme = useTheme();
  const { goBack } = useNavigation();
  const { state, topUp } = useWallet();
  const [amount, setAmount] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const error = topUpError(amount);
  const cents = parseMoneyInput(amount);

  const confirm = () => {
    setSubmitted(true);
    if (error === null && cents !== null) {
      topUp(cents);
      goBack();
    }
  };

  return (
    <Screen title="Top up" leading={<BackButton />}>
      <Text style={{ color: theme.muted }}>
        Add money to your wallet. In Part 9 this will charge a card through
        Stripe; for now it is pretend money.
      </Text>
      <Field
        label={`Amount (${state.account.currency})`}
        value={amount}
        onChangeText={setAmount}
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
              onPress={() => setAmount(value)}
            />
          </View>
        ))}
      </View>
      <Button
        title={
          cents && !error
            ? `Add ${formatMoney(cents, state.account.currency)}`
            : 'Add money'
        }
        onPress={confirm}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  quick: { flexDirection: 'row', gap: spacing.s },
  quickButton: { flex: 1 },
});
