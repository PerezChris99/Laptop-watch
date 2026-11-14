// @ts-check
/** Android native connectedDevice foreground status polling. Owner opt-in only. */
import { NativeModules, Platform } from 'react-native';
import { b64url, isPrivateIPv4 } from '../crypto/protocol.js';
import { foregroundEligibility } from './policy.js';

/**
 * The secret is only passed to the native module after explicit consent,
 * desktop approval, armed state, and Android notification permission.
 * It is kept in process RAM by the foreground service; never persisted
 * as a service Intent extra, logged or queued as a command.
 *
 * @param {{ownerOptedIn:boolean,pairedApproved:boolean,armed:boolean,notificationsGranted:boolean}} consent
 * @param {{deviceId:string,privateSeed:Uint8Array,host:string,port:number,tlsFingerprint:string,approved:boolean}} paired
 * @returns {Promise<void>}
 */
export async function startForegroundConnection(consent, paired) {
  const decision = foregroundEligibility(consent);
  if (!decision.allowed) throw new Error(decision.reason);
  if (Platform.OS !== 'android') throw new Error('ANDROID_ONLY_BACKGROUND_SERVICE');
  const native = NativeModules.LaptopWatchForegroundConnection;
  if (!native || typeof native.start !== 'function') {
    throw new Error('BACKGROUND_CONNECTION_NOT_AVAILABLE');
  }
  if (!paired.approved || !isPrivateIPv4(paired.host) ||
      !/^[a-f0-9]{64}$/.test(paired.tlsFingerprint) ||
      paired.privateSeed?.length !== 32) {
    throw new Error('INVALID_SIGNED_PAIRING');
  }
  await native.start({
    deviceId: paired.deviceId, host: paired.host, port: paired.port,
    tlsFingerprint: paired.tlsFingerprint,
    privateSeedB64: b64url(paired.privateSeed),
  });
}

/** @returns {Promise<void>} */
export async function stopForegroundConnection() {
  const native = NativeModules.LaptopWatchForegroundConnection;
  if (!native || typeof native.stop !== 'function') return;
  await native.stop();
}
