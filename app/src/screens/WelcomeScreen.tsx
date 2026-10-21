import React, { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { openAccount } from '../api/paydeck';
import { Button } from '../components/Button';
import { ErrorText } from '../components/ErrorText';
import { Field } from '../components/Field';
import { Screen } from '../components/Screen';
import type { Currency } from '../money';
import { spacing, useTheme } from '../theme';

const CURRENCIES: Currency[] = ['USD', 'EUR', 'GBP', 'INR'];

/** Shown on first launch: opens a PayDeck account for this device. */
export function WelcomeScreen({
  onOpened,
}: {
  onOpened: (accountId: string) => void;
}) {
  const theme = useTheme();
  const [name, setName] = useState('');
  const [currency, setCurrency] = useState<Currency>('USD');
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const nameError = name.trim() === '' ? 'Tell us your name' : null;

  const open = async () => {
    setSubmitted(true);
    if (nameError) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const account = await openAccount(name.trim(), currency);
      onOpened(account.id);
    } catch (e) {
      setError(e as Error);
      setBusy(false);
    }
  };

  return (
    <View style={[styles.root, { backgroundColor: theme.background }]}>
      <Screen title="Welcome to PayDeck">
        <Text style={{ color: theme.muted }}>
          Open your account to get started.
        </Text>
        <Field
          label="Your name"
          value={name}
          onChangeText={setName}
          maxLength={100}
          error={submitted ? nameError : null}
        />
        <Text style={[styles.label, { color: theme.text }]}>Currency</Text>
        <View style={styles.row}>
          {CURRENCIES.map(code => (
            <View key={code} style={styles.choice}>
              <Button
                title={code}
                variant={code === currency ? 'primary' : 'secondary'}
                onPress={() => setCurrency(code)}
              />
            </View>
          ))}
        </View>
        <ErrorText error={error} />
        <Button
          title={busy ? 'Opening…' : 'Open my account'}
          onPress={open}
          disabled={busy}
        />
      </Screen>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  label: { fontSize: 14, fontWeight: '600' },
  row: { flexDirection: 'row', gap: spacing.s },
  choice: { flex: 1 },
});
