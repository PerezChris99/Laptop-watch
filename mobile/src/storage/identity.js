// @ts-check
/** Private key seed is stored only via the OS-backed Keychain API; never log it
 * or persist to AsyncStorage. Hardware backing must be verified on device.
 */
import * as Keychain from 'react-native-keychain';
import { b64url, unb64url } from '../crypto/protocol.js';

const SERVICE = 'com.kc.laptopwatch.pairing';

/** @param {object} identity @returns {Promise<void>} */
export async function saveIdentity(identity) {
  if (!identity || !(identity.privateSeed instanceof Uint8Array) ||
      identity.privateSeed.length !== 32 || identity.tlsFingerprint?.length !== 64) {
    throw new Error('Cannot persist invalid pairing identity');
  }
  const serialized = JSON.stringify({ ...identity, privateSeed: b64url(identity.privateSeed) });
  const ok = await Keychain.setGenericPassword('paired', serialized, {
    service: SERVICE, accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
  if (!ok) throw new Error('Secure device storage unavailable; pairing not saved');
}

/** @returns {Promise<object|null>} */
export async function loadIdentity() {
  const credentials = await Keychain.getGenericPassword({ service: SERVICE });
  if (!credentials) return null;
  const raw = JSON.parse(credentials.password);
  const privateSeed = unb64url(raw.privateSeed, 32);
  if (!raw.deviceId || !raw.desktopId || !raw.tlsFingerprint) throw new Error('Stored pairing is invalid');
  return { ...raw, privateSeed };
}

/** @returns {Promise<void>} */
export async function clearIdentity() {
  const done = await Keychain.resetGenericPassword({ service: SERVICE });
  if (!done) throw new Error('Failed to remove pairing from secure storage');
}
