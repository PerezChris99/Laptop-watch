// @ts-check
/** Offline source contract tests. NOT compiled-native or device parity tests. */
import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const ROOT = fileURLToPath(new URL('../../', import.meta.url));
const exists = (part) => existsSync(resolve(ROOT, part));
const source = (part) => readFileSync(resolve(ROOT, part), 'utf8');

test('owner-authorized legacy Kotlin build is absent, not duplicated', () => {
  for (const path of [
    'app', 'mobile/android-native', 'settings.gradle.kts',
    'build.gradle.kts', 'gradle.properties', 'gradle', 'metadata.json',
  ]) {
    assert.equal(exists(path), false, `Retired build path returned: ${path}`);
  }
});

test('mobile/android/app and mobile/ios/ are canonical native projects', () => {
  const android = source('mobile/android/app/src/main/java/com/kc/laptopwatch/mobile/MainApplication.kt');
  const xcode = source('mobile/ios/LaptopWatchMobile.xcodeproj/project.pbxproj');
  const bridge = source('mobile/ios/LaptopWatchPinnedTransport/Sources/LaptopWatchPinnedTransportBridge.m');
  assert.match(android, /LaptopWatchPinnedPackage\(\)/);
  assert.match(android, /LaptopWatchEvidenceManagementPackage\(\)/);
  assert.match(android, /LaptopWatchRelayPackage\(\)/);
  assert.match(xcode, /com\.kc\.laptopwatch\.mobile/);
  assert.doesNotMatch(xcode, /org\.reactjs\.native\.example/);
  assert.match(bridge, /sendPinned:/);
  assert.match(bridge, /deleteAllLocal:/);
});

test('native responses, local media and relay remain protected in source', () => {
  const gateway = source('mobile/src/net/pinnedTransport.js');
  const evidence = source('mobile/src/net/evidence.js');
  const auth = source('desktop/src-python/laptopwatch/owner_health.py');
  const rust = source('desktop/src-tauri/src/main.rs');
  assert.match(gateway, /Certificate-pinned native transport not installed/);
  assert.match(gateway, /followRedirects !== false/);
  assert.match(evidence, /purgePrivateEvidence/);
  assert.match(auth, /X-LaptopWatch-Health-Proof/);
  assert.match(rust, /owner_health_hmac/);
});

test('owner Firebase configuration is never committed by normal Git staging', () => {
  const ignore = source('mobile/.gitignore');
  const gradle = source('mobile/android/app/build.gradle');
  assert.match(ignore, /\*\*\/google-services\.json/);
  assert.match(ignore, /\*\*\/GoogleService-Info\.plist/);
  assert.match(gradle, /laptopwatchFcmEnabled/);
  assert.match(gradle, /google-services\.json/);
});

test('new mobile protocol is JavaScript-only and rejects unsigned network fallback', () => {
  const sourceApp = source('mobile/src/App.jsx');
  const transports = source('mobile/src/net/SignedClient.js');
  assert.match(sourceApp, /QrScanner/);
  assert.match(sourceApp, /await purgePrivateEvidence\(\)/);
  assert.match(transports, /canonicalRequest/);
  assert.match(transports, /No valid certificate-pinned LAN pairing/);
});
