import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseOptions, validationErrors } from '../src/options.mjs';

test('takes the directory as positional and the rest as flags', () => {
  const options = parseOptions(['my-service', '--example', 'cibseven-remote', '--lang', 'java', '--from-checkout', '.']);
  assert.equal(options.directory, 'my-service');
  assert.equal(options.example, 'cibseven-remote');
  assert.equal(options.lang, 'java');
  assert.equal(options.fromCheckout, '.');
});

test('accepts valid names and rejects invalid ones', () => {
  assert.deepEqual(validationErrors({ lang: 'kotlin', name: 'leasing-service', package: 'com.acme.leasing' }), []);
  assert.equal(validationErrors({ lang: 'scala', name: 'Leasing Service', package: 'Com.Acme' }).length, 3);
});
