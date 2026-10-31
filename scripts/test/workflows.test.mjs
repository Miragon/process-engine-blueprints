import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { parse } from 'yaml';
import { ciWorkflow, exampleWorkflows, nightlyWorkflow, renderWorkflow, reusableWorkflow } from '../lib/workflows.mjs';

const source = `name: Pre-Merge Checks · Java/Maven

on:
  pull_request:
    branches: [ main ]

concurrency:
  group: \${{ github.workflow }}
  cancel-in-progress: true

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - run: ./mvnw verify
  mutation:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@abc # v7.0.1
      - uses: actions/setup-node@def # v7.1.0
        with:
          cache-dependency-path: package-lock.json
      - working-directory: java-maven
        run: ./mvnw verify
      - run: git diff --name-only base HEAD -- java-maven/src
      - run: ./mvnw spring-boot:run > "$GITHUB_WORKSPACE/app.log" 2>&1 &
      - uses: actions/upload-artifact@ghi # v7
        with:
          name: pitest-report
          path: java-maven/target/pit-reports
`;

function exampleWith(file, content, variants = { 'kotlin-gradle': {}, 'java-maven': {} }) {
  const dir = mkdtempSync(join(tmpdir(), 'example-'));
  mkdirSync(join(dir, '.github/workflows'), { recursive: true });
  writeFileSync(join(dir, '.github/workflows', file), content);
  return { name: 'demo-embedded', dir, variants };
}

test('turns an example workflow into a reusable one rooted in the example', () => {
  const example = exampleWith('pre-merge-java-maven.yml', source);
  const [workflow] = exampleWorkflows(example);
  const rendered = renderWorkflow('# generated', reusableWorkflow(example, workflow));
  const generated = parse(rendered);
  const steps = generated.jobs.mutation.steps;

  assert.equal(workflow.generatedFile, 'demo-embedded.pre-merge-java-maven.yml');
  assert.deepEqual(generated.on, { workflow_call: {} });
  assert.equal(generated.concurrency, undefined);
  assert.equal(generated.defaults.run['working-directory'], 'examples/demo-embedded');
  assert.equal(steps[1].with['cache-dependency-path'], 'examples/demo-embedded/package-lock.json');
  assert.equal(steps[2]['working-directory'], 'examples/demo-embedded/java-maven');
  assert.equal(steps[3].run, 'git diff --relative --name-only base HEAD -- java-maven/src');
  assert.equal(steps[4].run, './mvnw spring-boot:run > "$GITHUB_WORKSPACE/examples/demo-embedded/app.log" 2>&1 &');
  assert.equal(steps[5].with.name, 'demo-embedded-pitest-report');
  assert.equal(steps[5].with.path, 'examples/demo-embedded/java-maven/target/pit-reports');
  assert.match(rendered, /uses: actions\/checkout@abc # v7\.0\.1/);
});

test('leaves the backend job of a pre-merge workflow to the starter build', () => {
  const example = exampleWith('pre-merge-java-maven.yml', source);
  const [workflow] = exampleWorkflows(example);
  assert.equal(parse(renderWorkflow('# generated', reusableWorkflow(example, workflow))).jobs.backend, undefined);
});

test('keeps the backend job of a nightly workflow', () => {
  const example = exampleWith('nightly-java-maven.yml', source);
  const [workflow] = exampleWorkflows(example);
  assert.ok(parse(renderWorkflow('# generated', reusableWorkflow(example, workflow))).jobs.backend);
});

test('gates a pre-merge workflow on its variant and the blueprint checks on the example', () => {
  const { jobs } = ciWorkflow([exampleWith('pre-merge-java-maven.yml', source)]);
  assert.equal(jobs['demo-embedded--pre-merge-java-maven'].if, "contains(fromJSON(needs.changes.outputs.variants), 'demo-embedded/java-maven')");
  assert.equal(jobs['demo-embedded--blueprint'].if, "contains(fromJSON(needs.changes.outputs.examples), 'demo-embedded')");
});

test('gates a workflow without a variant in its name on the first variant', () => {
  const { jobs } = ciWorkflow([exampleWith('pre-merge.yml', source, { 'java-maven': {} })]);
  assert.equal(jobs['demo-embedded--pre-merge'].if, "contains(fromJSON(needs.changes.outputs.variants), 'demo-embedded/java-maven')");
});

test('the nightly run builds the starters of the selected examples', () => {
  const { jobs } = nightlyWorkflow([exampleWith('nightly-java-maven.yml', source)]);
  assert.equal(jobs['demo-embedded--blueprint'].uses, './.github/workflows/_blueprint.yml');
  assert.equal(jobs['demo-embedded--blueprint'].if, "contains(fromJSON(needs.changes.outputs.examples), 'demo-embedded')");
});

test('the nightly selection keeps checking every example on a change to shared tooling', () => {
  const { jobs } = nightlyWorkflow([]);
  assert.match(jobs.changes.steps.at(-1).run, /--shared-tooling-selects-all/);
});

test('ignores workflows that are not pre-merge or nightly checks', () => {
  const example = exampleWith('blueprint.yml', source);
  assert.deepEqual(exampleWorkflows(example), []);
});
