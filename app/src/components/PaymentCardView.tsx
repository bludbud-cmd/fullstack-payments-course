import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { formatMoney } from '../money';
import { spacing, useTheme } from '../theme';
import type { Card } from '../types';

const statusLabel = {
  ACTIVE: 'Active',
  FROZEN: 'Frozen',
  CANCELLED: 'Cancelled',
} as const;

/** A virtual card drawn like a real one. Only the last four digits are ever shown. */
export function PaymentCardView({ card }: { card: Card }) {
  const theme = useTheme();
  const background =
    card.status === 'ACTIVE'
      ? theme.cardActive
      : card.status === 'FROZEN'
      ? theme.cardFrozen
      : theme.cardCancelled;

  return (
    <View
      accessible
      accessibilityLabel={`${card.nickname} card ending ${card.last4}, ${
        statusLabel[card.status]
      }`}
      style={[styles.card, { backgroundColor: background }]}
    >
      <View style={styles.row}>
        <Text style={styles.nickname}>{card.nickname}</Text>
        <Text style={styles.badge}>{statusLabel[card.status]}</Text>
      </View>
      <Text style={styles.number}>•••• •••• •••• {card.last4}</Text>
      <View style={styles.row}>
        <Text style={styles.small}>PayDeck virtual</Text>
        <Text style={styles.small}>
          {card.spendingLimitCents === null
            ? 'No limit'
            : `Limit ${formatMoney(card.spendingLimitCents, card.currency)}`}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    padding: spacing.l - 4,
    aspectRatio: 1.586, // The shape of a real bank card
    maxWidth: 380,
    width: '100%',
    justifyContent: 'space-between',
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  nickname: { color: '#fff', fontSize: 18, fontWeight: '700' },
  badge: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
    paddingHorizontal: spacing.s,
    paddingVertical: 2,
    borderRadius: 8,
    backgroundColor: 'rgba(255,255,255,0.2)',
    overflow: 'hidden',
  },
  number: {
    color: '#fff',
    fontSize: 20,
    letterSpacing: 2,
    fontVariant: ['tabular-nums'],
  },
  small: { color: 'rgba(255,255,255,0.85)', fontSize: 13 },
});
