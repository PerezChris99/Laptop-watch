import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { webcrypto } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import {
  b64url, unb64url, canonicalRequest, publicKey, sign, verify,
  parsePairingQr, phoneProofTranscript, isPrivateIPv4,
} from '../src/crypto/protocol.js';
import { buildSignedRequest, GatewayError, SignedClient } from '../src/net/SignedClient.js';

globalThis.crypto ??= webcrypto;
const file = resolve(dirname(fileURLToPath(import.meta.url)), '../../shared/protocol-vectors/ed25519-v1.json');
const fixtures = JSON.parse(fs.readFileSync(file, 'utf8'));
const seed = Uint8Array.from(Buffer.from(fixtures.test_seed_hex, 'hex'));
const pub = unb64url(fixtures.public_key_b64url, 32);

test('deterministic Python and JS public keys match', () => {
  assert.equal(b64url(publicKey(seed)), fixtures.public_key_b64url);
});

for (const v of fixtures.signed_requests) {
  test(`LW1 exact signed vector: ${v.method} ${v.path}`, () => {
    const raw = Uint8Array.from(Buffer.from(v.body_utf8, 'utf8'));
    const canonical = canonicalRequest(v.method, v.path, raw, v.timestamp, v.nonce, v.device_id);
    assert.equal(Buffer.from(canonical).toString('ascii'), v.canonical_utf8);
    const signature = sign(seed, canonical);
    assert.equal(b64url(signature), v.signature_b64url);
    assert.ok(verify(pub, canonical, signature));
    assert.equal(verify(pub, Uint8Array.from([...canonical, 0x58]), signature), false);
  });
}

test('signed QR and phone possession proof match Python', () => {
  const qr = fixtures.pairing_qr;
  const url = new URL('laptopwatch://pair?' + new URLSearchParams({
    v: '1', id: qr.laptop_id, host: qr.host, port: String(qr.port),
    pk: qr.public_key_b64url, fp: qr.tls_fingerprint_hex,
    nonce: qr.challenge, exp: String(qr.expires), sig: qr.signature_b64url,
  }));
  const parsed = parsePairingQr(url.toString(), qr.expires - 45);
  assert.equal(parsed.tlsFingerprint, qr.tls_fingerprint_hex);
  const proof = fixtures.pairing_proof;
  assert.equal(Buffer.from(phoneProofTranscript(proof.device_id, proof.phone_public_key_b64url, proof.challenge)).toString('ascii'), proof.canonical_utf8);
  assert.equal(b64url(sign(seed, phoneProofTranscript(proof.device_id, proof.phone_public_key_b64url, proof.challenge))), proof.signature_b64url);
  url.searchParams.set('fp', '0'.repeat(64));
  assert.throws(() => parsePairingQr(url.toString(), qr.expires - 45), /signature/);
});

test('strict transport policy: bad paths, non-private hosts and missing native pin are refused', () => {
  assert.equal(isPrivateIPv4('8.8.8.8'), false);
  assert.equal(isPrivateIPv4('192.168.1.3'), true);
  const paired = {
    deviceId: fixtures.signed_requests[0].device_id, privateSeed: seed,
    host: '192.168.1.3', port: 8765, tlsFingerprint: fixtures.pairing_qr.tls_fingerprint_hex, approved: true,
  };
  assert.throws(() => new SignedClient(paired, null), GatewayError);
  assert.throws(() => buildSignedRequest({ ...paired, host: '8.8.8.8' }, 'GET', '/api/status', new Uint8Array()), GatewayError);
  assert.throws(() => buildSignedRequest(paired, 'POST', '/api/pin/unlock', new Uint8Array()), GatewayError);
  const request = buildSignedRequest(paired, 'GET', '/api/status', new Uint8Array());
  assert.equal(request.path, '/api/status');
  assert.ok(request.headers['X-LaptopWatch-Signature']);
  assert.equal(request.followRedirects, false);
});
