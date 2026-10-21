import { parseMoneyInput } from './money';

// Each check returns an error message to show, or null when the input is fine.
// The API checks the same rules again: the app checks first so people get
// help straight away, the server checks because it can never trust the app.

export const MAX_TOP_UP_CENTS = 10_000_000; // Matches the API's limit: 100,000.00

export function nicknameError(nickname: string): string | null {
  const trimmed = nickname.trim();
  if (trimmed.length === 0) {
    return 'Give the card a name';
  }
  if (trimmed.length > 50) {
    return 'Use 50 characters or fewer';
  }
  return null;
}

export function topUpError(text: string): string | null {
  const cents = parseMoneyInput(text);
  if (cents === null) {
    return 'Enter an amount like 25 or 25.50';
  }
  if (cents > MAX_TOP_UP_CENTS) {
    return 'The most you can top up at once is 100,000';
  }
  return null;
}

/** A spending limit is optional: an empty box means "no limit". */
export function limitError(text: string): string | null {
  if (text.trim() === '') {
    return null;
  }
  return parseMoneyInput(text) === null
    ? 'Enter an amount like 200, or leave it empty'
    : null;
}

export function parseLimit(text: string): number | null {
  return text.trim() === '' ? null : parseMoneyInput(text);
}

/** A light check that catches typos. The API does the full check. */
export function emailError(text: string): string | null {
  const trimmed = text.trim();
  if (trimmed === '') {
    return 'Enter your email';
  }
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)
    ? null
    : 'That does not look like an email address';
}

/** Matches the API: long passwords are what keep accounts safe, not odd symbols. */
export function passwordError(text: string): string | null {
  if (text.length < 10) {
    return 'Use at least 10 characters';
  }
  if (text.length > 128) {
    return 'Use 128 characters or fewer';
  }
  return null;
}

/** Optional on a new card: empty means you keep the card yourself. */
export function cardholderEmailError(text: string): string | null {
  return text.trim() === '' ? null : emailError(text);
}
