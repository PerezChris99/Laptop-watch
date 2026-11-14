// @ts-check
/** Opt-in FCM enrollment with owner-approved, signed HTTPS relay.
 * Push hints are untrusted and NEVER execute remote actions or imply a
 * person was detected; the app must query the signed incident endpoint.
 */
import { NativeModules, Platform } from 'react-native';
import { signedRelayJson, normalizedRelayUrl } from '../net/signedRelay.js';

/** @returns {Promise<boolean>} */
export async function pushAvailable() {
  const native = Platform.OS === 'android' ? NativeModules.LaptopWatchPush : null;
  if (!native || typeof native.available !== 'function') return false;
  try { return (await native.available()) === true; }
  catch (_) { return false; }
}

/** @param {object} paired @param {string} relayUrl @returns {Promise<object>} */
export async function registerPush(paired, relayUrl) {
  if (!paired?.approved || !await pushAvailable()) {
    throw new Error('FCM not configured or signed pairing unavailable');
  }
  const trusted = normalizedRelayUrl(relayUrl);
  const token = await NativeModules.LaptopWatchPush.getToken();
  if (typeof token !== 'string' || token.length < 20 || token.length > 4096 ||
      /[^!-~]/.test(token)) throw new Error('Invalid native Firebase token');
  const result = await signedRelayJson(paired, trusted, 'POST', '/api/push/register', { token });
  // Clear only after the signed relay accepted the latest token. No raw
  // bearer token is persisted in JavaScript settings or native preferences.
  const native = NativeModules.LaptopWatchPush;
  if (native && typeof native.acknowledgeTokenSync === 'function') {
    await native.acknowledgeTokenSync();
  }
  return result;
}

/** @param {object} paired @param {string} relayUrl @returns {Promise<void>} */
export async function unregisterPush(paired, relayUrl) {
  const trusted = normalizedRelayUrl(relayUrl);
  if (!paired?.approved) throw new Error('Approved pairing required for revocation');
  await signedRelayJson(paired, trusted, 'POST', '/api/push/unregister');
  const native = NativeModules.LaptopWatchPush;
  if (native && typeof native.deleteToken === 'function') await native.deleteToken();
}

/** @param {object} paired @param {string} relayUrl @returns {Promise<object>} */
export async function pushEnrollmentStatus(paired, relayUrl) {
  return signedRelayJson(paired, normalizedRelayUrl(relayUrl), 'GET', '/api/push/status');
}

/** @returns {string} */
export function pushStatusMessage() {
  return 'FCM is optional. Without a configured Firebase project and owner-signed relay, use foreground authenticated incident checks.';
}

/** Reconcile an FCM token rotation only while the owner-approved app is
 * foregrounded and already opted in to the signed relay.
 * @param {object} paired
 * @param {string} relayUrl
 * @returns {Promise<boolean>}
 */
export async function reconcilePushOnForeground(paired, relayUrl) {
  if (!paired?.approved || !relayUrl || !await pushAvailable()) return false;
  const native = NativeModules.LaptopWatchPush;
  if (!native || typeof native.needsTokenSync !== 'function') return false;
  if (await native.needsTokenSync() !== true) return false;
  await registerPush(paired, relayUrl);
  return true;
}
