import React from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { ActivityRow } from '../components/ActivityRow';
import { useWallet } from '../state/WalletContext';
import { spacing, useTheme } from '../theme';

/** Every transaction, newest first. FlatList only draws the rows on screen, so long lists stay fast. */
export function ActivityScreen() {
  const theme = useTheme();
  const { state } = useWallet();

  return (
    <View style={[styles.root, { backgroundColor: theme.background }]}>
      <FlatList
        data={state.activity}
        keyExtractor={item => item.transactionId}
        renderItem={({ item }) => (
          <ActivityRow item={item} currency={state.account.currency} />
        )}
        contentContainerStyle={styles.content}
        ListHeaderComponent={
          <Text
            accessibilityRole="header"
            style={[styles.title, { color: theme.text }]}
          >
            Activity
          </Text>
        }
        ListEmptyComponent={
          <Text style={{ color: theme.muted }}>No transactions yet.</Text>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: {
    padding: spacing.m,
    maxWidth: 720,
    width: '100%',
    alignSelf: 'center',
  },
  title: { fontSize: 28, fontWeight: '700', marginBottom: spacing.s },
});
