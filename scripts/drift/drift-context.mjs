import { DriftSnapshot } from './drift-snapshot.mjs';
import { EngineNormalizer } from './engine-normalizer.mjs';
import { globMatcher } from '../lib/glob.mjs';

const copyKey = (example, path) => `${example}\0${path}`;

export class DriftContext {
  constructor({ examples = [], filesOf, contentOf, manifest, lock = [], variantPairs = [] }) {
    const normalizer = new EngineNormalizer(manifest.engines);
    this.manifest = manifest;
    this.variantPairs = variantPairs;
    this.snapshot = DriftSnapshot.take({ examples, filesOf, contentOf, manifest });
    this.allFiles = new Map(examples.map((example) => [example, filesOf(example).map((path) => normalizer.normalize(path))]));
    const current = this.snapshot.identicalGroups();
    this.lockedByPath = DriftContext.#groupsByPath(lock);
    this.currentByPath = DriftContext.#groupsByPath(current);
    this.lockedCopies = DriftContext.#copiesIn(lock);
    this.currentCopies = DriftContext.#copiesIn(current);
    this.drifts = this.#findDrifts();
  }

  static #groupsByPath(groups) {
    const byPath = new Map();
    for (const { examples, files } of groups) {
      for (const path of files) byPath.set(path, [...(byPath.get(path) ?? []), examples.join(', ')]);
    }
    return byPath;
  }

  static #copiesIn(groups) {
    return new Set(groups.flatMap(({ examples, files }) => examples.flatMap((example) => files.map((path) => copyKey(example, path)))));
  }

  #findDrifts() {
    const containedIn = (set, groups) => groups.some((group) => set.split(', ').every((example) => group.split(', ').includes(example)));
    return [...this.lockedByPath].flatMap(([path, sets]) => {
      const now = this.currentByPath.get(path) ?? [];
      return sets.filter((set) => !containedIn(set, now)).map((set) => {
        const examples = set.split(', ');
        const stillAlike = now.map((group) => group.split(', ').filter((example) => examples.includes(example)))
          .sort((left, right) => right.length - left.length)[0] ?? [];
        return { path, examples, stillAlike };
      });
    });
  }

  isLocked(example, path) {
    return this.lockedCopies.has(copyKey(example, path));
  }

  isIdenticalCopy(example, path) {
    return this.isLocked(example, path) || this.currentCopies.has(copyKey(example, path));
  }

  manifestSections() {
    return [['independent', this.manifest.independent ?? []], ['differs', this.manifest.differs ?? []]];
  }

  filesMatching(entry, pattern) {
    const matches = globMatcher([pattern]);
    return [...this.allFiles].flatMap(([example, paths]) =>
      (!entry.examples || entry.examples.includes(example)) ? paths.filter(matches).map((path) => ({ example, path })) : []);
  }
}

export const positivePatterns = (entry) => entry.files.filter((pattern) => !pattern.startsWith('!'));
