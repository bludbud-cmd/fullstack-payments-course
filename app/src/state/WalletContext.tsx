import AsyncStorage from '@react-native-async-storage/async-storage';
import React, {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useState,
} from 'react';
import { sampleAccount, sampleActivity, sampleCards } from '../data/sampleData';
import {
  walletReducer,
  type WalletAction,
  type WalletState,
} from './walletReducer';

const STORAGE_KEY = 'paydeck.wallet.v1';

const initialState: WalletState = {
  account: sampleAccount,
  cards: sampleCards,
  activity: sampleActivity,
};

type WalletContextValue = {
  state: WalletState;
  ready: boolean;
  topUp: (amountCents: number) => void;
  issueCard: (nickname: string, spendingLimitCents: number | null) => void;
  renameCard: (cardId: string, nickname: string) => void;
  setSpendingLimit: (cardId: string, spendingLimitCents: number | null) => void;
  freezeCard: (cardId: string) => void;
  unfreezeCard: (cardId: string) => void;
  cancelCard: (cardId: string) => void;
  resetSampleData: () => void;
};

const WalletContext = createContext<WalletContextValue | null>(null);

type Action = WalletAction | { type: 'replace'; state: WalletState };

function reducer(state: WalletState, action: Action): WalletState {
  return action.type === 'replace'
    ? action.state
    : walletReducer(state, action);
}

const newId = () =>
  `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
const randomLast4 = () =>
  String(Math.floor(Math.random() * 10_000)).padStart(4, '0');

/** Holds the wallet for the whole app and saves it on the device after every change. */
export function WalletProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);
  const [ready, setReady] = useState(false);

  // Load what was saved last time, once, when the app starts.
  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY)
      .then(saved => {
        if (saved) {
          dispatch({ type: 'replace', state: JSON.parse(saved) });
        }
      })
      .catch(() => {
        // Nothing saved yet, or it could not be read: keep the sample data.
      })
      .finally(() => setReady(true));
  }, []);

  // Save after every change, but only once loading has finished.
  useEffect(() => {
    if (ready) {
      AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(state)).catch(() => {});
    }
  }, [ready, state]);

  const value = useMemo<WalletContextValue>(
    () => ({
      state,
      ready,
      topUp: amountCents =>
        dispatch({
          type: 'topUp',
          amountCents,
          at: new Date().toISOString(),
          id: newId(),
        }),
      issueCard: (nickname, spendingLimitCents) =>
        dispatch({
          type: 'issueCard',
          nickname,
          spendingLimitCents,
          at: new Date().toISOString(),
          id: newId(),
          last4: randomLast4(),
        }),
      renameCard: (cardId, nickname) =>
        dispatch({ type: 'renameCard', cardId, nickname }),
      setSpendingLimit: (cardId, spendingLimitCents) =>
        dispatch({ type: 'setSpendingLimit', cardId, spendingLimitCents }),
      freezeCard: cardId => dispatch({ type: 'freezeCard', cardId }),
      unfreezeCard: cardId => dispatch({ type: 'unfreezeCard', cardId }),
      cancelCard: cardId => dispatch({ type: 'cancelCard', cardId }),
      resetSampleData: () => dispatch({ type: 'replace', state: initialState }),
    }),
    [state, ready],
  );

  return (
    <WalletContext.Provider value={value}>{children}</WalletContext.Provider>
  );
}

export function useWallet(): WalletContextValue {
  const value = useContext(WalletContext);
  if (!value) {
    throw new Error('useWallet must be used inside <WalletProvider>');
  }
  return value;
}
