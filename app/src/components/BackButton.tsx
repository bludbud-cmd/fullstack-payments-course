import React from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { useNavigation } from '../navigation/NavigationContext';
import { useTheme } from '../theme';

export function BackButton() {
  const theme = useTheme();
  const { goBack } = useNavigation();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="Back"
      onPress={goBack}
      hitSlop={12}
    >
      <Text style={[styles.arrow, { color: theme.accent }]}>‹</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  arrow: { fontSize: 28 },
});
