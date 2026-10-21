// Gives Hermes crypto.getRandomValues, backed by the phone's or Mac's secure random generator.
import 'react-native-get-random-values';
import { sha256 } from 'js-sha256';

// Added by react-native-get-random-values; TypeScript does not know about it.
declare const crypto: { getRandomValues<T extends Uint8Array>(array: T): T };

const ALPHABET =
  'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

/** Base64 with URL-safe characters and no padding, as PKCE requires. */
/* eslint-disable no-bitwise -- base64 works on bits: each character is 6 of them */
export function base64Url(bytes: Uint8Array): string {
  let out = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const n =
      (bytes[i] << 16) | ((bytes[i + 1] ?? 0) << 8) | (bytes[i + 2] ?? 0);
    const chars = Math.min(4, Math.ceil(((bytes.length - i) * 8) / 6));
    for (let c = 0; c < chars; c++) {
      out += ALPHABET[(n >> (18 - 6 * c)) & 63];
    }
  }
  return out;
}

/* eslint-enable no-bitwise */

/** A secret only this app knows: 32 random bytes, 43 characters. */
export function newCodeVerifier(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

/** What the app sends first: the SHA-256 of the verifier. The verifier itself comes later. */
export function codeChallenge(verifier: string): string {
  return base64Url(new Uint8Array(sha256.arrayBuffer(verifier)));
}
