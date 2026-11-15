// @ts-check
/** Owner-configured public-CA HTTPS relay, separate from QR-pinned LAN API.
 * No redirects, custom URLs, ports, unsigned requests, or command actions.
 */
import { NativeModules, Platform } from 'react-native';
import { bytesToUtf8, utf8ToBytes } from '@noble/hashes/utils';
import { b64url, canonicalRequest, secureRandom16, sign } from '../crypto/protocol.js';
import { fromByteArray, toByteArray } from 'base64-js';
import { getPreference } from '../storage/db.js';

const RELAY_ROUTES = new Set([
  'GET /api/location/latest', 'POST /api/incidents/query',
  'POST /api/push/register', 'POST /api/push/unregister', 'GET /api/push/status',
]);

/** @param {string} raw @returns {string} */
export function normalizedRelayUrl(raw) {
  if (typeof raw !== 'string' || raw.length > 256) throw new Error('Invalid relay URL');
  const url = new URL(raw);
  if (url.protocol !== 'https:' || url.username || url.password ||
      url.search || url.hash || (url.pathname !== '/' && url.pathname !== '') ||
      (url.port !== '' && url.port !== '443') ||
      !/^[a-z0-9][a-z0-9.-]{3,218}[a-z0-9]$/i.test(url.hostname) ||
      !url.hostname.includes('.') || /^[\d.]+$/.test(url.hostname) ||
      url.hostname.endsWith('.invalid')) throw new Error('Relay must be an owner-approved HTTPS DNS domain');
  return `https://${url.hostname}`;
}

/** @param {object} paired @param {string} relayUrl @param {string} method @param {string} path @param {unknown} data @returns {Promise<object>} */
export async function signedRelayJson(paired, relayUrl, method, path, data = undefined) {
  const base = normalizedRelayUrl(relayUrl);
  if (!paired?.approved) throw new Error('Owner-approved signing identity required');
  if (!RELAY_ROUTES.has(`${method} ${path}`)) throw new Error('Unsupported relay endpoint');
  const body = data === undefined ? new Uint8Array(0) : utf8ToBytes(JSON.stringify(data));
  if (body.length > 4096) throw new Error('Signed relay body too large');
  const native = Platform.OS === 'ios'
    ? NativeModules.LaptopWatchPinnedTransport : NativeModules.LaptopWatchRelay;
  if (!native || typeof native.sendSignedRelay !== 'function') {
    throw new Error('Native OS-CA verified, no-redirect HTTPS relay module unavailable');
  }
  const timestamp = Math.floor(Date.now() / 1000);
  const nonce = b64url(secureRandom16());
  const signature = b64url(sign(
    paired.privateSeed, canonicalRequest(method, path, body, timestamp, nonce, paired.deviceId),
  ));
  // The server verifies this independently; client claims never grant remote rights.
  const token=await getPreference('commercial_signed_lease').catch(()=>null);
  const headers={
    'X-LaptopWatch-Device':paired.deviceId,
    'X-LaptopWatch-Nonce':nonce,
    'X-LaptopWatch-Timestamp':String(timestamp),
    'X-LaptopWatch-Signature':signature,
  };
  if (typeof token==='string' && token.length>=20 && token.length<=8192)
    headers['X-LaptopWatch-License']=token;
  const request = {
    relayUrl: base, method, path, bodyBase64: data === undefined ? '' : fromByteArray(body),
    headers,
    followRedirects: false,
    timeoutMs: 15000,
  };
  let response;
  try { response = await native.sendSignedRelay(request); }
  catch (_) { throw new Error('Signed relay unreachable or TLS not trusted'); }
  if (!Number.isInteger(response.status) || response.status < 200 || response.status >= 300 ||
      typeof response.bodyBase64 !== 'string' || response.bodyBase64.length > 1400000) {
    throw new Error('Signed relay rejected the request');
  }
  const bytes = toByteArray(response.bodyBase64);
  try { return JSON.parse(bytesToUtf8(bytes)); }
  catch (_) { throw new Error('Invalid signed relay JSON response'); }
}

