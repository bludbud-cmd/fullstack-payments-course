import React from 'react';
import { Text } from 'react-native';
import { useTheme } from '../theme';

/** The message from a failed save, if there is one. */
export function ErrorText({ error }: { error: Error | null }) {
  const theme = useTheme();
  return error ? (
    <Text style={{ color: theme.negative }}>{error.message}</Text>
  ) : null;
}
