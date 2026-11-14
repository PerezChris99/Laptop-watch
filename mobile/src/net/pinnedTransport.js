// @ts-check
/** Fail-closed native bridge. The Android module must validate the leaf DER SHA256
 * from the physical QR on EVERY request. Plain fetch is prohibited.
 */
import { NativeModules } from 'react-native';
import { fromByteArray, toByteArray } from 'base64-js';

const native = NativeModules.LaptopWatchPinnedTransport;

/** @returns {{sendPinned:(request:object)=>Promise<{status:number,body:Uint8Array,contentType:string}>}} */
export function pinnedTransport() {
  if (!native || typeof native.sendPinned !== 'function') {
    throw new Error('Certificate-pinned native transport not installed. Controls disabled.');
  }
  return {
    async sendPinned(request) {
      if (!request || request.followRedirects !== false || !/^[0-9a-f]{64}$/.test(request.tlsFingerprint)) {
        throw new Error('Refusing a request without strict certificate pinning');
      }
      const encoded = fromByteArray(request.body);
      const out = await native.sendPinned({
        ...request, bodyBase64: encoded, body: undefined,
      });
      if (!out || !Number.isInteger(out.status) || typeof out.bodyBase64 !== 'string') {
        throw new Error('Untrusted native transport response');
      }
      if (out.bodyBase64.length > (request.maxResponseBytes * 2 + 8)) {
        throw new Error('Native response exceeded declared maximum');
      }
      return { status: out.status, body: toByteArray(out.bodyBase64), contentType: out.contentType || '' };
    },
  };
}
