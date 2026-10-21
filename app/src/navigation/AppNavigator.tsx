import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  BackHandler,
  Platform,
  Pressable,
  StatusBar,
  StyleSheet,
  Text,
  useColorScheme,
  useWindowDimensions,
  View,
} from 'react-native';
import { ActivityScreen } from '../screens/ActivityScreen';
import { CardDetailScreen } from '../screens/CardDetailScreen';
import { CardsScreen } from '../screens/CardsScreen';
import { HomeScreen } from '../screens/HomeScreen';
import { IssueCardScreen } from '../screens/IssueCardScreen';
import { ProfileScreen } from '../screens/ProfileScreen';
import { TopUpScreen } from '../screens/TopUpScreen';
import { spacing, useTheme } from '../theme';
import { NavigationContext, type Navigation } from './NavigationContext';
import {
  current,
  pop,
  push,
  switchTab,
  tabs,
  type NavState,
  type Route,
  type TabName,
} from './routes';

/** Windows at least this wide (a Mac window, a tablet) get a sidebar instead of a tab bar. */
const WIDE_LAYOUT = 720;

function renderScreen(route: Route) {
  switch (route.name) {
    case 'home':
      return <HomeScreen />;
    case 'cards':
      return <CardsScreen />;
    case 'activity':
      return <ActivityScreen />;
    case 'profile':
      return <ProfileScreen />;
    case 'card':
      return <CardDetailScreen cardId={route.cardId} />;
    case 'topUp':
      return <TopUpScreen />;
    case 'issueCard':
      return <IssueCardScreen />;
  }
}

/** Which tab a screen belongs to, so the right tab is highlighted. */
function tabFor(stack: NavState): TabName {
  const first = stack[0].name;
  return first === 'cards' || first === 'activity' || first === 'profile'
    ? first
    : 'home';
}

export function AppNavigator() {
  const theme = useTheme();
  const isDark = useColorScheme() === 'dark';
  const { width } = useWindowDimensions();
  const wide = width >= WIDE_LAYOUT;
  const [stack, setStack] = useState<NavState>([{ name: 'home' }]);

  const goBack = useCallback(() => setStack(pop), []);
  const navigation = useMemo<Navigation>(
    () => ({
      navigate: route => setStack(s => push(s, route)),
      goBack,
      switchTab: tab => setStack(switchTab(tab)),
      canGoBack: stack.length > 1,
    }),
    [goBack, stack.length],
  );

  // The Android back button goes back a screen instead of closing the app.
  useEffect(() => {
    const subscription = BackHandler.addEventListener(
      'hardwareBackPress',
      () => {
        if (stack.length > 1) {
          goBack();
          return true;
        }
        return false;
      },
    );
    return () => subscription.remove();
  }, [stack.length, goBack]);

  const activeTab = tabFor(stack);
  const tabButtons = tabs.map(tab => {
    const selected = tab.name === activeTab;
    return (
      <Pressable
        key={tab.name}
        accessibilityRole="tab"
        accessibilityState={{ selected }}
        onPress={() => navigation.switchTab(tab.name)}
        style={[
          wide ? styles.sidebarItem : styles.tabItem,
          selected && wide && { backgroundColor: theme.background },
        ]}
      >
        <Text
          style={[
            styles.tabLabel,
            { color: selected ? theme.accent : theme.muted },
            selected && styles.tabLabelSelected,
          ]}
        >
          {tab.label}
        </Text>
      </Pressable>
    );
  });

  return (
    <NavigationContext.Provider value={navigation}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} />
      <View
        style={[
          styles.root,
          wide && styles.rootWide,
          { backgroundColor: theme.background },
        ]}
      >
        {wide && (
          <View
            accessibilityRole="tablist"
            style={[
              styles.sidebar,
              {
                backgroundColor: theme.surface,
                borderRightColor: theme.border,
              },
            ]}
          >
            <Text style={[styles.brand, { color: theme.text }]}>PayDeck</Text>
            {tabButtons}
          </View>
        )}
        <View style={styles.content}>{renderScreen(current(stack))}</View>
        {!wide && (
          <View
            accessibilityRole="tablist"
            style={[
              styles.tabBar,
              { backgroundColor: theme.surface, borderTopColor: theme.border },
            ]}
          >
            {tabButtons}
          </View>
        )}
      </View>
    </NavigationContext.Provider>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    // Android draws the app behind the status bar, so leave room for it.
    paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight ?? 0 : 0,
  },
  rootWide: { flexDirection: 'row' },
  content: { flex: 1 },
  sidebar: {
    width: 200,
    paddingTop: spacing.l,
    paddingHorizontal: spacing.s,
    borderRightWidth: 1,
    gap: 2,
  },
  brand: {
    fontSize: 20,
    fontWeight: '800',
    paddingHorizontal: spacing.s + 4,
    marginBottom: spacing.m,
  },
  sidebarItem: {
    paddingVertical: spacing.s + 2,
    paddingHorizontal: spacing.s + 4,
    borderRadius: 8,
  },
  tabBar: {
    flexDirection: 'row',
    borderTopWidth: 1,
    paddingBottom: Platform.OS === 'android' ? spacing.l : spacing.s,
  },
  tabItem: { flex: 1, alignItems: 'center', paddingVertical: spacing.s + 4 },
  tabLabel: { fontSize: 15 },
  tabLabelSelected: { fontWeight: '700' },
});
