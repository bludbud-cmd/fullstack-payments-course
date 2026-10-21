/**
 * PayDeck: the app learners build in the Full-Stack From Zero course.
 *
 * @format
 */

import React from 'react';
import { AppNavigator } from './src/navigation/AppNavigator';
import { WalletProvider } from './src/state/WalletContext';

export default function App() {
  return (
    <WalletProvider>
      <AppNavigator />
    </WalletProvider>
  );
}
