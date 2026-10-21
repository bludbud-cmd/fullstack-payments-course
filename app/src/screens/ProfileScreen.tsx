import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useMe } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/Button';
import { QueryState } from '../components/QueryState';
import { Screen } from '../components/Screen';
import { spacing, useTheme } from '../theme';

const roleLabel = {
  CUSTOMER: 'Customer',
  SUPPORT: 'Support staff',
  ADMIN: 'Administrator',
} as const;

export function ProfileScreen() {
  const theme = useTheme();
  const { signOut } = useAuth();
  const me = useMe();

  if (!me.data) {
    return (
      <Screen title="You">
        <QueryState queries={[me]} />
      </Screen>
    );
  }
  const { user } = me.data;
  return (
    <Screen title="You">
      <View
        style={[
          styles.box,
          { backgroundColor: theme.surface, borderColor: theme.border },
        ]}
      >
        <Text style={[styles.name, { color: theme.text }]}>
          {user.displayName}
        </Text>
        <Text style={{ color: theme.muted }}>{user.email}</Text>
        {user.role !== 'CUSTOMER' && (
          <Text style={{ color: theme.accent }}>{roleLabel[user.role]}</Text>
        )}
      </View>
      <Button title="Sign out" variant="secondary" onPress={signOut} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  box: {
    borderRadius: 16,
    borderWidth: 1,
    padding: spacing.m,
    gap: spacing.xs,
  },
  name: { fontSize: 20, fontWeight: '700' },
});
