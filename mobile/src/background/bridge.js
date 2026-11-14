// @ts-check
/** Native FGS must never be simulated by JS timers while the app is asleep. */
import { NativeModules, Platform } from 'react-native';
import { foregroundEligibility } from './policy.js';

/**
 * Native module must first prove the signed long-poll/background protocol,
 * Android FGS type/notification permission, and real device lifecycle.
 *
 * @param {{ownerOptedIn:boolean,pairedApproved:boolean,armed:boolean,notificationsGranted:boolean}} consent
 * @returns {Promise<void>}
 */
export async function startForegroundConnection(consent) {
  const decision = foregroundEligibility(consent);
  if (!decision.allowed) throw new Error(decision.reason);
  if (Platform.OS !== 'android') throw new Error('Android foreground service only');
  const bridge = NativeModules.LaptopWatchForegroundConnection;
  if (!bridge || typeof bridge.start !== 'function') {
    throw new Error('BACKGROUND_CONNECTION_NOT_AVAILABLE');
  }
  // No signed long-poll transport has been accepted yet. Do not claim success
  // or call an unsafe stub; native module must own validated connection state.
  await bridge.start();
}

/** @returns {Promise<void>} */
export async function stopForegroundConnection() {
  const bridge = NativeModules.LaptopWatchForegroundConnection;
  if (!bridge || typeof bridge.stop !== 'function') return;
  await bridge.stop();
}
