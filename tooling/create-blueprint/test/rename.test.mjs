import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { projectNameOf, renameProject } from '../src/rename.mjs';

function gradleProject() {
  const root = mkdtempSync(join(tmpdir(), 'rename-'));
  const sources = join(root, 'service/app/src/main/kotlin/io/miragon/blueprint/domain');
  mkdirSync(sources, { recursive: true });
  writeFileSync(join(root, 'settings.gradle.kts'), 'rootProject.name = "cibseven-embedded-example"\n');
  writeFileSync(join(root, 'build.gradle.kts'), 'group = "io.miragon.blueprint"\n');
  writeFileSync(join(sources, 'Leasing.kt'), 'package io.miragon.blueprint.domain\n');
  writeFileSync(join(root, 'README.md'), 'see service/app/src/main/kotlin/io/miragon/blueprint and miravelo/cibseven-embedded-example\n');
  writeFileSync(join(root, 'logo.png'), Buffer.from([0x89, 0x50, 0x00, 0x69, 0x6f, 0x2e, 0x6d]));
  return root;
}

test('reads the project name from Gradle and from Maven without the parent', () => {
  const maven = mkdtempSync(join(tmpdir(), 'maven-'));
  writeFileSync(join(maven, 'pom.xml'), '<project><parent><artifactId>spring-boot-starter-parent</artifactId></parent><artifactId>zeebe-example</artifactId></project>');
  assert.equal(projectNameOf(gradleProject()), 'cibseven-embedded-example');
  assert.equal(projectNameOf(maven), 'zeebe-example');
});

test('moves the package, rewrites its references and renames the project', () => {
  const root = gradleProject();
  renameProject(root, { fromPackage: 'io.miragon.blueprint', toPackage: 'com.acme.leasing', toName: 'leasing-service' });

  const moved = join(root, 'service/app/src/main/kotlin/com/acme/leasing/domain/Leasing.kt');
  assert.equal(readFileSync(moved, 'utf8'), 'package com.acme.leasing.domain\n');
  assert.equal(existsSync(join(root, 'service/app/src/main/kotlin/io')), false);
  assert.equal(readFileSync(join(root, 'build.gradle.kts'), 'utf8'), 'group = "com.acme.leasing"\n');
  assert.equal(readFileSync(join(root, 'settings.gradle.kts'), 'utf8'), 'rootProject.name = "leasing-service"\n');
  assert.equal(readFileSync(join(root, 'README.md'), 'utf8'), 'see service/app/src/main/kotlin/com/acme/leasing and miravelo/leasing-service\n');
});

test('leaves binary files untouched', () => {
  const root = gradleProject();
  const before = readFileSync(join(root, 'logo.png'));
  renameProject(root, { fromPackage: 'io.miragon.blueprint', toPackage: 'com.acme.leasing', toName: 'leasing-service' });
  assert.deepEqual(readFileSync(join(root, 'logo.png')), before);
});

test('keeps everything when neither package nor name change', () => {
  const root = gradleProject();
  assert.equal(renameProject(root, { fromPackage: 'io.miragon.blueprint', toPackage: 'io.miragon.blueprint', toName: 'cibseven-embedded-example' }), 0);
});
