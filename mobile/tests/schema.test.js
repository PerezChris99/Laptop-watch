import test from 'node:test';
import assert from 'node:assert/strict';
import { MIGRATION_V1, LATEST_SCHEMA_VERSION, statementsFor } from '../src/storage/schema.js';

test('offline command outbox is forbidden by schema', () => {
  const sql = MIGRATION_V1.join('\n').toLowerCase();
  assert.ok(sql.includes('evidence_index') && sql.includes('location_cache') && sql.includes('alerts'));
  assert.equal(sql.includes('command_outbox'), false);
  assert.equal(sql.includes('private_seed'), false);
  assert.equal(sql.includes('phone_private'), false);
});

test('migration is versioned and unknown schema fails closed', () => {
  assert.equal(LATEST_SCHEMA_VERSION, 1);
  assert.equal(statementsFor(0), MIGRATION_V1);
  assert.deepEqual(statementsFor(1), []);
  assert.throws(() => statementsFor(2));
  assert.throws(() => statementsFor(-1));
});
