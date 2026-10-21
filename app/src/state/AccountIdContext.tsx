import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useContext, useEffect, useState } from 'react';

const STORAGE_KEY = 'paydeck.accountId.v1';

type AccountIdState =
  | { status: 'loading' }
  | { status: 'none' }
  | { status: 'ready'; accountId: string };

const AccountIdContext = createContext<string | null>(null);

/**
 * Remembers which PayDeck account belongs to this device. Until login
 * arrives in Part 8, the first launch opens a new account and keeps its ID.
 */
export function AccountIdGate({
  welcome: Welcome,
  children,
}: {
  /** The screen shown when this device has no account yet. */
  welcome: React.ComponentType<{ onOpened: (accountId: string) => void }>;
  children: React.ReactNode;
}) {
  const [state, setState] = useState<AccountIdState>({ status: 'loading' });

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY)
      .then(saved =>
        setState(
          saved ? { status: 'ready', accountId: saved } : { status: 'none' },
        ),
      )
      .catch(() => setState({ status: 'none' }));
  }, []);

  if (state.status === 'loading') {
    return null;
  }
  if (state.status === 'none') {
    return (
      <Welcome
        onOpened={accountId => {
          AsyncStorage.setItem(STORAGE_KEY, accountId).catch(() => {});
          setState({ status: 'ready', accountId });
        }}
      />
    );
  }
  return (
    <AccountIdContext.Provider value={state.accountId}>
      {children}
    </AccountIdContext.Provider>
  );
}

export function useAccountId(): string {
  const accountId = useContext(AccountIdContext);
  if (!accountId) {
    throw new Error('useAccountId must be used inside <AccountIdGate>');
  }
  return accountId;
}
