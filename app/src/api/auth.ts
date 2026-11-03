import { API_BASE_URL } from '../config';
import type { Account, Card, Role, User } from '../types';
import { request } from './client';

/** What the API sends back whenever someone signs in. */
export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: User;
};

/** The signed-in person, the accounts they own, and cards other people gave them. */
export type Me = {
  user: User;
  accounts: Account[];
  heldCards: Card[];
};

export type { Role };

export const register = (
  email: string,
  password: string,
  displayName: string,
) =>
  request<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: { email, password, displayName },
    withToken: false,
  });

export const login = (email: string, password: string) =>
  request<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: { email, password },
    withToken: false,
  });

export const refresh = (refreshToken: string) =>
  request<AuthResponse>('/api/auth/refresh', {
    method: 'POST',
    body: { refreshToken },
    withToken: false,
  });

export const logout = (refreshToken: string) =>
  request<void>('/api/auth/logout', {
    method: 'POST',
    body: { refreshToken },
    withToken: false,
  });

/** Trades the one-time code from "Sign in with Google" for tokens. */
export const exchangeCode = (code: string, codeVerifier: string) =>
  request<AuthResponse>('/api/auth/token', {
    method: 'POST',
    body: { code, codeVerifier },
    withToken: false,
  });

export const getMe = () => request<Me>('/api/me');

/** The link that starts "Sign in with Google" in the browser. */
export const googleSignInUrl = (codeChallenge: string) =>
  `${API_BASE_URL}/api/auth/google?redirect_uri=${encodeURIComponent(
    APP_REDIRECT_URI,
  )}` + `&code_challenge=${codeChallenge}&code_challenge_method=S256`;

/** The app's own link. The API sends the browser here after Google. */
export const APP_REDIRECT_URI = 'paydeck://auth';
