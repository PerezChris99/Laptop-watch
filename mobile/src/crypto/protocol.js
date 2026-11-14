// @ts-check
/** LW1 byte transcript and QR verifier. All bytes are UTF-8, never normalized JSON. */
import * as ed from '@noble/ed25519';
import { sha256 } from '@noble/hashes/sha256';
import { sha512 } from '@noble/hashes/sha512';
import { bytesToHex, utf8ToBytes } from '@noble/hashes/utils';
import { fromByteArray, toByteArray } from 'base64-js';

ed.etc.sha512Sync = (...messages) => sha512(ed.etc.concatBytes(...messages));

const API_PATH = /^\/api\/[A-Za-z0-9/_-]{1,180}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const BASE64URL = /^[A-Za-z0-9_-]+$/;

/** @param {Uint8Array} bytes @returns {string} */
export function b64url(bytes) {
  return fromByteArray(bytes).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

/** @param {string} encoded @param {number} expected @returns {Uint8Array} */
export function unb64url(encoded, expected) {
  if (typeof encoded !== 'string' || !BASE64URL.test(encoded) || encoded.length > expected * 2) {
    throw new Error('Invalid encoded binary input');
  }
  const output = toByteArray(encoded.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - encoded.length % 4) % 4));
  if (output.length !== expected || b64url(output) !== encoded) throw new Error('Invalid encoded length');
  return output;
}

/** @param {string} host @returns {boolean} */
export function isPrivateIPv4(host) {
  if (typeof host !== 'string' || !/^\d{1,3}(\.\d{1,3}){3}$/.test(host)) return false;
  const parts = host.split('.');
  if (parts.some((part) => (part.length > 1 && part[0] === '0') || Number(part) > 255)) return false;
  const [a, b, c, d] = parts.map(Number);
  return a === 10 || (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && b === 168) || (a === 127 && b === 0 && c === 0 && d === 1);
}

/** @param {string} method @param {string} path @param {Uint8Array} body @param {number} timestamp @param {string} nonce @param {string} deviceId @returns {Uint8Array} */
export function canonicalRequest(method, path, body, timestamp, nonce, deviceId) {
  const verb = method.toUpperCase();
  if (!['GET', 'POST', 'PUT', 'DELETE'].includes(verb) ||
      !API_PATH.test(path) || path.includes('//') || path.includes('..') ||
      !UUID.test(deviceId) || !Number.isSafeInteger(timestamp) ||
      !(body instanceof Uint8Array) || body.byteLength > 1048576) {
    throw new Error('Invalid signed request');
  }
  unb64url(nonce, 16);
  return utf8ToBytes(`LW1\n${verb}\n${path}\n${bytesToHex(sha256(body))}\n${timestamp}\n${nonce}\n${deviceId}\n`);
}

/** @param {Uint8Array} seed @param {Uint8Array} message @returns {Uint8Array} */
export function sign(seed, message) {
  if (!(seed instanceof Uint8Array) || seed.length !== 32) throw new Error('Invalid private seed');
  return ed.sign(message, seed);
}

/** @param {Uint8Array} pub @param {Uint8Array} message @param {Uint8Array} signature @returns {boolean} */
export function verify(pub, message, signature) {
  if (pub.length !== 32 || signature.length !== 64) return false;
  return ed.verify(signature, message, pub);
}

/** @param {Uint8Array} seed @returns {Uint8Array} */
export function publicKey(seed) {
  if (seed.length !== 32) throw new Error('Invalid private seed');
  return ed.getPublicKey(seed);
}

/** @returns {Uint8Array} */
export function secureRandom16() {
  if (!globalThis.crypto || typeof globalThis.crypto.getRandomValues !== 'function') {
    throw new Error('Secure random generator unavailable');
  }
  const bytes = new Uint8Array(16);
  globalThis.crypto.getRandomValues(bytes);
  return bytes;
}

/** @returns {Uint8Array} */
export function secureRandom32() {
  const a = secureRandom16();
  const b = secureRandom16();
  const seed = new Uint8Array(32);
  seed.set(a, 0); seed.set(b, 16);
  return seed;
}

/** @returns {string} */
export function randomDeviceUUID() {
  const b = secureRandom16();
  b[6] = (b[6] & 15) | 64;
  b[8] = (b[8] & 63) | 128;
  const hex = bytesToHex(b);
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}

/** @param {string} raw @param {number} nowSeconds @returns {object} */
export function parsePairingQr(raw, nowSeconds = Math.floor(Date.now() / 1000)) {
  if (typeof raw !== 'string' || raw.length < 40 || raw.length > 4096) throw new Error('Invalid QR size');
  const uri = new URL(raw);
  if (uri.protocol !== 'laptopwatch:' || uri.hostname !== 'pair' || uri.pathname !== '' ||
      uri.hash || uri.username || uri.password) throw new Error('Not a LaptopWatch pairing QR');
  const keys = ['v','id','host','port','pk','fp','nonce','exp','sig'];
  if (Array.from(uri.searchParams.keys()).length !== keys.length ||
      keys.some((key) => uri.searchParams.getAll(key).length !== 1)) throw new Error('Invalid QR fields');
  const value = (/** @type {string} */ key) => uri.searchParams.get(key);
  const id = value('id'), host = value('host'), port = Number(value('port'));
  const pk = value('pk'), fp = value('fp'), nonce = value('nonce'), exp = Number(value('exp'));
  if (value('v') !== '1' || !UUID.test(id) || !isPrivateIPv4(host) ||
      !Number.isInteger(port) || port < 1024 || port > 65535 ||
      !/^[0-9a-f]{64}$/.test(fp) || !Number.isSafeInteger(exp) ||
      exp < nowSeconds - 30 || exp > nowSeconds + 180) throw new Error('Invalid or expired pairing QR');
  const publicBytes = unb64url(pk, 32), signature = unb64url(value('sig'), 64);
  unb64url(nonce, 16);
  const transcript = utf8ToBytes(`LWPAIR1\n${id}\n${host}\n${port}\n${pk}\n${fp}\n${nonce}\n${exp}\n`);
  if (!verify(publicBytes, transcript, signature)) throw new Error('Invalid desktop QR signature');
  return { desktopId: id, host, port, desktopPubkey: pk, tlsFingerprint: fp, challenge: nonce, expiresSeconds: exp };
}

/** @param {string} phoneId @param {string} phonePubkey @param {string} challenge @returns {Uint8Array} */
export function phoneProofTranscript(phoneId, phonePubkey, challenge) {
  if (!UUID.test(phoneId)) throw new Error('Invalid phone identity');
  unb64url(phonePubkey, 32); unb64url(challenge, 16);
  return utf8ToBytes(`LWPAIR-PROOF1\n${phoneId}\n${phonePubkey}\n${challenge}\n`);
}
