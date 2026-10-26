import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parse } from 'yaml';

export const repoRoot = new URL('../../', import.meta.url).pathname;
export const examplesDir = join(repoRoot, 'examples');

export function loadExamples() {
  return readdirSync(examplesDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && existsSync(join(examplesDir, entry.name, 'blueprint.yaml')))
    .map((entry) => loadExample(entry.name))
    .sort((left, right) => left.name.localeCompare(right.name));
}

function loadExample(name) {
  const dir = join(examplesDir, name);
  const blueprint = parse(readFileSync(join(dir, 'blueprint.yaml'), 'utf8'));
  if (blueprint.name !== name) {
    throw new Error(`examples/${name}/blueprint.yaml names itself "${blueprint.name}"`);
  }
  return { ...blueprint, dir, missingVariants: blueprint.missingVariants ?? {}, identicalResources: blueprint.identicalResources ?? [] };
}

export function variantsOf(example) {
  return Object.entries(example.variants).map(([variant, { path }]) => ({ variant, path }));
}
