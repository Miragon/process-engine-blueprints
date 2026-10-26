import { execFileSync } from 'node:child_process';
import { loadExamples } from './lib/examples.mjs';

const pathsWithoutEffectOnExamples = [/^README\.md$/, /^docs\//, /^CONTRIBUTING\.md$/, /^LICENSE$/, /^\.claude\//, /^\.claude-plugin\//, /^plugins\//];

export function selectExamples(exampleNames, changedPaths) {
  const touchesSharedTooling = changedPaths.some(
    (path) => !path.startsWith('examples/') && !pathsWithoutEffectOnExamples.some((pattern) => pattern.test(path)),
  );
  if (touchesSharedTooling) {
    return exampleNames;
  }
  return exampleNames.filter((name) => changedPaths.some((path) => path.startsWith(`examples/${name}/`)));
}

function changedPathsBetween(base, head) {
  return execFileSync('git', ['diff', '--name-only', `${base}...${head}`], { encoding: 'utf8' }).split('\n').filter(Boolean);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const exampleNames = loadExamples().map((example) => example.name);
  const [base, head] = process.argv.slice(2);
  const selected = process.argv.includes('--all') || !base ? exampleNames : selectExamples(exampleNames, changedPathsBetween(base, head));
  console.log(JSON.stringify(selected));
}
