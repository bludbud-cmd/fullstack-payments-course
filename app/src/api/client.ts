import { API_BASE_URL } from '../config';

/** An error response from the API, in its "problem details" format. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly title: string,
    detail: string,
    readonly code?: string,
    readonly fields?: Record<string, string>,
  ) {
    super(detail);
  }
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Send the same key again when retrying, so money never moves twice. */
  idempotencyKey?: string;
};

/**
 * How the client gets an access token, and gets a new one when it expires.
 * The sign-in code (src/auth/AuthContext.tsx) plugs itself in here.
 */
export type TokenSource = {
  accessToken: () => string | null;
  /** Swaps the refresh token for new tokens. Resolves false if the person must sign in again. */
  refresh: () => Promise<boolean>;
};

let tokens: TokenSource | null = null;

export function setTokenSource(source: TokenSource | null) {
  tokens = source;
}

export async function request<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const token = tokens?.accessToken() ?? null;
  const response = await send(path, options, token);

  // Access tokens last 15 minutes. When one has expired, get a new one and
  // try once more, so the person never notices.
  if (response.status === 401 && token && tokens && (await tokens.refresh())) {
    return read<T>(await send(path, options, tokens.accessToken()));
  }
  return read<T>(response);
}

async function send(
  path: string,
  options: RequestOptions,
  token: string | null,
): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey;
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  try {
    return await fetch(API_BASE_URL + path, {
      method: options.method ?? 'GET',
      headers,
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError(
      0,
      'No connection',
      'Could not reach PayDeck. Check that the API is running.',
    );
  }
}

async function read<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }
  const json = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(
      response.status,
      json?.title ?? 'Something went wrong',
      json?.detail ?? `The server answered ${response.status}`,
      json?.code,
      json?.fields,
    );
  }
  return json as T;
}

/**
 * A random ID for an Idempotency-Key. Hermes has no crypto.randomUUID, and
 * this key only has to be unique, not secret, so Math.random is enough.
 */
export function newIdempotencyKey(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, char => {
    const random = Math.floor(Math.random() * 16);
    // The "y" digit must be 8, 9, a or b to mark the ID as a standard UUID.
    return (char === 'x' ? random : 8 + (random % 4)).toString(16);
  });
}
