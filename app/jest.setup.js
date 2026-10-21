/* eslint-env jest */
// Native modules do not exist in tests, so swap them for simple versions.

// The Keychain / Keystore becomes a Map. Tests can read and fill it through
// global.fakeKeychain.
global.fakeKeychain = new Map();
jest.mock('react-native-keychain', () => ({
  setGenericPassword: jest.fn(async (username, password, { service }) => {
    global.fakeKeychain.set(service, password);
    return true;
  }),
  getGenericPassword: jest.fn(async ({ service }) =>
    global.fakeKeychain.has(service)
      ? { username: 'paydeck', password: global.fakeKeychain.get(service) }
      : false,
  ),
  resetGenericPassword: jest.fn(async ({ service }) =>
    global.fakeKeychain.delete(service),
  ),
}));

// Node already has crypto.getRandomValues, so the polyfill is not needed.
jest.mock('react-native-get-random-values', () => ({}));
