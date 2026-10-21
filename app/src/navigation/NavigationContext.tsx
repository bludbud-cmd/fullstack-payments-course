import { createContext, useContext } from 'react';
import type { Route, TabName } from './routes';

export type Navigation = {
  navigate: (route: Route) => void;
  goBack: () => void;
  switchTab: (tab: TabName) => void;
  canGoBack: boolean;
};

export const NavigationContext = createContext<Navigation | null>(null);

export function useNavigation(): Navigation {
  const navigation = useContext(NavigationContext);
  if (!navigation) {
    throw new Error('useNavigation must be used inside <AppNavigator>');
  }
  return navigation;
}
