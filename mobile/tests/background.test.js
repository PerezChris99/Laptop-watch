import test from 'node:test';
import assert from 'node:assert/strict';
import { foregroundEligibility } from '../src/background/policy.js';
import { canEnablePush, PUSH_ENABLED } from '../src/notifications/push.js';

const all = { ownerOptedIn: true, pairedApproved: true, armed: true, notificationsGranted: true };

test('foreground connection fails closed without any one prerequisite', () => {
  assert.equal(foregroundEligibility(all).allowed, true);
  for (const key of Object.keys(all)) {
    assert.equal(foregroundEligibility({ ...all, [key]: false }).allowed, false);
  }
});
test('no hidden FCM enrollment without owner config and feature flag', () => {
  assert.equal(PUSH_ENABLED, false);
  assert.equal(canEnablePush({ ownerOptedIn: true, relayConfigured: true, firebaseConfigured: true }), false);
});
