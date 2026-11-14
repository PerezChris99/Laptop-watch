// @ts-check
/** mDNS only supplies an untrusted LAN candidate. The already scanned QR
 * fingerprint and a freshly Ed25519-signed status are the authority.
 */
import { NativeModules, Platform } from 'react-native';
import { SignedClient } from '../net/SignedClient.js';
import { isPrivateIPv4 } from '../crypto/protocol.js';
import { pinnedTransport } from '../net/pinnedTransport.js';

/**
 * @param {object} paired
 * @returns {Promise<object>} Updated in-memory pairing, not persisted.
 */
export async function rediscoverPairedLaptop(paired) {
  if (!paired?.approved || Platform.OS !== 'android') {
    throw new Error('Approved Android pairing required');
  }
  const bridge = NativeModules.LaptopWatchDiscovery;
  if (!bridge || typeof bridge.find !== 'function') {
    throw new Error('Trusted LAN discovery is unavailable');
  }
  const host = await bridge.find({
    desktopId: paired.desktopId,
    tlsFingerprint: paired.tlsFingerprint,
    port: paired.port,
  });
  if (!isPrivateIPv4(host)) throw new Error('Untrusted mDNS candidate');
  const candidate = { ...paired, host };
  const result = await new SignedClient(candidate, pinnedTransport()).status();
  if (result.online !== true) throw new Error('mDNS candidate failed pinned signed verification');
  return candidate;
}
