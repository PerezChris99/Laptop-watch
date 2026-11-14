// @ts-check
/** Native SQLite cache: cache ONLY. Keystore owns the Ed25519 seed and no
 * SQLite table may contain an offline lock/alarm/photo/record command.
 */
import { open } from '@op-engineering/op-sqlite';
import { LATEST_SCHEMA_VERSION, statementsFor } from './schema.js';

let connection = null;

/** @returns {Promise<object>} */
export async function database() {
  if (connection) return connection;
  const db = open({ name: 'laptopwatch-cache.sqlite' });
  try {
    await db.execute('PRAGMA foreign_keys=ON');
    const result = await db.execute('PRAGMA user_version');
    const version = Number(result.rows[0]?.user_version);
    if (!Number.isSafeInteger(version)) throw new Error('Cannot read cache schema');
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
    throw error;
  }
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

const PREFERENCE_KEYS = new Set(['owner_consent_v1', 'background_opt_in', 'mic_listen_opt_in']);

/** @param {string} key @returns {Promise<string|null>} */
export async function getPreference(key) {
  if (!PREFERENCE_KEYS.has(key)) throw new Error('Unsupported preference');
  const db = await database();
  const result = await db.execute('SELECT value FROM settings WHERE key = ? LIMIT 1', [key]);
  return result.rows[0]?.value ?? null;
}

/** @param {string} key @param {string} value @returns {Promise<void>} */
export async function setPreference(key, value) {
  if (!PREFERENCE_KEYS.has(key) || !['0', '1'].includes(value)) {
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

/** @returns {Promise<void>} */
export async function closeDatabase() {
  if (connection) {
    connection.close();
    connection = null;
  }
}
