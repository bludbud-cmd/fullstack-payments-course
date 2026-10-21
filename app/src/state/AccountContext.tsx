import React, { createContext, useContext } from 'react';
import { useMe } from '../api/hooks';
import { QueryState } from '../components/QueryState';
import { Screen } from '../components/Screen';

const AccountIdContext = createContext<string | null>(null);

/**
 * Finds the signed-in person's account. Someone who has just signed up has
 * none yet, so they see `openAccount` first.
 */
export function AccountGate({
  openAccount: OpenAccount,
  children,
}: {
  openAccount: React.ComponentType;
  children: React.ReactNode;
}) {
  const me = useMe();
  if (!me.data) {
    return (
      <Screen title="PayDeck">
        <QueryState queries={[me]} />
      </Screen>
    );
  }
  const account = me.data.accounts[0];
  if (!account) {
    return <OpenAccount />;
  }
  return (
    <AccountIdContext.Provider value={account.id}>
      {children}
    </AccountIdContext.Provider>
  );
}

export function useAccountId(): string {
  const accountId = useContext(AccountIdContext);
  if (!accountId) {
    throw new Error('useAccountId must be used inside <AccountGate>');
  }
  return accountId;
}
