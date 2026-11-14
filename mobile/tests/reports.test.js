import test from 'node:test';
import assert from 'node:assert/strict';
import { incidentsCsv, incidentsText } from '../src/reports/incidentReport.js';

test('empty exports show explicit report headers but no invented activity', () => {
  assert.equal(incidentsCsv([]).split('\r\n').filter(Boolean).length, 1);
  assert.match(incidentsText([]), /not an externally timestamped/);
});

test('CSV formula input is neutralized, only valid signed metadata included', () => {
  const event = {
    id: 42, observed_at_ms: 1700000000000, confidence: 0.88,
    kind: '=HYPERLINK("bad")', evidence_id: '1234',
  };
  const content = incidentsCsv([event]);
  assert.ok(content.includes("'=HYPERLINK"));
  assert.ok(content.includes('"0.880"'));
  assert.equal(content.includes('1234'), false);
});

test('bounded reports, no private material fields', () => {
  assert.throws(() => incidentsCsv(Array(101).fill({})));
  const text = incidentsText([{ id: 5, observed_at_ms: 1700000000000, confidence: 0.9, private_key: 'never-in-report' }]);
  assert.equal(text.includes('never-in-report'), false);
});
