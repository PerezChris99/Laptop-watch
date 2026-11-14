// @ts-check
/** Version 1 app-private cache schema. Pairing seeds are NEVER placed here.
 * The file is unencrypted SQLite until explicitly configured with SQLCipher.
 */
export const LATEST_SCHEMA_VERSION = 1;

/** @type {readonly string[]} */
export const MIGRATION_V1 = Object.freeze([
  'CREATE TABLE IF NOT EXISTS pairings (device_id TEXT PRIMARY KEY, desktop_id TEXT NOT NULL, certificate_sha256 TEXT NOT NULL, approved INTEGER NOT NULL CHECK(approved IN (0,1)), added_at_ms INTEGER NOT NULL)',
  'CREATE TABLE IF NOT EXISTS evidence_index (id TEXT NOT NULL, kind TEXT NOT NULL CHECK(kind IN (\'photo\',\'video\')), size_bytes INTEGER NOT NULL CHECK(size_bytes>0), modified_at_ms INTEGER NOT NULL, local_path TEXT, PRIMARY KEY(kind,id))',
  'CREATE INDEX IF NOT EXISTS idx_evidence_date ON evidence_index(modified_at_ms DESC)',
  'CREATE TABLE IF NOT EXISTS alerts (desktop_id TEXT NOT NULL, event_id INTEGER NOT NULL, observed_at_ms INTEGER NOT NULL, kind TEXT NOT NULL, confidence REAL NOT NULL, PRIMARY KEY(desktop_id,event_id))',
  'CREATE INDEX IF NOT EXISTS idx_alerts_date ON alerts(observed_at_ms DESC)',
  'CREATE TABLE IF NOT EXISTS location_cache (desktop_id TEXT PRIMARY KEY, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracy_meters REAL NOT NULL, observed_at_ms INTEGER NOT NULL, provider TEXT NOT NULL)',
  'CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)',
]);

/** @param {number} currentVersion @returns {readonly string[]} */
export function statementsFor(currentVersion) {
  if (!Number.isSafeInteger(currentVersion) || currentVersion < 0 ||
      currentVersion > LATEST_SCHEMA_VERSION) throw new Error('Unsupported SQLite schema version');
  return currentVersion === 0 ? MIGRATION_V1 : [];
}
