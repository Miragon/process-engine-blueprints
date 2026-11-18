import assert from 'node:assert/strict';
import { test } from 'node:test';
import { renderIdeaGradle, renderIdeaMisc } from '../lib/idea.mjs';

const examples = [
  { name: 'zeebe-native', variants: { 'kotlin-gradle': { path: 'kotlin-gradle' }, 'java-maven': { path: 'java-maven' } } },
  { name: 'operaton-process-engine-api', variants: { 'java-maven': { path: '.' } } },
];

test('links the pom.xml of every Java/Maven variant, sorted by path', () => {
  const misc = renderIdeaMisc(examples);
  assert.ok(misc.indexOf('$PROJECT_DIR$/examples/operaton-process-engine-api/pom.xml') < misc.indexOf('$PROJECT_DIR$/examples/zeebe-native/java-maven/pom.xml'));
  assert.ok(!misc.includes('kotlin-gradle'));
});

test('links the folder of every Kotlin/Gradle variant', () => {
  const gradle = renderIdeaGradle(examples);
  assert.ok(gradle.includes('<option name="externalProjectPath" value="$PROJECT_DIR$/examples/zeebe-native/kotlin-gradle" />'));
  assert.ok(!gradle.includes('java-maven'));
});
