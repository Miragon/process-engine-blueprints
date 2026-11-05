import { createHash } from 'node:crypto';
import { EngineNormalizer } from './engine-normalizer.mjs';
import { ManifestScope } from './manifest-scope.mjs';

export class DriftSnapshot {
  constructor(hashesByExample) {
    this.hashesByExample = hashesByExample;
  }

  static take({ examples, filesOf, contentOf, manifest }) {
    const normalizer = new EngineNormalizer(manifest.engines);
    const independent = new ManifestScope(manifest.independent ?? []);
    const hashesByExample = new Map();
    for (const example of examples) {
      hashesByExample.set(example, new Map());
      for (const path of filesOf(example)) {
        const normalizedPath = normalizer.normalize(path);
        if (!independent.entryFor(example, normalizedPath)) {
          const hash = createHash('sha256').update(normalizer.normalize(contentOf(example, path))).digest('hex');
          hashesByExample.get(example).set(normalizedPath, hash);
        }
      }
    }
    return new DriftSnapshot(hashesByExample);
  }

  has(example, path) {
    return this.hashesByExample.get(example).has(path);
  }

  identicalGroups() {
    const examplesByCopy = new Map();
    for (const [example, hashes] of this.hashesByExample) {
      for (const [path, hash] of hashes) {
        const key = `${path}\0${hash}`;
        examplesByCopy.set(key, [...(examplesByCopy.get(key) ?? []), example]);
      }
    }
    const pathsByExamples = new Map();
    for (const [key, examples] of examplesByCopy) {
      if (examples.length > 1) {
        const set = examples.sort().join(',');
        pathsByExamples.set(set, [...(pathsByExamples.get(set) ?? []), key.split('\0')[0]]);
      }
    }
    return [...pathsByExamples]
      .map(([set, paths]) => ({ examples: set.split(','), files: paths.sort() }))
      .sort((left, right) => right.examples.length - left.examples.length || left.examples.join().localeCompare(right.examples.join()));
  }
}
