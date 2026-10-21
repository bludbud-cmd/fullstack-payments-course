import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { ActivityRow } from '../components/ActivityRow';
import { Button } from '../components/Button';
import { Screen } from '../components/Screen';
import { formatMoney } from '../money';
import { useNavigation } from '../navigation/NavigationContext';
import { useWallet } from '../state/WalletContext';
import { spacing, useTheme } from '../theme';

export function HomeScreen() {
  const theme = useTheme();
  const { navigate, switchTab } = useNavigation();
  const { state } = useWallet();
  const { account, activity, cards } = state;
  const activeCards = cards.filter(card => card.status === 'ACTIVE').length;

  return (
    <Screen title={`Hi, ${account.ownerName}`}>
      <View
        style={[
          styles.balance,
          { backgroundColor: theme.surface, borderColor: theme.border },
        ]}
      >
        <Text style={{ color: theme.muted }}>Balance</Text>
        <Text testID="balance" style={[styles.amount, { color: theme.text }]}>
          {formatMoney(account.balanceCents, account.currency)}
        </Text>
        <Text style={{ color: theme.muted }}>
          {activeCards} active {activeCards === 1 ? 'card' : 'cards'}
        </Text>
        <View style={styles.actions}>
          <View style={styles.action}>
            <Button
              title="Top up"
              onPress={() => navigate({ name: 'topUp' })}
            />
          </View>
          <View style={styles.action}>
            <Button
              title="Cards"
              variant="secondary"
              onPress={() => switchTab('cards')}
            />
          </View>
        </View>
      </View>

      <Text style={[styles.heading, { color: theme.text }]}>
        Recent activity
      </Text>
      <View>
        {activity.slice(0, 3).map(item => (
          <ActivityRow
            key={item.transactionId}
            item={item}
            currency={account.currency}
          />
        ))}
      </View>
      <Button
        title="See all activity"
        variant="secondary"
        onPress={() => switchTab('activity')}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  balance: {
    borderRadius: 16,
    borderWidth: 1,
    padding: spacing.l - 4,
    gap: spacing.xs,
  },
  amount: { fontSize: 40, fontWeight: '800', fontVariant: ['tabular-nums'] },
  actions: { flexDirection: 'row', gap: spacing.s, marginTop: spacing.m },
  action: { flex: 1 },
  heading: { fontSize: 20, fontWeight: '700' },
});
