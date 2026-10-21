import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Button } from '../components/Button';
import { PaymentCardView } from '../components/PaymentCardView';
import { Screen } from '../components/Screen';
import { useNavigation } from '../navigation/NavigationContext';
import { useWallet } from '../state/WalletContext';
import { spacing, useTheme } from '../theme';

export function CardsScreen() {
  const theme = useTheme();
  const { navigate } = useNavigation();
  const { state } = useWallet();
  const openCards = state.cards.filter(card => card.status !== 'CANCELLED');

  return (
    <Screen title="Cards">
      <Button
        title="Issue a new card"
        onPress={() => navigate({ name: 'issueCard' })}
      />
      {openCards.length === 0 && (
        <Text style={{ color: theme.muted }}>
          You have no cards yet. Issue one to start paying.
        </Text>
      )}
      <View style={styles.grid}>
        {openCards.map(card => (
          <Pressable
            key={card.id}
            accessibilityRole="button"
            accessibilityHint="Opens the card's settings"
            onPress={() => navigate({ name: 'card', cardId: card.id })}
            style={styles.cardSlot}
          >
            <PaymentCardView card={card} currency={state.account.currency} />
          </Pressable>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.m },
  cardSlot: { flexGrow: 1, flexBasis: 300, maxWidth: 380 },
});
