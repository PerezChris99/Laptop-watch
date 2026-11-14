// @ts-check
/** Native SQLite cache: cache ONLY. Keystore owns the Ed25519 seed and no
 * SQLite table may contain an offline lock/alarm/photo/record command.
 */
import { open } from '@op-engineering/op-sqlite';
import { LATEST_SCHEMA_VERSION, statementsFor } from './schema.js';
import { cacheEncryptionKey } from './encryption.js';

let connection = null;
let opening = null;

/** Single-flight encrypted connection. Never open a plaintext cache or
 * silently delete an unrecognized legacy SQLite file. The key is stored only
 * in OS Keychain/Keystore, and is NOT the pairing signing seed.
 * @returns {Promise<object>}
 */
export async function database() {
  if (connection) return connection;
  if (opening) return opening;
  opening = (async () => {
    const encryptionKey = await cacheEncryptionKey();
    // A distinct name prevents silently reading an earlier plaintext cache.
    const db = open({ name: 'laptopwatch-private-v2.sqlite', encryptionKey });
    try {
      await db.execute('PRAGMA foreign_keys=ON');
      const result = await db.execute('PRAGMA user_version');
      const version = Number(result.rows[0]?.user_version);
      if (!Number.isSafeInteger(version)) throw new Error('Cannot read private cache schema');
      const statements = statementsFor(version);
      if (statements.length) {
        await db.transaction(async (tx) => {
          for (const statement of statements) await tx.execute(statement);
          await tx.execute(`PRAGMA user_version = ${LATEST_SCHEMA_VERSION}`);
        });
      }
      connection = db;
      return db;
    } catch (error) {
      db.close();
      // No fallback to an unencrypted connection. The app must fail closed.
      throw error;
    }
  })();
  try { return await opening; }
  finally { opening = null; }
}

/** @param {object} item @returns {Promise<void>} */
export async function cacheEvidence(item) {
  if (!/^[a-f0-9]{32}$/.test(item.id) ||
      !['photo','video'].includes(item.kind) ||
      !Number.isSafeInteger(item.size_bytes) || item.size_bytes <= 0 ||
      !Number.isSafeInteger(item.modified_at_ms)) throw new Error('Invalid evidence item');
  const db = await database();
  await db.execute(
    'INSERT INTO evidence_index(kind,id,size_bytes,modified_at_ms,local_path) VALUES(?,?,?,?,NULL) ON CONFLICT(kind,id) DO UPDATE SET size_bytes=excluded.size_bytes, modified_at_ms=excluded.modified_at_ms',
    [item.kind, item.id, item.size_bytes, item.modified_at_ms],
  );
}

/** @param {string} desktopId @param {object} fix @returns {Promise<void>} */
export async function cacheLastSeen(desktopId, fix) {
  if (!/^[0-9a-f-]{36}$/i.test(desktopId) ||
      !Number.isFinite(fix.latitude) || !Number.isFinite(fix.longitude) ||
      !Number.isFinite(fix.accuracy_meters) || !Number.isSafeInteger(fix.observed_at_ms) ||
      fix.latitude < -90 || fix.latitude > 90 || fix.longitude < -180 || fix.longitude > 180 ||
      fix.accuracy_meters <= 0 || typeof fix.provider !== 'string' || fix.provider.length > 36) {
    throw new Error('Invalid signed GNSS fix');
  }
  const db = await database();
  await db.execute(
    'INSERT INTO location_cache(desktop_id,latitude,longitude,accuracy_meters,observed_at_ms,provider) VALUES(?,?,?,?,?,?) ON CONFLICT(desktop_id) DO UPDATE SET latitude=excluded.latitude, longitude=excluded.longitude, accuracy_meters=excluded.accuracy_meters, observed_at_ms=excluded.observed_at_ms, provider=excluded.provider WHERE excluded.observed_at_ms >= location_cache.observed_at_ms',
    [desktopId, fix.latitude, fix.longitude, fix.accuracy_meters, fix.observed_at_ms, fix.provider],
  );
}

/** @param {string} desktopId @returns {Promise<object|null>} */
export async function loadLastSeen(desktopId) {
  const db = await database();
  const result = await db.execute(
    'SELECT latitude,longitude,accuracy_meters,observed_at_ms,provider FROM location_cache WHERE desktop_id=? LIMIT 1',
    [desktopId],
  );
  return result.rows[0] || null;
}

const BOOLEAN_PREFERENCES = new Set([
  'owner_consent_v1', 'background_opt_in', 'mic_listen_opt_in', 'push_opt_in',
  'person_auto_snapshot', 'person_auto_record', 'person_auto_alarm',
]);
const PREFERENCE_KEYS = new Set([...BOOLEAN_PREFERENCES, 'relay_https_url']);

/** @param {string} key @returns {Promise<string|null>} */
export async function getPreference(key) {
  if (!PREFERENCE_KEYS.has(key)) throw new Error('Unsupported preference');
  const db = await database();
  const result = await db.execute('SELECT value FROM settings WHERE key = ? LIMIT 1', [key]);
  return result.rows[0]?.value ?? null;
}

/** @param {string} key @param {string} value @returns {Promise<void>} */
export async function setPreference(key, value) {
  if (!PREFERENCE_KEYS.has(key) || typeof value !== 'string' ||
      (BOOLEAN_PREFERENCES.has(key) && !['0','1'].includes(value)) ||
      (key === 'relay_https_url' && value.length > 256) ||
      (key === 'relay_https_url' && value !== '' &&
       !/^https:\/\/[a-z0-9][a-z0-9.-]{3,218}[a-z0-9]$/i.test(value))) {
    throw new Error('Unsupported preference value');
  }
  const db = await database();
  await db.execute(
    'INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value = excluded.value',
    [key, value],
  );
}

/** @param {string} desktopId @returns {Promise<void>} */
export async function deleteDesktopCache(desktopId) {
  const db = await database();
  await db.transaction(async (tx) => {
    await tx.execute('DELETE FROM pairings WHERE desktop_id = ?', [desktopId]);
    await tx.execute('DELETE FROM location_cache WHERE desktop_id = ?', [desktopId]);
    await tx.execute('DELETE FROM alerts WHERE desktop_id = ?', [desktopId]);
    // Evidence entries have no desktop_id in v1. Clear on local unpair to
    // prevent accidental cross-owner evidence inventory mixing.
    await tx.execute('DELETE FROM evidence_index');
  });
}

/** @param {string} kind @param {string} id @param {string} uri @returns {Promise<void>} */
export async function recordPrivateEvidence(kind, id, uri) {
  if (!['photo','video'].includes(kind) || !/^[a-f0-9]{32}$/.test(id) ||
      typeof uri !== 'string' || !uri.startsWith('file://') || uri.length > 1024) {
    throw new Error('Invalid private evidence identifier');
  }
  const db = await database();
  await db.execute('UPDATE evidence_index SET local_path = ? WHERE kind = ? AND id = ?',
    [uri, kind, id]);
}

/** @param {string} kind @param {string} id @returns {Promise<void>} */
export async function forgetPrivateEvidence(kind, id) {
  if (!['photo','video'].includes(kind) || !/^[a-f0-9]{32}$/.test(id)) {
    throw new Error('Invalid private evidence identifier');
  }
  const db = await database();
  await db.execute('UPDATE evidence_index SET local_path = NULL WHERE kind = ? AND id = ?', [kind,id]);
}

/** @param {string} desktopId @param {object[]} items @returns {Promise<void>} */
export async function cacheAlerts(desktopId, items) {
  if (!/^[a-f0-9-]{36}$/i.test(desktopId) || !Array.isArray(items) ||
      items.length > 100) throw new Error('Invalid alert batch');
  const db = await database();
  await db.transaction(async (tx) => {
    for (const item of items) {
      if (!Number.isSafeInteger(item.id) || item.id < 1 ||
          !Number.isSafeInteger(item.observed_at_ms) ||
          !Number.isFinite(item.confidence) ||
          item.confidence < 0 || item.confidence > 1) continue;
      await tx.execute(
        'INSERT OR IGNORE INTO alerts(desktop_id,event_id,observed_at_ms,kind,confidence) VALUES(?,?,?,?,?)',
        [desktopId, item.id, item.observed_at_ms, 'person_verified', item.confidence],
      );
    }
    await tx.execute(
      'DELETE FROM alerts WHERE desktop_id=? AND event_id NOT IN (SELECT event_id FROM alerts WHERE desktop_id=? ORDER BY event_id DESC LIMIT 100)',
      [desktopId,desktopId],
    );
  });
}

/** @param {string} desktopId @returns {Promise<object[]>} */
export async function loadAlerts(desktopId) {
  const db = await database();
  const result = await db.execute(
    'SELECT event_id AS id,observed_at_ms,kind,confidence FROM alerts WHERE desktop_id=? ORDER BY event_id DESC LIMIT 100',
    [desktopId],
  );
  return [...result.rows].reverse();
}

/** @returns {Promise<void>} */
export async function closeDatabase() {
  if (connection) {
    connection.close();
    connection = null;
  }
}
