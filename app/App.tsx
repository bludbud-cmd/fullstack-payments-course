/**
 * PayDeck: the app learners build in the Full-Stack From Zero course.
 *
 * @format
 */

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React, { useState } from 'react';
import { AppNavigator } from './src/navigation/AppNavigator';
import { WelcomeScreen } from './src/screens/WelcomeScreen';
import { AccountIdGate } from './src/state/AccountIdContext';

export default function App({
  queryClient: given,
}: {
  queryClient?: QueryClient;
}) {
  const [queryClient] = useState(() => given ?? new QueryClient());
  return (
    <QueryClientProvider client={queryClient}>
      <AccountIdGate welcome={WelcomeScreen}>
        <AppNavigator />
      </AccountIdGate>
    </QueryClientProvider>
  );
}
