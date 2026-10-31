import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { isDocument, parse, parseDocument, stringify } from 'yaml';

const exampleWorkflowPattern = /^(pre-merge|nightly)(-[a-z-]+)?\.yml$/;
const workspacePathInputs = ['cache-dependency-path', 'node-version-file', 'java-version-file', 'build-root-directory'];
const actionsWithWorkspacePath = ['actions/upload-artifact', 'actions/download-artifact', 'actions/cache'];
const callerWorkflows = { 'pre-merge': 'ci', nightly: 'nightly' };

export function exampleWorkflows(example) {
  const workflowsDir = join(example.dir, '.github', 'workflows');
  return readdirSync(workflowsDir)
    .filter((file) => exampleWorkflowPattern.test(file))
    .sort()
    .map((file) => {
      const text = readFileSync(join(workflowsDir, file), 'utf8');
      return { file, kind: file.match(exampleWorkflowPattern)[1], generatedFile: `${example.name}.${file}`, text, source: parse(text) };
    });
}

export function callerOf(workflow) {
  return callerWorkflows[workflow.kind];
}

export function reusableWorkflow(example, workflow) {
  const exampleRoot = `examples/${example.name}`;
  const doc = parseDocument(workflow.text);
  doc.set('name', `${example.name} · ${doc.get('name')}`);
  doc.set('on', doc.createNode({ workflow_call: {} }));
  doc.delete('concurrency');
  if (workflow.kind === 'pre-merge') {
    doc.deleteIn(['jobs', 'backend']);
  }
  const workingDirectory = doc.getIn(['defaults', 'run', 'working-directory']) ?? '.';
  doc.setIn(['defaults', 'run', 'working-directory'], prefixed(exampleRoot, workingDirectory));
  moveAfter(doc, 'defaults', 'on');
  for (const job of doc.get('jobs').items.map((pair) => pair.value)) {
    relocateJob(example, exampleRoot, job);
  }
  return doc;
}

function moveAfter(doc, key, anchor) {
  const items = doc.contents.items;
  const moved = items.splice(items.findIndex((pair) => pair.key.value === key), 1)[0];
  items.splice(items.findIndex((pair) => pair.key.value === anchor) + 1, 0, moved);
}

function relocateJob(example, exampleRoot, job) {
  const jobWorkingDirectory = job.getIn(['defaults', 'run', 'working-directory']);
  if (jobWorkingDirectory) {
    job.setIn(['defaults', 'run', 'working-directory'], prefixed(exampleRoot, jobWorkingDirectory));
  }
  for (const step of job.get('steps').items) {
    relocateStep(example, exampleRoot, step);
  }
}

function relocateStep(example, exampleRoot, step) {
  if (step.get('working-directory')) {
    step.set('working-directory', prefixed(exampleRoot, step.get('working-directory')));
  }
  const run = step.get('run', true);
  if (run) {
    run.value = run.value.replaceAll('git diff --name-only', 'git diff --relative --name-only').replaceAll('$GITHUB_WORKSPACE/', `$GITHUB_WORKSPACE/${exampleRoot}/`);
  }
  const inputs = step.get('with');
  if (inputs) {
    relocateInputs(example, exampleRoot, step.get('uses')?.split('@')[0], inputs);
  }
}

function relocateInputs(example, exampleRoot, action, inputs) {
  const relocate = (key) => {
    const node = inputs.get(key, true);
    node.value = prefixedLines(exampleRoot, node.value);
  };
  workspacePathInputs.filter((input) => typeof inputs.get(input) === 'string').forEach(relocate);
  if (actionsWithWorkspacePath.includes(action) && typeof inputs.get('path') === 'string') {
    relocate('path');
  }
  if (action === 'actions/upload-artifact' && inputs.get('name')) {
    inputs.get('name', true).value = `${example.name}-${inputs.get('name')}`;
  }
}

function prefixedLines(root, value) {
  return value
    .split('\n')
    .map((line) => (line.trim() === '' ? line : prefixed(root, line.trim())))
    .join('\n');
}

function prefixed(root, path) {
  if (path.startsWith('/') || path.startsWith('${{') || path.startsWith('$')) {
    return path;
  }
  const relative = path.replace(/^\.\/?/, '');
  return relative === '' ? root : `${root}/${relative}`;
}

export function renderWorkflow(header, workflow) {
  const body = isDocument(workflow) ? workflow.toString({ lineWidth: 0 }) : withVersionComments(stringify(workflow, { lineWidth: 0, aliasDuplicateObjects: false }));
  return `${header}\n${body}`;
}

function withVersionComments(text) {
  return text.replace(/^(\s*-?\s*uses: )(\S+)$/gm, (line, prefix, action) => (actionPins[action] ? `${prefix}${action} # ${actionPins[action]}` : line));
}

function variantOf(example, workflow) {
  const variants = Object.keys(example.variants);
  return variants.find((variant) => workflow.file.includes(variant)) ?? variants[0];
}

function jobId(example, workflow) {
  return `${example.name}--${workflow.file.replace(/\.yml$/, '')}`.replaceAll('.', '-');
}

const actionPins = {
  'actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1': 'v7.0.1',
  'actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1': 'v7.1.0',
};
const checkout = { name: 'Checkout code', uses: 'actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1' };
const setupNode = {
  name: 'Set up Node',
  uses: 'actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1',
  with: { 'node-version': '22', cache: 'npm', 'cache-dependency-path': 'package-lock.json' },
};

export function ciWorkflow(examples) {
  const exampleJobs = {};
  for (const example of examples) {
    for (const workflow of exampleWorkflows(example).filter((candidate) => callerOf(candidate) === 'ci')) {
      exampleJobs[jobId(example, workflow)] = {
        name: `${example.name} · ${workflow.source.name}`,
        needs: 'changes',
        if: `contains(fromJSON(needs.changes.outputs.variants), '${example.name}/${variantOf(example, workflow)}')`,
        uses: `./.github/workflows/${workflow.generatedFile}`,
      };
    }
    exampleJobs[`${example.name}--blueprint`] = {
      name: `${example.name} · Blueprint checks`,
      needs: 'changes',
      if: `contains(fromJSON(needs.changes.outputs.examples), '${example.name}')`,
      uses: './.github/workflows/_blueprint.yml',
      with: { example: example.name },
    };
  }
  const gatedJobs = ['changes', 'generated', 'tooling', ...Object.keys(exampleJobs)];
  return {
    name: 'CI',
    on: { pull_request: { branches: ['main'] } },
    concurrency: { group: '${{ github.workflow }}-${{ github.head_ref || github.ref }}', 'cancel-in-progress': true },
    permissions: { contents: 'read' },
    jobs: {
      changes: {
        name: 'Select the examples to check',
        'runs-on': 'ubuntu-latest',
        outputs: { examples: '${{ steps.select.outputs.examples }}', variants: '${{ steps.select.outputs.variants }}' },
        steps: [
          { ...checkout, with: { 'fetch-depth': 0 } },
          setupNode,
          { name: 'Install the maintainer tooling', run: 'npm ci' },
          {
            name: 'Select examples and variants by the changed paths',
            id: 'select',
            env: { BASE_SHA: '${{ github.event.pull_request.base.sha }}' },
            run: 'selection=$(node scripts/changed-examples.mjs "$BASE_SHA" HEAD)\necho "$selection"\necho "$selection" >> "$GITHUB_OUTPUT"',
          },
        ],
      },
      generated: {
        name: 'Generated files and cross-example drift',
        'runs-on': 'ubuntu-latest',
        steps: [
          checkout,
          setupNode,
          { name: 'Install the maintainer tooling', run: 'npm ci' },
          { name: 'Verify the generated files are in sync with examples/*/blueprint.yaml', run: 'npm run generate:check' },
          { name: 'Verify the copies across examples have not drifted (drift.yml)', run: 'npm run drift' },
          { name: 'Verify no hard-coded owner outside the owner setting', run: 'scripts/check-owner.sh' },
        ],
      },
      tooling: {
        name: 'Tooling tests',
        'runs-on': 'ubuntu-latest',
        steps: [checkout, setupNode, { name: 'Install the maintainer tooling', run: 'npm ci' }, { name: 'Test the generator and the starter CLI', run: 'npm test' }],
      },
      ...exampleJobs,
      'ci-gate': {
        name: 'CI gate',
        if: 'always()',
        needs: gatedJobs,
        'runs-on': 'ubuntu-latest',
        steps: [
          {
            name: 'Fail when a needed job failed or was cancelled',
            env: { RESULTS: '${{ join(needs.*.result, \' \') }}' },
            run: 'echo "$RESULTS"\nif [[ " $RESULTS " =~ " failure " || " $RESULTS " =~ " cancelled " ]]; then exit 1; fi',
          },
        ],
      },
    },
  };
}

export function nightlyWorkflow(examples) {
  const exampleJobs = {};
  for (const example of examples) {
    for (const workflow of exampleWorkflows(example).filter((candidate) => callerOf(candidate) === 'nightly')) {
      exampleJobs[jobId(example, workflow)] = {
        name: `${example.name} · ${workflow.source.name}`,
        needs: 'changes',
        if: `contains(fromJSON(needs.changes.outputs.examples), '${example.name}')`,
        uses: `./.github/workflows/${workflow.generatedFile}`,
      };
    }
    exampleJobs[`${example.name}--blueprint`] = {
      name: `${example.name} · Blueprint checks`,
      needs: 'changes',
      if: `contains(fromJSON(needs.changes.outputs.examples), '${example.name}')`,
      uses: './.github/workflows/_blueprint.yml',
      with: { example: example.name },
    };
  }
  return {
    name: 'Nightly',
    on: { schedule: [{ cron: '0 3 * * *' }], workflow_dispatch: {} },
    permissions: { contents: 'read' },
    jobs: {
      changes: {
        name: 'Select the examples to check',
        'runs-on': 'ubuntu-latest',
        outputs: { examples: '${{ steps.select.outputs.examples }}' },
        steps: [
          { ...checkout, with: { 'fetch-depth': 0 } },
          setupNode,
          { name: 'Install the maintainer tooling', run: 'npm ci' },
          {
            name: 'Every example on Sundays and on demand, otherwise the ones changed in the last day',
            id: 'select',
            run: [
              'if [[ "${{ github.event_name }}" == "workflow_dispatch" || "$(date -u +%u)" == "7" ]]; then',
              '  selection=$(node scripts/changed-examples.mjs --all)',
              'else',
              '  since=$(git rev-list -n1 --before="25 hours ago" HEAD || true)',
              '  selection=$(node scripts/changed-examples.mjs "${since:-}" HEAD --shared-tooling-selects-all)',
              'fi',
              'echo "$selection"',
              'echo "$selection" >> "$GITHUB_OUTPUT"',
            ].join('\n'),
          },
        ],
      },
      ...exampleJobs,
    },
  };
}
