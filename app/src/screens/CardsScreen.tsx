import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useAccount, useCards } from '../api/hooks';
import { Button } from '../components/Button';
import { PaymentCardView } from '../components/PaymentCardView';
import { QueryState } from '../components/QueryState';
import { Screen } from '../components/Screen';
import { useNavigation } from '../navigation/NavigationContext';
import { spacing, useTheme } from '../theme';

export function CardsScreen() {
  const theme = useTheme();
  const { navigate } = useNavigation();
  const account = useAccount();
  const cards = useCards();

  if (!account.data || !cards.data) {
    return (
      <Screen title="Cards">
        <QueryState queries={[account, cards]} />
      </Screen>
    );
  }

  const openCards = cards.data.filter(card => card.status !== 'CANCELLED');
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
            <PaymentCardView card={card} currency={account.data.currency} />
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
