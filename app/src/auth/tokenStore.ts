import * as Keychain from 'react-native-keychain';

// The refresh token lets anyone holding it sign in for 30 days, so it lives in
// the Android Keystore or the macOS Keychain, never in plain AsyncStorage.
// The short-lived access token is only ever kept in memory.
const SERVICE = 'dev.paydeck.refresh-token';

export async function saveRefreshToken(token: string): Promise<void> {
  await Keychain.setGenericPassword('paydeck', token, { service: SERVICE });
}

export async function loadRefreshToken(): Promise<string | null> {
  const saved = await Keychain.getGenericPassword({ service: SERVICE });
  return saved ? saved.password : null;
}

export async function clearRefreshToken(): Promise<void> {
  await Keychain.resetGenericPassword({ service: SERVICE });
}
