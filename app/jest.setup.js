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

// Firebase (push notifications) has no native side in tests.
jest.mock('@react-native-firebase/messaging', () => ({
  getMessaging: jest.fn(() => ({})),
  getToken: jest.fn(async () => 'fcm-token-1'),
  onTokenRefresh: jest.fn(() => jest.fn()),
}));

// A WebSocket that never touches the network. Tests find the app's sockets in
// global.fakeSockets, read what the app sent, and play the server with receive().
global.fakeSockets = [];
global.WebSocket = class FakeWebSocket {
  constructor(url) {
    this.url = url;
    this.sent = [];
    this.closed = false;
    global.fakeSockets.push(this);
    setTimeout(() => this.onopen?.(), 0);
  }
  send(data) {
    this.sent.push(
      typeof data === 'string' ? data : String.fromCharCode(...data),
    );
  }
  close() {
    this.closed = true;
    this.onclose?.();
  }
  receive(text) {
    this.onmessage?.({ data: text });
  }
};
