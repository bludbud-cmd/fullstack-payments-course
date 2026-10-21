import { useColorScheme } from 'react-native';

const light = {
  background: '#f5f6fa',
  surface: '#ffffff',
  text: '#14162b',
  muted: '#5d6177',
  border: '#dfe1ea',
  accent: '#3c4fe0',
  onAccent: '#ffffff',
  positive: '#1d7a46',
  negative: '#b3261e',
  cardActive: '#3c4fe0',
  cardFrozen: '#7b8bb0',
  cardCancelled: '#9a9cab',
};

const dark: typeof light = {
  background: '#0f1020',
  surface: '#1a1c33',
  text: '#f1f2f8',
  muted: '#a3a7c2',
  border: '#2c2f4d',
  accent: '#8f9bff',
  onAccent: '#0f1020',
  positive: '#5fd394',
  negative: '#ff8a80',
  cardActive: '#4b5cf0',
  cardFrozen: '#55607f',
  cardCancelled: '#4a4c5e',
};

export type Theme = typeof light;

/** The colours to use right now, following the phone's or Mac's light or dark mode. */
export function useTheme(): Theme {
  return useColorScheme() === 'dark' ? dark : light;
}

export const spacing = { xs: 4, s: 8, m: 16, l: 24, xl: 32 };
