import { Platform } from 'react-native';

/**
 * Where the PayDeck API runs. On your Mac it is localhost. The Android
 * emulator is a separate (virtual) device, so it reaches your Mac through
 * the special address 10.0.2.2. Part 10 points this at the deployed API.
 */
export const API_BASE_URL =
  Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';
