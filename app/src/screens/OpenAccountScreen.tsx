import { useQueryClient } from '@tanstack/react-query';
import React, { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { keys, useMe } from '../api/hooks';
import { openAccount } from '../api/paydeck';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/Button';
import { ErrorText } from '../components/ErrorText';
import { Screen } from '../components/Screen';
import type { Currency } from '../money';
import { spacing, useTheme } from '../theme';

const CURRENCIES: Currency[] = ['USD', 'EUR', 'GBP', 'INR'];

/** Shown once, right after signing up: pick a currency and open a wallet. */
export function OpenAccountScreen() {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const { signOut } = useAuth();
  const me = useMe();
  const [currency, setCurrency] = useState<Currency>('USD');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const open = async () => {
    setBusy(true);
    setError(null);
    try {
      await openAccount(currency);
      // /api/me now lists the account, so the app moves on to the wallet.
      await queryClient.invalidateQueries({ queryKey: keys.me });
    } catch (e) {
      setError(e as Error);
      setBusy(false);
    }
  };

  return (
    <View style={[styles.root, { backgroundColor: theme.background }]}>
      <Screen title={`Welcome, ${me.data?.user.displayName ?? ''}`}>
        <Text style={{ color: theme.muted }}>
          Pick the currency for your wallet. You cannot change it later.
        </Text>
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
          title={busy ? 'Opening…' : 'Open my wallet'}
          onPress={open}
          disabled={busy}
        />
        <Button title="Sign out" variant="secondary" onPress={signOut} />
      </Screen>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  row: { flexDirection: 'row', gap: spacing.s },
  choice: { flex: 1 },
});
