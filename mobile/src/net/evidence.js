// @ts-check
/** Signed, app-private media transfer. Never use a web URL or external browser. */
import { NativeModules, Platform } from 'react-native';
import { buildSignedRequest } from './SignedClient.js';

/**
 * @param {import('./SignedClient.js').SignedClient} client
 * @param {{id:string,kind:'photo'|'video',size_bytes:number}} item
 * @returns {Promise<{uri:string,kind:string,id:string,sizeBytes:number}>}
 */
export async function downloadEvidence(client, item) {
  if (!item || !/^[a-f0-9]{32}$/.test(item.id) ||
      !['photo', 'video'].includes(item.kind) ||
      !Number.isSafeInteger(item.size_bytes) || item.size_bytes < 1 ||
      item.size_bytes > 128 * 1024 * 1024) {
    throw new Error('Refusing unverified evidence metadata');
  }
  if (!client?.paired?.approved) throw new Error('Paired owner authorization is required');
  const native = Platform.OS === 'ios'
    ? NativeModules.LaptopWatchPinnedTransport
    : NativeModules.LaptopWatchEvidence;
  if (!native || typeof native.downloadPinned !== 'function') {
    throw new Error('Private pinned evidence transport not available');
  }
  // Each download is signed with a *new* nonce, regardless of whether this
  // item was previously fetched. Failed downloads are never queued.
  const body = new Uint8Array(0);
  const request = buildSignedRequest(
    client.paired, 'GET', `/api/evidence/${item.kind}/${item.id}`, body,
  );
  let result;
  try { result = await native.downloadPinned({ ...request, bodyBase64: '', body: undefined }); }
  catch (_) { throw new Error('Evidence unavailable or TLS certificate did not match'); }
  if (!result || result.kind !== item.kind || result.id !== item.id ||
      typeof result.uri !== 'string' || !result.uri.startsWith('file://') ||
      !Number.isFinite(result.sizeBytes) || result.sizeBytes <= 0) {
    throw new Error('Native evidence download returned an invalid result');
  }
  return result;
}

/** App-private evidence is only exportable through a native, explicit owner
 * document picker/share sheet. It is never uploaded automatically.
 * @param {{kind:string,id:string}} item
 * @returns {Promise<void>}
 */
export async function exportEvidenceLocal(item) {
  if (!item || !['photo','video'].includes(item.kind) ||
      !/^[a-f0-9]{32}$/.test(item.id)) throw new Error('Invalid local evidence');
  const native = Platform.OS === 'ios'
    ? NativeModules.LaptopWatchPinnedTransport
    : NativeModules.LaptopWatchEvidenceManagement;
  if (!native || typeof native.exportLocal !== 'function') {
    throw new Error('User-controlled native evidence export unavailable');
  }
  await native.exportLocal(item.kind, item.id);
}

/** Deletes only the phone's private copy; desktop evidence is unaffected.
 * @param {{kind:string,id:string}} item
 * @returns {Promise<void>}
 */
export async function deleteEvidenceLocal(item) {
  if (!item || !['photo','video'].includes(item.kind) ||
      !/^[a-f0-9]{32}$/.test(item.id)) throw new Error('Invalid local evidence');
  const native = Platform.OS === 'ios'
    ? NativeModules.LaptopWatchPinnedTransport
    : NativeModules.LaptopWatchEvidenceManagement;
  if (!native || typeof native.deleteLocal !== 'function') {
    throw new Error('Private native evidence deletion unavailable');
  }
  await native.deleteLocal(item.kind, item.id);
}
