import { useQueryClient } from '@tanstack/react-query';
import React, { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { currentAccessToken, refreshTokens } from '../api/client';
import { useMe } from '../api/hooks';
import { API_BASE_URL } from '../config';
import { startPush } from '../push/push';
import { useAccountId } from '../state/AccountContext';
import { spacing, useTheme } from '../theme';
import type { LiveMessage } from '../types';
import { connectStomp } from './stomp';

/** How long the banner stays up. */
const BANNER_MS = 5000;

/**
 * Keeps a WebSocket open to the API while someone is signed in. When money
 * moves or a card changes, every screen refreshes its data and a banner
 * says what happened, without anyone pulling to refresh.
 */
export function LiveUpdates({ children }: { children: React.ReactNode }) {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const accountId = useAccountId();
  const userId = useMe().data?.user.id;
  const [latest, setLatest] = useState<LiveMessage | null>(null);

  useEffect(() => {
    if (!userId) {
      return;
    }
    startPush();
    return connectStomp({
      url: API_BASE_URL.replace(/^http/, 'ws') + '/ws',
      accessToken: currentAccessToken,
      // Your account, plus anything about cards other people shared with you.
      topics: [`/topic/accounts/${accountId}`, `/topic/users/${userId}`],
      onMessage: body => {
        queryClient.invalidateQueries();
        setLatest(JSON.parse(body) as LiveMessage);
      },
      onRefused: () => {
        refreshTokens();
      },
    });
  }, [accountId, userId, queryClient]);

  useEffect(() => {
    if (!latest) {
      return;
    }
    const timer = setTimeout(() => setLatest(null), BANNER_MS);
    return () => clearTimeout(timer);
  }, [latest]);

  const declined = latest?.kind === 'CARD_DECLINED';
  return (
    <View style={styles.root}>
      {children}
      {latest && (
        <Pressable
          accessibilityRole="alert"
          accessibilityLiveRegion="polite"
          accessibilityHint="Dismisses this message"
          onPress={() => setLatest(null)}
          style={[
            styles.banner,
            {
              backgroundColor: theme.surface,
              borderColor: declined ? theme.negative : theme.accent,
            },
          ]}
        >
          <Text
            style={[
              styles.title,
              { color: declined ? theme.negative : theme.text },
            ]}
          >
            {latest.title}
          </Text>
          <Text style={{ color: theme.muted }}>{latest.body}</Text>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  banner: {
    position: 'absolute',
    top: spacing.xl,
    left: spacing.m,
    right: spacing.m,
    maxWidth: 480,
    alignSelf: 'center',
    padding: spacing.m,
    borderRadius: 12,
    borderWidth: 2,
    gap: spacing.xs,
    elevation: 6,
    shadowColor: '#000',
    shadowOpacity: 0.15,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
  },
  title: { fontWeight: '700', fontSize: 16 },
});
