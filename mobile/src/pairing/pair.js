// @ts-check
/** Pairing cannot bypass the laptop's visible local approval. */
import { bytesToUtf8, utf8ToBytes } from '@noble/hashes/utils';
import {
  b64url, parsePairingQr, phoneProofTranscript, publicKey,
  randomDeviceUUID, secureRandom32, sign,
} from '../crypto/protocol.js';
import { SignedClient } from '../net/SignedClient.js';

/** @param {string} qrText @param {{sendPinned:(request:object)=>Promise<{status:number, body:Uint8Array}>}} transport @param {string} label @returns {Promise<object>} */
export async function requestPairing(qrText, transport, label = 'Android phone') {
  const invitation = parsePairingQr(qrText);
  if (!transport || typeof transport.sendPinned !== 'function') throw new Error('Pinned native transport unavailable');
  const deviceId = randomDeviceUUID();
  const privateSeed = secureRandom32();
  const pubkey = b64url(publicKey(privateSeed));
  const proof = b64url(sign(privateSeed, phoneProofTranscript(deviceId, pubkey, invitation.challenge)));
  const message = {
    device_id: deviceId, pubkey, nonce: invitation.challenge,
    proof, label: label.replace(/[^\x20-\x7e]/g, '').slice(0, 48) || 'Android phone',
  };
  const body = utf8ToBytes(JSON.stringify(message));
  const response = await transport.sendPinned({
    host: invitation.host, port: invitation.port,
    tlsFingerprint: invitation.tlsFingerprint,
    method: 'POST', path: '/pair/request', body,
    headers: { 'Content-Type': 'application/json' },
    timeoutMs: 22000, maxResponseBytes: 4096, followRedirects: false,
  });
  if (response.status < 200 || response.status >= 300) throw new Error('Pairing rejected; check the desktop QR and approval window');
  const parsed = JSON.parse(bytesToUtf8(response.body));
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(parsed.pending_id) ||
      parsed.status !== 'AWAITING_LOCAL_APPROVAL') throw new Error('Unrecognized pairing response');
  return {
    deviceId, privateSeed, desktopId: invitation.desktopId,
    host: invitation.host, port: invitation.port,
    tlsFingerprint: invitation.tlsFingerprint,
    desktopPubkey: invitation.desktopPubkey, approved: false,
    pendingId: parsed.pending_id,
  };
}

/** @param {object} paired @param {object} transport @returns {Promise<object>} */
export async function verifyApproval(paired, transport) {
  const client = new SignedClient(paired, transport);
  const status = await client.json('GET', '/api/status', undefined, { allowPending: true });
  if (status.online !== true) throw new Error('Desktop has not approved this phone');
  return { ...paired, approved: true, pendingId: null };
}
