import { useQueryClient } from '@tanstack/react-query';
import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { Linking } from 'react-native';
import * as authApi from '../api/auth';
import { ApiError, setTokenSource } from '../api/client';
import type { User } from '../types';
import { codeChallenge, newCodeVerifier } from './pkce';
import {
  clearRefreshToken,
  loadRefreshToken,
  saveRefreshToken,
} from './tokenStore';

type Session =
  | { status: 'loading' }
  | { status: 'signedOut' }
  | { status: 'signedIn'; user: User };

type Auth = {
  session: Session;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (
    displayName: string,
    email: string,
    password: string,
  ) => Promise<void>;
  /** Opens Google in the browser. Sign-in finishes when the browser sends the person back. */
  signInWithGoogle: () => Promise<void>;
  /** Why the last Google sign-in did not work, if it did not. */
  googleError: string | null;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<Auth | null>(null);

/**
 * Keeps track of who is signed in. The access token stays in memory; the
 * refresh token goes in the Keychain (macOS) or Keystore (Android), so the
 * person stays signed in when they reopen the app.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState<Session>({ status: 'loading' });
  const [googleError, setGoogleError] = useState<string | null>(null);
  const accessToken = useRef<string | null>(null);
  const refreshToken = useRef<string | null>(null);
  const refreshing = useRef<Promise<boolean> | null>(null);
  const codeVerifier = useRef<string | null>(null);

  const forget = useCallback(async () => {
    accessToken.current = null;
    refreshToken.current = null;
    await clearRefreshToken().catch(() => {});
    queryClient.clear();
    setSession({ status: 'signedOut' });
  }, [queryClient]);

  const accept = useCallback(async (response: authApi.AuthResponse) => {
    accessToken.current = response.accessToken;
    refreshToken.current = response.refreshToken;
    await saveRefreshToken(response.refreshToken).catch(() => {});
    setSession({ status: 'signedIn', user: response.user });
  }, []);

  // Several requests can fail at once when the access token expires. They
  // all wait for the same refresh, because each refresh token works only once.
  const refresh = useCallback((): Promise<boolean> => {
    if (!refreshing.current) {
      refreshing.current = (async () => {
        const token = refreshToken.current;
        if (!token) {
          return false;
        }
        try {
          await accept(await authApi.refresh(token));
          return true;
        } catch (e) {
          if (e instanceof ApiError && e.status === 401) {
            await forget();
          }
          return false;
        } finally {
          refreshing.current = null;
        }
      })();
    }
    return refreshing.current;
  }, [accept, forget]);

  useEffect(() => {
    setTokenSource({ accessToken: () => accessToken.current, refresh });
    return () => setTokenSource(null);
  }, [refresh]);

  // On launch: if a refresh token was saved, use it to sign straight back in.
  useEffect(() => {
    loadRefreshToken()
      .catch(() => null)
      .then(async saved => {
        refreshToken.current = saved;
        if (!(saved && (await refresh()))) {
          setSession(current =>
            current.status === 'loading' ? { status: 'signedOut' } : current,
          );
        }
      });
  }, [refresh]);

  // After Google, the browser opens paydeck://auth?code=... and the app takes over.
  useEffect(() => {
    const subscription = Linking.addEventListener('url', async ({ url }) => {
      const params = queryParams(url);
      if (!url.startsWith(authApi.APP_REDIRECT_URI) || !codeVerifier.current) {
        return;
      }
      const verifier = codeVerifier.current;
      codeVerifier.current = null;
      if (params.error || !params.code) {
        setGoogleError(
          params.error === 'email_not_verified'
            ? 'Google has not verified that email address.'
            : 'Google sign-in was cancelled.',
        );
        return;
      }
      try {
        await accept(await authApi.exchangeCode(params.code, verifier));
      } catch (e) {
        setGoogleError((e as Error).message);
      }
    });
    return () => subscription.remove();
  }, [accept]);

  const auth = useMemo<Auth>(
    () => ({
      session,
      googleError,
      signIn: async (email, password) =>
        accept(await authApi.login(email, password)),
      signUp: async (displayName, email, password) =>
        accept(await authApi.register(email, password, displayName)),
      signInWithGoogle: async () => {
        setGoogleError(null);
        codeVerifier.current = newCodeVerifier();
        await Linking.openURL(
          authApi.googleSignInUrl(codeChallenge(codeVerifier.current)),
        );
      },
      signOut: async () => {
        const token = refreshToken.current;
        if (token) {
          // Tell the API to forget the refresh token too. If that fails, signing
          // out on this device still works.
          await authApi.logout(token).catch(() => {});
        }
        await forget();
      },
    }),
    [session, googleError, accept, forget],
  );

  return <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>;
}

export function useAuth(): Auth {
  const auth = useContext(AuthContext);
  if (!auth) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return auth;
}

/** Reads "?code=abc&error=x" from a link. React Native has no full URL parser. */
export function queryParams(url: string): Record<string, string> {
  const query = url.split('?')[1]?.split('#')[0] ?? '';
  const params: Record<string, string> = {};
  for (const pair of query.split('&')) {
    const [key, value = ''] = pair.split('=');
    if (key) {
      params[decodeURIComponent(key)] = decodeURIComponent(
        value.replace(/\+/g, ' '),
      );
    }
  }
  return params;
}
