// @ts-check
/** Local report of signed incident metadata only; no photos, tokens, keys,
 * location history, person names or biometric "trust" classifications.
 */
const MAX_EVENTS = 100;
/** @param {unknown} value @returns {string} */
function safeCell(value) {
  const clean = String(value ?? '').replace(/[\u0000-\u001f\u007f]/g, ' ');
  // Prevent spreadsheet formula execution on opening exported CSV.
  return /^[=+@-]/.test(clean) ? "'" + clean : clean;
}
/** @param {unknown} value @returns {string} */
function csv(value) { return '"' + safeCell(value).replace(/"/g, '""') + '"'; }

/** @param {object[]} alerts @returns {string} */
export function incidentsCsv(alerts) {
  if (!Array.isArray(alerts) || alerts.length > MAX_EVENTS) throw new Error('Invalid incident report');
  const lines = ['"Event ID","Timestamp UTC","Kind","Confidence","Evidence ID"'];
  for (const item of alerts) {
    if (!Number.isSafeInteger(item.id) || item.id < 1 ||
        !Number.isSafeInteger(item.observed_at_ms) ||
        !Number.isFinite(item.confidence)) continue;
    const stamp = new Date(item.observed_at_ms);
    if (Number.isNaN(stamp.getTime())) continue;
    lines.push([
      csv(item.id), csv(stamp.toISOString()),
      csv(item.kind || 'person_verified'), csv(item.confidence.toFixed(3)),
      csv(/^[a-f0-9]{32}$/.test(item.evidence_id || '') ? item.evidence_id : ''),
    ].join(','));
  }
  return lines.join('\r\n') + '\r\n';
}

/** @param {object[]} alerts @returns {string} */
export function incidentsText(alerts) {
  if (!Array.isArray(alerts) || alerts.length > MAX_EVENTS) throw new Error('Invalid incident report');
  const lines = ['LaptopWatch - locally exported authenticated incident metadata',
    'This report is not an externally timestamped forensic chain of custody.',
    'No photographs, audio, location coordinates or private keys are included.', ''];
  for (const item of alerts) {
    if (!Number.isSafeInteger(item.id) || !Number.isSafeInteger(item.observed_at_ms) ||
        !Number.isFinite(item.confidence)) continue;
    const stamp = new Date(item.observed_at_ms);
    if (Number.isNaN(stamp.getTime())) continue;
    lines.push(`Event #${item.id}: ${stamp.toISOString()} • confidence ${item.confidence.toFixed(3)}`);
  }
  return lines.join('\n') + '\n';
}
