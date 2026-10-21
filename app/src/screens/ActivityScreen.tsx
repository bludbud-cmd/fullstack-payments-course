import React from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { useAccount, useActivity } from '../api/hooks';
import { ActivityRow } from '../components/ActivityRow';
import { QueryState } from '../components/QueryState';
import { spacing, useTheme } from '../theme';

/** Every transaction, newest first. FlatList only draws the rows on screen, so long lists stay fast. */
export function ActivityScreen() {
  const theme = useTheme();
  const account = useAccount();
  const activity = useActivity();
  const currency = account.data?.currency;

  return (
    <View style={[styles.root, { backgroundColor: theme.background }]}>
      <FlatList
        data={currency ? activity.data : []}
        keyExtractor={item => item.transactionId}
        renderItem={({ item }) => (
          <ActivityRow item={item} currency={currency ?? 'USD'} />
        )}
        contentContainerStyle={styles.content}
        refreshing={activity.isRefetching}
        onRefresh={() => activity.refetch()}
        ListHeaderComponent={
          <>
            <Text
              accessibilityRole="header"
              style={[styles.title, { color: theme.text }]}
            >
              Activity
            </Text>
            <QueryState queries={[account, activity]} />
          </>
        }
        ListEmptyComponent={
          activity.data ? (
            <Text style={{ color: theme.muted }}>No transactions yet.</Text>
          ) : null
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
