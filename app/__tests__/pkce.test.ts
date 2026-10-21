import { base64Url, codeChallenge, newCodeVerifier } from '../src/auth/pkce';
import { queryParams } from '../src/auth/AuthContext';

it('encodes bytes as URL-safe base64 without padding', () => {
  expect(base64Url(new Uint8Array([251, 255]))).toBe('-_8');
  expect(base64Url(new Uint8Array([102, 111, 111, 98, 97, 114]))).toBe(
    'Zm9vYmFy',
  );
});

it('makes the challenge from RFC 7636, appendix B', () => {
  expect(codeChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe(
    'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM',
  );
});

it('makes a different 43-character verifier every time', () => {
  const verifier = newCodeVerifier();
  expect(verifier).toMatch(/^[A-Za-z0-9_-]{43}$/);
  expect(newCodeVerifier()).not.toBe(verifier);
});

it('reads the code or error from the link back to the app', () => {
  expect(queryParams('paydeck://auth?code=abc%2F123')).toEqual({
    code: 'abc/123',
  });
  expect(queryParams('paydeck://auth?error=access_denied#')).toEqual({
    error: 'access_denied',
  });
  expect(queryParams('paydeck://auth')).toEqual({});
});
