import assert from 'node:assert/strict';
import { test } from 'node:test';
import { selectChanges } from '../changed-examples.mjs';

const twoVariants = (name) => ({ name, variants: { 'kotlin-gradle': { path: 'kotlin-gradle' }, 'java-maven': { path: 'java-maven' } } });
const oneVariant = (name, variant) => ({ name, variants: { [variant]: { path: '.' } } });

const examples = [
  twoVariants('cibseven-embedded'),
  twoVariants('cibseven-remote'),
  oneVariant('fullstack-cibseven-embedded', 'kotlin-gradle'),
  oneVariant('operaton-process-engine-api', 'java-maven'),
  twoVariants('operaton-remote'),
  twoVariants('zeebe-native'),
];

test('selects only the examples whose files changed', () => {
  const { examples: selected } = selectChanges(examples, ['examples/cibseven-remote/README.md', 'examples/zeebe-native/bruno/x.bru']);
  assert.deepEqual(selected, ['cibseven-remote', 'zeebe-native']);
});

test('does not mistake an example for another one with the same prefix', () => {
  assert.deepEqual(selectChanges(examples, ['examples/cibseven-embedded-legacy/README.md']), { examples: [], variants: [] });
});

test('a path inside a variant selects that variant', () => {
  assert.deepEqual(selectChanges(examples, ['examples/zeebe-native/java-maven/pom.xml']), {
    examples: ['zeebe-native'],
    variants: ['zeebe-native/java-maven'],
  });
  assert.deepEqual(selectChanges(examples, ['examples/zeebe-native/kotlin-gradle/build.gradle.kts', 'examples/zeebe-native/java-maven/pom.xml']).variants, [
    'zeebe-native/kotlin-gradle',
    'zeebe-native/java-maven',
  ]);
});

test('a workflow named after a variant selects that variant', () => {
  assert.deepEqual(selectChanges(examples, ['examples/zeebe-native/.github/workflows/pre-merge-java-maven.yml']).variants, ['zeebe-native/java-maven']);
});

test('any other path of an example selects its first variant', () => {
  assert.deepEqual(selectChanges(examples, ['examples/zeebe-native/bruno/x.bru']).variants, ['zeebe-native/kotlin-gradle']);
  assert.deepEqual(selectChanges(examples, ['examples/operaton-process-engine-api/service/app/build.txt']).variants, ['operaton-process-engine-api/java-maven']);
});

test('a Markdown-only change keeps the example for its starter but selects no variant', () => {
  assert.deepEqual(selectChanges(examples, ['examples/zeebe-native/README.md', 'examples/zeebe-native/kotlin-gradle/NOTES.md']), {
    examples: ['zeebe-native'],
    variants: [],
  });
});

test('a mixed change selects by its non-Markdown paths', () => {
  assert.deepEqual(selectChanges(examples, ['examples/zeebe-native/README.md', 'examples/zeebe-native/java-maven/pom.xml']).variants, ['zeebe-native/java-maven']);
});

test('shared tooling selects the representative subset on the first variant', () => {
  const expected = {
    examples: ['fullstack-cibseven-embedded', 'operaton-remote', 'zeebe-native'],
    variants: ['fullstack-cibseven-embedded/kotlin-gradle', 'operaton-remote/kotlin-gradle', 'zeebe-native/kotlin-gradle'],
  };
  assert.deepEqual(selectChanges(examples, ['tooling/starter/export.sh']), expected);
  assert.deepEqual(selectChanges(examples, ['.github/workflows/_blueprint.yml']), expected);
});

test('shared tooling keeps the examples that were touched directly', () => {
  const { examples: selected, variants } = selectChanges(examples, ['tooling/starter/export.sh', 'examples/cibseven-remote/java-maven/pom.xml']);
  assert.deepEqual(selected, ['cibseven-remote', 'fullstack-cibseven-embedded', 'operaton-remote', 'zeebe-native']);
  assert.ok(variants.includes('cibseven-remote/java-maven'));
});

test('the nightly mode selects every example when shared tooling changed', () => {
  const { examples: selected } = selectChanges(examples, ['tooling/starter/export.sh'], { sharedToolingSelectsAll: true });
  assert.deepEqual(
    selected,
    examples.map((example) => example.name),
  );
});

test('selects none for documentation of the repository itself', () => {
  assert.deepEqual(selectChanges(examples, ['README.md', 'docs/choose.md', 'docs/adr/0001-x.md']), { examples: [], variants: [] });
});
