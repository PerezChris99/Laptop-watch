// @ts-check
/** Phone -> Python gateway: one exact signed protocol, fail-closed pinned transport. */
import { bytesToUtf8, utf8ToBytes } from '@noble/hashes/utils';
import { b64url, canonicalRequest, isPrivateIPv4, secureRandom16, sign } from '../crypto/protocol.js';

const ROUTES = new Set([
  'GET /api/status', 'POST /api/config/armed',
  'POST /api/lock', 'POST /api/alarm', 'POST /api/warning/tts',
  'GET /api/camera/frame', 'POST /api/camera/photo',
  'GET /api/evidence/list',
  'POST /api/camera/record/start', 'POST /api/camera/record/stop',
  'POST /api/webrtc/offer', 'POST /api/webrtc/stop',
  'GET /api/location/last-seen', 'POST /api/location/refresh',
  'POST /api/incidents/query', 'POST /api/incidents/clear',
  'POST /api/config/person-monitor',
]);

/** @typedef {{deviceId:string, privateSeed:Uint8Array, host:string, port:number, tlsFingerprint:string, approved:boolean}} PairedDevice */
/** @typedef {{status:number, body:Uint8Array, contentType?:string}} PinnedResponse */
/** @typedef {{sendPinned:(request:object)=>Promise<PinnedResponse>}} PinnedTransport */

export class GatewayError extends Error {
  /** @param {string} code @param {string} message */
  constructor(code, message) { super(message); this.name = 'GatewayError'; this.code = code; }
}

/** @param {number} status @returns {string} */
function classify(status) {
  if (status === 401) return 'UNAUTHENTICATED';
  if (status === 403) return 'FORBIDDEN';
  if (status === 409) return 'CONFLICT';
  if (status === 413) return 'BODY_TOO_LARGE';
  if (status === 429) return 'RATE_LIMITED';
  if (status === 503 || status === 502 || status === 504) return 'UNAVAILABLE';
  return 'HTTP_ERROR';
}

/** JSON body uses the bytes sent over transport; never hash a regenerated string. 
 * @param {unknown} payload
 * @returns {Uint8Array}
 */
export function jsonBody(payload) {
  if (payload === undefined || payload === null) return new Uint8Array(0);
  return utf8ToBytes(JSON.stringify(payload));
}

/** Construct request without sending. Includes a fresh nonce on EVERY attempt.
 * @param {PairedDevice} paired
 * @param {string} method
 * @param {string} path
 * @param {Uint8Array} body
 * @returns {object}
 */
export function buildSignedRequest(paired, method, path, body) {
  if (!isPrivateIPv4(paired.host) || !Number.isInteger(paired.port) ||
      paired.port < 1024 || paired.port > 65535 ||
      !/^[a-f0-9]{64}$/.test(paired.tlsFingerprint)) {
    throw new GatewayError('INVALID_PAIRING', 'No valid certificate-pinned LAN pairing');
  }
  if (!ROUTES.has(`${method} ${path}`) && !/^GET \/api\/evidence\/(photo|video)\/[a-f0-9]{32}$/.test(`${method} ${path}`)) {
    throw new GatewayError('FORBIDDEN', 'Unknown or unsupported gateway route');
  }
  const nonce = b64url(secureRandom16());
  const timestamp = Math.floor(Date.now() / 1000);
  const message = canonicalRequest(method, path, body, timestamp, nonce, paired.deviceId);
  const signature = b64url(sign(paired.privateSeed, message));
  return {
    host: paired.host,
    port: paired.port,
    tlsFingerprint: paired.tlsFingerprint,
    method, path, body,
    headers: {
      'X-LaptopWatch-Device': paired.deviceId,
      'X-LaptopWatch-Nonce': nonce,
      'X-LaptopWatch-Timestamp': String(timestamp),
      'X-LaptopWatch-Signature': signature,
    },
    timeoutMs: 22000,
    maxResponseBytes: 150 * 1024 * 1024,
    followRedirects: false,
  };
}

/** @param {PairedDevice} paired @param {PinnedTransport} transport */
export class SignedClient {
  constructor(paired, transport) {
    if (!transport || typeof transport.sendPinned !== 'function') {
      throw new GatewayError('PIN_UNAVAILABLE', 'A native certificate-pinning transport is required');
    }
    this.paired = paired;
    this.transport = transport;
  }

  /** @param {string} method @param {string} path @param {unknown} payload @param {{allowPending?:boolean}} options @returns {Promise<PinnedResponse>} */
  async send(method, path, payload = undefined, options = {}) {
    if (!this.paired.approved && !(options.allowPending && method === 'GET' && path === '/api/status')) {
      throw new GatewayError('FORBIDDEN', 'Desktop must approve this phone before controls work');
    }
    if (method !== 'GET' && method !== 'POST') throw new GatewayError('FORBIDDEN', 'Method disallowed');
    const body = jsonBody(payload);
    if (body.length > 65536) throw new GatewayError('BODY_TOO_LARGE', 'Request exceeded 64 KiB');
    const request = buildSignedRequest(this.paired, method, path, body);
    let response;
    try { response = await this.transport.sendPinned(request); }
    catch (_) { throw new GatewayError('UNAVAILABLE', 'Paired laptop unavailable or TLS pin mismatch'); }
    if (response.status < 200 || response.status >= 300) {
      throw new GatewayError(classify(response.status), `Laptop rejected request (HTTP ${response.status})`);
    }
    return response;
  }

  /** @returns {Promise<object>} */
  async status() { return this.json('GET', '/api/status'); }

  /** @param {boolean} armed @returns {Promise<object>} */
  async arm(armed) { return this.json('POST', '/api/config/armed', { armed }); }

  /** @param {'lock'|'alarm'|'photo'|'recordStart'|'recordStop'} operation @returns {Promise<object>} */
  async action(operation) {
    const paths = {
      lock: '/api/lock', alarm: '/api/alarm', photo: '/api/camera/photo',
      recordStart: '/api/camera/record/start', recordStop: '/api/camera/record/stop',
    };
    if (!paths[operation]) throw new GatewayError('FORBIDDEN', 'Invalid action');
    // Never retry non-idempotent actions, including lock and alarm.
    return this.json('POST', paths[operation]);
  }

  /** @param {string} message @returns {Promise<object>} */
  async warn(message) {
    const text = typeof message === 'string' ? message.trim() : '';
    if (text.length < 1 || text.length > 400 || /[\x00-\x1f]/.test(text)) {
      throw new GatewayError('INVALID_REQUEST', 'Warning must contain 1–400 printable characters');
    }
    return this.json('POST', '/api/warning/tts', { message: text });
  }

  /** @param {{auto_snapshot:boolean,auto_record:boolean,auto_alarm:boolean}} options @returns {Promise<object>} */
  async configurePersonMonitor(options) {
    if (!options || ['auto_snapshot','auto_record','auto_alarm'].some((field) =>
      typeof options[field] !== 'boolean')) {
      throw new GatewayError('INVALID_REQUEST', 'Person monitor requires three explicit boolean settings');
    }
    return this.json('POST', '/api/config/person-monitor', {
      auto_snapshot: options.auto_snapshot, auto_record: options.auto_record,
      auto_alarm: options.auto_alarm,
    });
  }

  /** @returns {Promise<object>} */
  async clearIncidents() { return this.json('POST', '/api/incidents/clear'); }

  /** @returns {Promise<object>} */
  async refreshLocation() { return this.json('POST', '/api/location/refresh'); }

  /** @returns {Promise<object>} */
  async evidenceIndex() { return this.json('GET', '/api/evidence/list'); }

  /** @returns {Promise<object>} */
  async lastSeen() { return this.json('GET', '/api/location/last-seen'); }

  /** @param {number} afterId @returns {Promise<object>} */
  async incidents(afterId = 0) {
    return this.json('POST', '/api/incidents/query', { after_id: afterId, limit: 100 });
  }

  /** @param {string} method @param {string} path @param {unknown} payload @param {{allowPending?:boolean}} options @returns {Promise<object>} */
  async json(method, path, payload = undefined, options = {}) {
    const response = await this.send(method, path, payload, options);
    if (!(response.body instanceof Uint8Array) || response.body.length > 1048576) {
      throw new GatewayError('INVALID_RESPONSE', 'Gateway response exceeded JSON size limit');
    }
    let parsed;
    try { parsed = JSON.parse(bytesToUtf8(response.body)); }
    catch (_) { throw new GatewayError('INVALID_RESPONSE', 'Invalid gateway JSON response'); }
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new GatewayError('INVALID_RESPONSE', 'Invalid gateway response shape');
    }
    return parsed;
  }
}
