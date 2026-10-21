import React from 'react';
import { StyleSheet, Switch, Text, View } from 'react-native';
import {
  useMe,
  useNotificationSettings,
  useSetNotificationSettings,
} from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/Button';
import { ErrorText } from '../components/ErrorText';
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
      <NotificationSwitches />
      <Button title="Sign out" variant="secondary" onPress={signOut} />
    </Screen>
  );
}

/** Which push notifications this person wants. The API remembers the answer. */
function NotificationSwitches() {
  const theme = useTheme();
  const settings = useNotificationSettings();
  const save = useSetNotificationSettings();
  if (!settings.data) {
    return null;
  }
  const current = settings.data;
  const rows = [
    { key: 'payments', label: 'Money in and out' },
    { key: 'declines', label: 'Declined card payments' },
  ] as const;
  return (
    <View
      style={[
        styles.box,
        { backgroundColor: theme.surface, borderColor: theme.border },
      ]}
    >
      <Text style={[styles.heading, { color: theme.text }]}>Notifications</Text>
      {rows.map(row => (
        <View key={row.key} style={styles.row}>
          <Text style={[styles.rowLabel, { color: theme.text }]}>
            {row.label}
          </Text>
          <Switch
            accessibilityLabel={row.label}
            value={current[row.key]}
            disabled={save.isPending}
            onValueChange={on => save.mutate({ ...current, [row.key]: on })}
          />
        </View>
      ))}
      <ErrorText error={save.error} />
    </View>
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
  heading: { fontSize: 17, fontWeight: '700' },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.m },
  rowLabel: { flex: 1, fontSize: 16 },
});
