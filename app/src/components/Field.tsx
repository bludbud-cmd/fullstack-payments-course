import React from 'react';
import {
  StyleSheet,
  Text,
  TextInput,
  View,
  type TextInputProps,
} from 'react-native';
import { spacing, useTheme } from '../theme';

type Props = TextInputProps & {
  label: string;
  error?: string | null;
  hint?: string;
};

/** A labelled text box that can show an error message under it. */
export function Field({ label, error, hint, ...input }: Props) {
  const theme = useTheme();
  return (
    <View style={styles.field}>
      <Text style={[styles.label, { color: theme.text }]}>{label}</Text>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={theme.muted}
        style={[
          styles.input,
          {
            color: theme.text,
            backgroundColor: theme.surface,
            borderColor: error ? theme.negative : theme.border,
          },
        ]}
        {...input}
      />
      {error ? (
        <Text style={{ color: theme.negative }}>{error}</Text>
      ) : hint ? (
        <Text style={{ color: theme.muted }}>{hint}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  field: { gap: spacing.xs },
  label: { fontSize: 14, fontWeight: '600' },
  input: {
    borderWidth: 1,
    borderRadius: 10,
    padding: spacing.s + 4,
    fontSize: 16,
  },
});
