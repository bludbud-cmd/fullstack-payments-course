import { PermissionsAndroid, Platform } from 'react-native';
import { registerDevice, unregisterDevice } from '../api/payments';

/**
 * Push notifications on Android, through Firebase Cloud Messaging (FCM).
 *
 * Firebase needs android/app/google-services.json from your own Firebase
 * project (Module 34 shows how). Without it, and on the Mac, push stays off
 * and the live banner (src/live) still tells people what happened while the
 * app is open.
 */
let registeredToken: string | null = null;
let stopListening: (() => void) | null = null;

/** Loads Firebase only on Android, and only if it is set up. */
function firebase() {
  if (Platform.OS !== 'android') {
    return null;
  }
  try {
    const fcm = require('@react-native-firebase/messaging');
    return { ...fcm, messaging: fcm.getMessaging() };
  } catch {
    return null; // No google-services.json: Firebase has no app to use.
  }
}

/** Asks to show notifications, then tells the API where to send them. */
export async function startPush(): Promise<boolean> {
  const fcm = firebase();
  if (!fcm) {
    return false;
  }
  // Android 13 and newer asks the person first.
  if (Number(Platform.Version) >= 33) {
    const answer = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
    );
    if (answer !== PermissionsAndroid.RESULTS.GRANTED) {
      return false;
    }
  }
  try {
    const token: string = await fcm.getToken(fcm.messaging);
    await registerDevice(token);
    registeredToken = token;
    // Firebase can swap the token at any time; the API needs the new one.
    stopListening = fcm.onTokenRefresh(fcm.messaging, (fresh: string) => {
      registeredToken = fresh;
      registerDevice(fresh).catch(() => {});
    });
    return true;
  } catch {
    return false;
  }
}

/** On sign-out: this phone should stop getting this person's notifications. */
export async function stopPush() {
  stopListening?.();
  stopListening = null;
  const token = registeredToken;
  registeredToken = null;
  if (token) {
    await unregisterDevice(token).catch(() => {});
  }
}
