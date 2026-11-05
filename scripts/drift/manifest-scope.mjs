import { globMatcher } from '../lib/glob.mjs';

export class ManifestScope {
  #scoped;

  constructor(entries) {
    this.#scoped = entries.map((entry) => ({ entry, matches: globMatcher(entry.files) }));
  }

  entryFor(example, path) {
    return this.#scoped.find(({ entry, matches }) => (!entry.examples || entry.examples.includes(example)) && matches(path))?.entry;
  }
}
