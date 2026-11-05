import assert from 'node:assert/strict';
import { test } from 'node:test';
import { checkDrift, comparedPath, identicalGroups, snapshot, variantViolations } from '../check-drift.mjs';

const manifest = {
  engines: { cibseven: ['cibseven', 'CIB seven'], operaton: ['operaton', 'Operaton'] },
  independent: [{ files: ['**/pom.xml'], reason: 'Dependabot bumps it per example' }],
  differs: [{ files: ['README.md'], reason: 'names its own example' }],
};

function repository(contents) {
  const examples = Object.keys(contents);
  return {
    examples,
    filesOf: (example) => Object.keys(contents[example]),
    contentOf: (example, path) => contents[example][path],
    manifest,
  };
}

function lockOf(input) {
  return identicalGroups(snapshot(input));
}

const inStep = {
  cibseven: { LICENSE: 'MIT', 'README.md': 'embedded, port 8080', 'pom.xml': '1.0', 'adapter/cibseven/Delegate.kt': 'class CIB seven delegate' },
  operaton: { LICENSE: 'MIT', 'README.md': 'remote, port 8081', 'pom.xml': '1.1', 'adapter/operaton/Delegate.kt': 'class Operaton delegate' },
};

test('holds while every copy is identical or has a reason', () => {
  const input = repository(inStep);
  assert.deepEqual(checkDrift({ ...input, lock: lockOf(input) }), []);
});

test('treats copies that differ only in the engine name as identical', () => {
  assert.deepEqual(lockOf(repository(inStep)), [{ examples: ['cibseven', 'operaton'], files: ['LICENSE', 'adapter/{engine}/Delegate.kt'] }]);
});

test('reports a locked copy that drifted', () => {
  const lock = lockOf(repository(inStep));
  const drifted = repository({ ...inStep, operaton: { ...inStep.operaton, LICENSE: 'Apache' } });
  assert.deepEqual(checkDrift({ ...drifted, lock }), ['LICENSE drifted in cibseven, operaton: must stay identical to the other copies']);
});

test('reports a new file that is neither identical nor explained', () => {
  const lock = lockOf(repository(inStep));
  const withNewFile = repository({ ...inStep, operaton: { ...inStep.operaton, 'NOTES.md': 'only here' } });
  assert.deepEqual(checkDrift({ ...withNewFile, lock }), ['operaton: NOTES.md is neither identical to another example nor listed under differs in drift.yml']);
});

test('reports copies that became identical but are not locked yet', () => {
  const lock = lockOf(repository(inStep));
  const both = repository({ cibseven: { ...inStep.cibseven, 'NOTES.md': 'x' }, operaton: { ...inStep.operaton, 'NOTES.md': 'x' } });
  assert.deepEqual(checkDrift({ ...both, lock }), ['NOTES.md is identical in [cibseven, operaton] but not locked: run npm run drift:update']);
});

test('asks for an update, not a fix, when a locked group grows', () => {
  const third = { LICENSE: 'BSD', 'README.md': 'third', 'adapter/cibseven/Delegate.kt': 'class CIB seven delegate' };
  const lock = lockOf(repository({ ...inStep, third }));
  const allAlike = repository({ ...inStep, third: { ...third, LICENSE: 'MIT' } });
  assert.deepEqual(checkDrift({ ...allAlike, lock }), ['LICENSE is identical in [cibseven, operaton, third] but not locked: run npm run drift:update']);
});

test('reports a differs entry whose files are all identical now', () => {
  const sameReadme = repository({ cibseven: { ...inStep.cibseven, 'README.md': 'same' }, operaton: { ...inStep.operaton, 'README.md': 'same' } });
  assert.deepEqual(checkDrift({ ...sameReadme, lock: lockOf(sameReadme) }), ['differs in drift.yml: every file of README.md is identical now, remove it']);
});

test('reports an entry without a reason', () => {
  const input = repository(inStep);
  const withoutReason = { ...manifest, differs: [{ files: ['README.md'], reason: ' ' }] };
  assert.deepEqual(checkDrift({ ...input, manifest: withoutReason, lock: lockOf(input) }), ['differs in drift.yml: README.md has no reason']);
});

const example = { name: 'cibseven', identicalResources: ['src/main/resources'] };
const kotlin = ['app/src/test/kotlin/io/ProcessTest.kt', 'src/main/resources/application.yaml'];
const java = ['app/src/test/java/io/ProcessTest.java', 'src/main/resources/application.yaml'];
const sameContent = () => 'same';

test('holds while both variants have the same sources and resources', () => {
  assert.deepEqual(variantViolations(example, { 'kotlin-gradle': kotlin, 'java-maven': java }, sameContent, []), []);
});

test('reports a source that exists in one variant only', () => {
  const javaOnly = [...java, 'app/src/test/java/io/ExtraTest.java'];
  assert.deepEqual(variantViolations(example, { 'kotlin-gradle': kotlin, 'java-maven': javaOnly }, sameContent, []),
    ['cibseven: app/src/test/io/ExtraTest exists only in Java; add it to the other variant or to languageOnly in drift.yml']);
});

test('accepts a language-only source with a reason', () => {
  const javaOnly = [...java, 'app/src/test/java/io/ExtraTest.java'];
  const languageOnly = [{ examples: ['cibseven'], sources: ['app/src/test/io/ExtraTest'], reason: 'Java needs it' }];
  assert.deepEqual(variantViolations(example, { 'kotlin-gradle': kotlin, 'java-maven': javaOnly }, sameContent, languageOnly), []);
});

test('reports resources that differ between the variants', () => {
  const contentOf = (path) => (path.startsWith('java-maven/') ? 'changed' : 'same');
  assert.deepEqual(variantViolations(example, { 'kotlin-gradle': kotlin, 'java-maven': java }, contentOf, []),
    ['cibseven: src/main/resources/application.yaml differs between kotlin-gradle and java-maven (identicalResources)']);
});

const kotlinTest = 'class ProcessTest {\n    @Test\n    fun `rejects an unknown id`() {}\n}';
const javaTest = (name) => `class ProcessTest {\n    @Test\n    @DisplayName("${name}")\n    void rejectsUnknown() {}\n}`;
const testsOf = (javaName) => (path) => (path.startsWith('java-maven/') ? javaTest(javaName) : kotlinTest);
const testFiles = { 'kotlin-gradle': ['app/src/test/kotlin/io/ProcessTest.kt'], 'java-maven': ['app/src/test/java/io/ProcessTest.java'] };
const noResources = { name: 'cibseven', identicalResources: [] };

test('matches test methods by their display name, ignoring case and punctuation', () => {
  assert.deepEqual(variantViolations(noResources, testFiles, testsOf('Rejects an unknown ID'), []), []);
});

test('reports a test method that exists in one variant only', () => {
  assert.deepEqual(variantViolations(noResources, testFiles, testsOf('rejects a missing id'), []), [
    'cibseven: test rejectsanunknownid in app/src/test/io/ProcessTest exists only in Kotlin; name it alike in both variants or add it to languageOnly tests in drift.yml',
    'cibseven: test rejectsamissingid in app/src/test/io/ProcessTest exists only in Java; name it alike in both variants or add it to languageOnly tests in drift.yml',
  ]);
});

test('accepts a language-only test with a reason', () => {
  const languageOnly = [{ examples: ['cibseven'], tests: ['app/src/test/io/ProcessTest#rejects a missing id', 'app/src/test/io/ProcessTest#rejects an unknown id'], reason: 'idioms differ' }];
  assert.deepEqual(variantViolations(noResources, testFiles, testsOf('rejects a missing id'), languageOnly), []);
});

test('compares the service of a single-variant example at the root with that variant in the others', () => {
  const atRoot = { variants: { 'kotlin-gradle': { path: '.' } } };
  assert.equal(comparedPath(atRoot, 'service/app/src/main/kotlin/App.kt'), 'kotlin-gradle/service/app/src/main/kotlin/App.kt');
  assert.equal(comparedPath(atRoot, 'docs/README.md'), 'docs/README.md');
  assert.equal(comparedPath({ variants: { 'java-maven': { path: 'java-maven' } } }, 'java-maven/pom.xml'), 'java-maven/pom.xml');
});
