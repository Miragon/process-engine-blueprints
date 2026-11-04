import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { repoRoot, variantsOf } from './examples.mjs';
import { engines, integrationLabel } from './readme.mjs';

export function repositorySlug() {
  const { repository } = JSON.parse(readFileSync(join(repoRoot, 'package.json'), 'utf8'));
  return repository.url.match(/github\.com\/([^/]+\/[^/.]+)/)[1];
}

export function starterCatalog(examples) {
  return {
    repository: repositorySlug(),
    examples: examples.map((example) => ({
      name: example.name,
      engine: example.engine,
      engineLabel: engines[example.engine],
      integration: example.integration,
      integrationLabel: integrationLabel(example),
      topology: example.topology,
      summary: example.summary,
      variants: variantsOf(example).map(({ variant }) => variant),
    })),
  };
}

export function renderCatalog(catalog) {
  return `${JSON.stringify(catalog, null, 2)}\n`;
}
