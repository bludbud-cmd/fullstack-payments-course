import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useCards, useMe } from '../api/hooks';
import { Button } from '../components/Button';
import { PaymentCardView } from '../components/PaymentCardView';
import { QueryState } from '../components/QueryState';
import { Screen } from '../components/Screen';
import { useNavigation } from '../navigation/NavigationContext';
import { spacing, useTheme } from '../theme';
import type { Card } from '../types';

export function CardsScreen() {
  const theme = useTheme();
  const { navigate } = useNavigation();
  const me = useMe();
  const cards = useCards();

  if (!me.data || !cards.data) {
    return (
      <Screen title="Cards">
        <QueryState queries={[me, cards]} />
      </Screen>
    );
  }

  const open = (list: Card[]) =>
    list.filter(card => card.status !== 'CANCELLED');
  const ownCards = open(cards.data);
  const sharedWithMe = open(me.data.heldCards);

  const grid = (list: Card[]) => (
    <View style={styles.grid}>
      {list.map(card => (
        <Pressable
          key={card.id}
          accessibilityRole="button"
          accessibilityHint="Opens the card's settings"
          onPress={() => navigate({ name: 'card', cardId: card.id })}
          style={styles.cardSlot}
        >
          <PaymentCardView card={card} />
        </Pressable>
      ))}
    </View>
  );

  return (
    <Screen title="Cards">
      <Button
        title="Issue a new card"
        onPress={() => navigate({ name: 'issueCard' })}
      />
      {ownCards.length === 0 && (
        <Text style={{ color: theme.muted }}>
          You have no cards yet. Issue one to start paying.
        </Text>
      )}
      {grid(ownCards)}
      {sharedWithMe.length > 0 && (
        <>
          <Text style={[styles.heading, { color: theme.text }]}>
            Shared with you
          </Text>
          {grid(sharedWithMe)}
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.m },
  cardSlot: { flexGrow: 1, flexBasis: 300, maxWidth: 380 },
  heading: { fontSize: 20, fontWeight: '700' },
});
