import React from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { spacing, useTheme } from '../theme';
import { Button } from './Button';

type Query = {
  isPending: boolean;
  error: Error | null;
  refetch: () => unknown;
};

/**
 * Shows a spinner while data loads and a friendly message if it failed.
 * Returns null once the data is ready, so the screen can draw it.
 */
export function QueryState({ queries }: { queries: Query[] }) {
  const theme = useTheme();
  const failed = queries.find(query => query.error);
  if (failed?.error) {
    return (
      <View style={styles.box}>
        <Text style={[styles.message, { color: theme.text }]}>
          {failed.error.message}
        </Text>
        <Button
          title="Try again"
          variant="secondary"
          onPress={() => queries.forEach(q => q.refetch())}
        />
      </View>
    );
  }
  if (queries.some(query => query.isPending)) {
    return (
      <View style={styles.box}>
        <ActivityIndicator accessibilityLabel="Loading" color={theme.accent} />
      </View>
    );
  }
  return null;
}

const styles = StyleSheet.create({
  box: { padding: spacing.l, gap: spacing.m, alignItems: 'center' },
  message: { fontSize: 16, textAlign: 'center' },
});
