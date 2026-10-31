import { execFileSync } from 'node:child_process';
import { loadExamples } from './lib/examples.mjs';

const pathsWithoutEffectOnExamples = [/^README\.md$/, /^docs\//, /^CONTRIBUTING\.md$/, /^LICENSE$/, /^\.claude\//, /^\.claude-plugin\//, /^plugins\//];
const representativeExamples = ['fullstack-cibseven-embedded', 'operaton-remote', 'zeebe-native'];

export function selectChanges(examples, changedPaths, { sharedToolingSelectsAll = false } = {}) {
  const touchesSharedTooling = changedPaths.some(
    (path) => !path.startsWith('examples/') && !pathsWithoutEffectOnExamples.some((pattern) => pattern.test(path)),
  );
  const selected = examples
    .map((example) => ({ example, paths: pathsInside(example, changedPaths) }))
    .filter(({ example, paths }) => paths.length > 0 || (touchesSharedTooling && (sharedToolingSelectsAll || representativeExamples.includes(example.name))));
  return {
    examples: selected.map(({ example }) => example.name),
    variants: selected.flatMap(({ example, paths }) => changedVariants(example, paths, touchesSharedTooling).map((variant) => `${example.name}/${variant}`)),
  };
}

function pathsInside(example, changedPaths) {
  const prefix = `examples/${example.name}/`;
  return changedPaths.filter((path) => path.startsWith(prefix)).map((path) => path.slice(prefix.length));
}

function changedVariants(example, paths, touchesSharedTooling) {
  const variants = Object.keys(example.variants);
  const nonMarkdownPaths = paths.filter((path) => !path.endsWith('.md'));
  const namesNoVariant = (path) => !variants.some((variant) => path.includes(variant));
  const includesFirstVariant = touchesSharedTooling || nonMarkdownPaths.some(namesNoVariant);
  return variants.filter((variant, index) => (includesFirstVariant && index === 0) || nonMarkdownPaths.some((path) => path.includes(variant)));
}

function changedPathsBetween(base, head) {
  return execFileSync('git', ['diff', '--name-only', `${base}...${head}`], { encoding: 'utf8' }).split('\n').filter(Boolean);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const examples = loadExamples();
  const [base, head] = process.argv.slice(2);
  const everyExample = examples.map((example) => example.name);
  const selection =
    process.argv.includes('--all') || !base
      ? { examples: everyExample, variants: examples.flatMap((example) => Object.keys(example.variants).map((variant) => `${example.name}/${variant}`)) }
      : selectChanges(examples, changedPathsBetween(base, head), { sharedToolingSelectsAll: process.argv.includes('--shared-tooling-selects-all') });
  console.log(`examples=${JSON.stringify(selection.examples)}`);
  console.log(`variants=${JSON.stringify(selection.variants)}`);
}
