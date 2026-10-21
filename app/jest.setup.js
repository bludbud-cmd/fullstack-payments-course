/* eslint-env jest */
// AsyncStorage talks to the phone's storage, which does not exist in tests.
// This swaps it for an in-memory version.
jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);
