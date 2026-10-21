/**
 * PayDeck: the app learners build in the Full-Stack From Zero course.
 *
 * @format
 */

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React, { useState } from 'react';
import { AuthProvider, useAuth } from './src/auth/AuthContext';
import { LiveUpdates } from './src/live/LiveUpdates';
import { AppNavigator } from './src/navigation/AppNavigator';
import { OpenAccountScreen } from './src/screens/OpenAccountScreen';
import { SignInScreen } from './src/screens/SignInScreen';
import { AccountGate } from './src/state/AccountContext';

export default function App({
  queryClient: given,
}: {
  queryClient?: QueryClient;
}) {
  const [queryClient] = useState(() => given ?? new QueryClient());
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <SignedInOnly />
      </AuthProvider>
    </QueryClientProvider>
  );
}

/** Signed out: the sign-in screen. Signed in: the wallet. */
function SignedInOnly() {
  const { session } = useAuth();
  if (session.status === 'loading') {
    return null;
  }
  if (session.status === 'signedOut') {
    return <SignInScreen />;
  }
  return (
    <AccountGate openAccount={OpenAccountScreen}>
      <LiveUpdates>
        <AppNavigator />
      </LiveUpdates>
    </AccountGate>
  );
}
