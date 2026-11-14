import test from 'node:test';
import assert from 'node:assert/strict';
import { locationStatus } from '../src/location/labels.js';

const FIX = { latitude: 0.32, longitude: 32.6, accuracy_meters: 9, observed_at_ms: 200_000 };
test('cached coordinate never becomes LIVE', () => {
  assert.equal(locationStatus(FIX, 201_000, false).label, 'LAST SEEN');
});
test('only freshly fetched 30-second valid GNSS fix is LIVE', () => {
  assert.equal(locationStatus(FIX, 202_000, true).label, 'LIVE');
  assert.equal(locationStatus(FIX, 240_000, true).label, 'LAST SEEN');
});
test('malformed or invented coordinate is never represented as a fix', () => {
  assert.equal(locationStatus({}, 200_000, true).label, 'NO VERIFIED FIX');
  assert.equal(locationStatus({ ...FIX, latitude: 999 }, 200_000, true).live, false);
});
