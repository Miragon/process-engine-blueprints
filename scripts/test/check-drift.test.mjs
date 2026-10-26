import assert from 'node:assert/strict';
import { test } from 'node:test';
import { violations } from '../check-drift.mjs';

const rules = [{ name: 'scenarios', examples: ['a', 'b', 'c'], files: ['bruno/01.bru'] }];

test('holds while every copy is identical', () => {
  assert.deepEqual(violations(rules, () => 'same'), []);
});

test('reports a copy that drifted', () => {
  const hashOf = (example) => (example === 'c' ? 'changed' : 'same');
  assert.deepEqual(violations(rules, hashOf), ['scenarios: bruno/01.bru differs between a, b, c']);
});

test('reports a copy that was deleted', () => {
  const hashOf = (example) => (example === 'b' ? null : 'same');
  assert.deepEqual(violations(rules, hashOf), ['scenarios: bruno/01.bru is missing in b']);
});
