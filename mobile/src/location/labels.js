// @ts-check
/** Honest GNSS status — last-seen cache is NEVER represented as a live fix. */

/** @param {object|null} fix @param {number} nowMs @param {boolean} freshRead */
export function locationStatus(fix, nowMs = Date.now(), freshRead = false) {
  if (!fix || !Number.isFinite(fix.latitude) ||
      !Number.isFinite(fix.longitude) || !Number.isFinite(fix.observed_at_ms) ||
      !Number.isFinite(fix.accuracy_meters) ||
      fix.latitude < -90 || fix.latitude > 90 ||
      fix.longitude < -180 || fix.longitude > 180 ||
      fix.accuracy_meters <= 0) {
    return { label: 'NO VERIFIED FIX', live: false };
  }
  const age = nowMs - fix.observed_at_ms;
  const live = Boolean(freshRead && Number.isFinite(age) && age >= 0 && age <= 30_000);
  return { label: live ? 'LIVE' : 'LAST SEEN', live, ageMs: age };
}
