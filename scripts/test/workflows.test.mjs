import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { parse } from 'yaml';
import { exampleWorkflows, renderWorkflow, reusableWorkflow } from '../lib/workflows.mjs';

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

function exampleWith(file, content) {
  const dir = mkdtempSync(join(tmpdir(), 'example-'));
  mkdirSync(join(dir, '.github/workflows'), { recursive: true });
  writeFileSync(join(dir, '.github/workflows', file), content);
  return { name: 'demo-embedded', dir };
}

test('turns an example workflow into a reusable one rooted in the example', () => {
  const example = exampleWith('pre-merge-java-maven.yml', source);
  const [workflow] = exampleWorkflows(example);
  const rendered = renderWorkflow('# generated', reusableWorkflow(example, workflow));
  const generated = parse(rendered);
  const steps = generated.jobs.backend.steps;

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

test('ignores workflows that are not pre-merge or nightly checks', () => {
  const example = exampleWith('blueprint.yml', source);
  assert.deepEqual(exampleWorkflows(example), []);
});
