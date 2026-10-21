import { ApiError, newIdempotencyKey, request } from '../src/api/client';

const respond = (status: number, body?: unknown) =>
  jest.fn(async () => ({
    status,
    ok: status >= 200 && status < 300,
    json: async () => body,
  }));

it('returns the JSON body of a successful response', async () => {
  globalThis.fetch = respond(200, { id: 'a1' }) as unknown as typeof fetch;
  await expect(request('/api/accounts/a1')).resolves.toEqual({ id: 'a1' });
});

it('turns problem details into an ApiError', async () => {
  globalThis.fetch = respond(422, {
    title: 'Not enough money',
    detail: 'The balance is too low',
    code: 'INSUFFICIENT_FUNDS',
  }) as unknown as typeof fetch;
  const error = await request('/api/transfers', {
    method: 'POST',
    body: {},
  }).catch(e => e);
  expect(error).toBeInstanceOf(ApiError);
  expect(error).toMatchObject({
    status: 422,
    title: 'Not enough money',
    message: 'The balance is too low',
    code: 'INSUFFICIENT_FUNDS',
  });
});

it('explains a failure without a body', async () => {
  globalThis.fetch = respond(502) as unknown as typeof fetch;
  await expect(request('/api/accounts/a1')).rejects.toThrow(
    'The server answered 502',
  );
});

it('reports a network failure in plain words', async () => {
  globalThis.fetch = jest.fn(async () => {
    throw new TypeError('Network request failed');
  }) as unknown as typeof fetch;
  const error = await request('/api/accounts/a1').catch(e => e);
  expect(error).toMatchObject({ status: 0, title: 'No connection' });
});

it('sends JSON and the idempotency key', async () => {
  const fetchMock = respond(201, {});
  globalThis.fetch = fetchMock as unknown as typeof fetch;
  await request('/api/x', {
    method: 'POST',
    body: { a: 1 },
    idempotencyKey: 'k1',
  });
  expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/x', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'Idempotency-Key': 'k1',
    },
    body: '{"a":1}',
  });
});

it('makes a fresh version 4 UUID each time', () => {
  const a = newIdempotencyKey();
  expect(a).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
  );
  expect(newIdempotencyKey()).not.toBe(a);
});
