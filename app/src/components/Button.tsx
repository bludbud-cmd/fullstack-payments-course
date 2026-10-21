import React from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { spacing, useTheme } from '../theme';

type Props = {
  title: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary' | 'danger';
  disabled?: boolean;
};

export function Button({
  title,
  onPress,
  variant = 'primary',
  disabled = false,
}: Props) {
  const theme = useTheme();
  const filled = variant === 'primary';
  const color = variant === 'danger' ? theme.negative : theme.accent;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        { borderColor: color, backgroundColor: filled ? color : 'transparent' },
        (pressed || disabled) && styles.dimmed,
      ]}
    >
      <Text style={[styles.title, { color: filled ? theme.onAccent : color }]}>
        {title}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    borderWidth: 1.5,
    borderRadius: 10,
    paddingVertical: spacing.s + 4,
    paddingHorizontal: spacing.m,
    alignItems: 'center',
  },
  title: { fontSize: 16, fontWeight: '600' },
  dimmed: { opacity: 0.6 },
});
