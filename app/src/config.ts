import { Platform } from 'react-native';

/**
 * Where your deployed API lives (Module 36). Change this to your own address
 * before building a release. It must be https://, because release builds of
 * Android and macOS refuse plain http:// to other computers.
 */
const PRODUCTION_API_URL = 'https://api.example.com';

/**
 * Where the PayDeck API runs. While you develop (__DEV__ is true) it is your
 * Mac: localhost on the Mac itself, and the special address 10.0.2.2 from the
 * Android emulator, which is a separate (virtual) device. Release builds use
 * the deployed API.
 */
export const API_BASE_URL = __DEV__
  ? Platform.OS === 'android'
    ? 'http://10.0.2.2:8080'
    : 'http://localhost:8080'
  : PRODUCTION_API_URL;
