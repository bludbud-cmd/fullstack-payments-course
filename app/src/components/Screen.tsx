import React from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { spacing, useTheme } from '../theme';

type Props = {
  title: string;
  /** Shown on the left of the title, for example a back button. */
  leading?: React.ReactNode;
  children: React.ReactNode;
};

/** The frame every screen sits in: a title and a scrollable body. */
export function Screen({ title, leading, children }: Props) {
  const theme = useTheme();
  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      <View style={styles.header}>
        {leading}
        <Text
          accessibilityRole="header"
          style={[styles.title, { color: theme.text }]}
        >
          {title}
        </Text>
      </View>
      {children}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: {
    padding: spacing.m,
    gap: spacing.m,
    maxWidth: 720,
    width: '100%',
    alignSelf: 'center',
  },
  header: { flexDirection: 'row', alignItems: 'center', gap: spacing.s },
  title: { fontSize: 28, fontWeight: '700' },
});
