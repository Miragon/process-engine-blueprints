import assert from 'node:assert/strict';
import { test } from 'node:test';
import { selectExamples } from '../changed-examples.mjs';

const examples = ['cibseven-embedded', 'cibseven-remote', 'zeebe-native'];

test('selects only the examples whose files changed', () => {
  assert.deepEqual(selectExamples(examples, ['examples/cibseven-remote/README.md', 'examples/zeebe-native/bruno/x.bru']), ['cibseven-remote', 'zeebe-native']);
});

test('does not mistake an example for another one with the same prefix', () => {
  assert.deepEqual(selectExamples(examples, ['examples/cibseven-embedded-legacy/README.md']), []);
});

test('selects every example when shared tooling changed', () => {
  assert.deepEqual(selectExamples(examples, ['tooling/starter/export.sh']), examples);
  assert.deepEqual(selectExamples(examples, ['.github/workflows/_blueprint.yml']), examples);
});

test('selects none for documentation of the repository itself', () => {
  assert.deepEqual(selectExamples(examples, ['README.md', 'docs/choose.md', 'docs/adr/0001-x.md']), []);
});
