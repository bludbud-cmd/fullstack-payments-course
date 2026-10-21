import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { formatSignedMoney, type Currency } from '../money';
import { spacing, useTheme } from '../theme';
import type { Activity } from '../types';

const typeLabel = {
  TOP_UP: 'Top-up',
  TRANSFER: 'Transfer',
  CARD_PAYMENT: 'Card payment',
  REFUND: 'Refund',
} as const;

export function ActivityRow({
  item,
  currency,
}: {
  item: Activity;
  currency: Currency;
}) {
  const theme = useTheme();
  const date = new Date(item.createdAt).toLocaleDateString('en', {
    day: 'numeric',
    month: 'short',
  });
  return (
    <View style={[styles.row, { borderBottomColor: theme.border }]}>
      <View style={styles.text}>
        <Text style={[styles.description, { color: theme.text }]}>
          {item.description}
        </Text>
        <Text style={{ color: theme.muted }}>
          {typeLabel[item.type]} · {date}
        </Text>
      </View>
      <Text
        style={[
          styles.amount,
          { color: item.amountCents < 0 ? theme.text : theme.positive },
        ]}
      >
        {formatSignedMoney(item.amountCents, currency)}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: spacing.s + 4,
    borderBottomWidth: StyleSheet.hairlineWidth,
    gap: spacing.m,
  },
  text: { flex: 1, gap: 2 },
  description: { fontSize: 16, fontWeight: '500' },
  amount: { fontSize: 16, fontWeight: '600', fontVariant: ['tabular-nums'] },
});
