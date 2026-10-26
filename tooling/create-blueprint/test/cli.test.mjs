import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { main } from '../src/cli.mjs';

const checkout = new URL('../../../', import.meta.url).pathname;

function newProjectDirectory() {
  return join(mkdtempSync(join(tmpdir(), 'project-')), 'leasing-service');
}

test('creates a renamed single-variant project with its own repository', async () => {
  const target = newProjectDirectory();
  await main([target, '--example', 'operaton-process-engine-api', '--lang', 'java', '--package', 'com.acme.leasing', '--from-checkout', checkout, '--yes']);

  const pom = readFileSync(join(target, 'pom.xml'), 'utf8');
  assert.match(pom, /<groupId>com\.acme\.leasing<\/groupId>/);
  assert.match(pom, /<artifactId>leasing-service<\/artifactId>/);
  assert.equal(existsSync(join(target, 'service/app/src/main/java/com/acme/leasing')), true);
  assert.equal(existsSync(join(target, 'service/app/src/main/java/io')), false);
  assert.equal(existsSync(join(target, 'blueprint.yaml')), false);
  assert.equal(execFileSync('git', ['rev-list', '--count', 'HEAD'], { cwd: target, encoding: 'utf8' }).trim(), '1');
});

test('refuses a language the example does not have and leaves nothing behind', async () => {
  const target = newProjectDirectory();
  await assert.rejects(main([target, '--example', 'fullstack-cibseven-embedded', '--lang', 'java', '--from-checkout', checkout, '--yes']), /has no java-maven variant/);
  assert.equal(existsSync(target), false);
});
