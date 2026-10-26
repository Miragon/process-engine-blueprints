import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { main } from '../src/cli.mjs';

function starterArchive() {
  const starter = mkdtempSync(join(tmpdir(), 'starter-'));
  mkdirSync(join(starter, 'src/main/java/io/miragon/blueprint'), { recursive: true });
  writeFileSync(join(starter, 'pom.xml'), '<project><groupId>io.miragon.blueprint</groupId><artifactId>cibseven-remote-example</artifactId></project>');
  writeFileSync(join(starter, 'src/main/java/io/miragon/blueprint/App.java'), 'package io.miragon.blueprint;\n');
  const archive = join(mkdtempSync(join(tmpdir(), 'archive-')), 'cibseven-remote-java-maven.tar.gz');
  execFileSync('tar', ['-czf', archive, '-C', starter, '.']);
  return archive;
}

test('creates a renamed project with its own repository from a starter archive', async () => {
  const target = join(mkdtempSync(join(tmpdir(), 'project-')), 'leasing-service');
  await main([target, '--example', 'cibseven-remote', '--lang', 'java', '--package', 'com.acme.leasing', '--from', starterArchive(), '--yes']);

  assert.equal(readFileSync(join(target, 'src/main/java/com/acme/leasing/App.java'), 'utf8'), 'package com.acme.leasing;\n');
  assert.match(readFileSync(join(target, 'pom.xml'), 'utf8'), /<groupId>com\.acme\.leasing<\/groupId><artifactId>leasing-service<\/artifactId>/);
  assert.equal(existsSync(join(target, '.git')), true);
});

test('refuses a language the example does not have and leaves nothing behind', async () => {
  const target = join(mkdtempSync(join(tmpdir(), 'project-')), 'leasing-service');
  await assert.rejects(main([target, '--example', 'fullstack-cibseven-embedded', '--lang', 'java', '--from', starterArchive(), '--yes']), /has no java-maven variant/);
  assert.equal(existsSync(target), false);
});
