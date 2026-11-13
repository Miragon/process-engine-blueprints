import { DiffersEntriesStillDiffer } from './rules/differs-entries-still-differ.mjs';
import { EveryFileIsClassified } from './rules/every-file-is-classified.mjs';
import { IdenticalCopiesAreLocked } from './rules/identical-copies-are-locked.mjs';
import { IdenticalResourcesMatch } from './rules/identical-resources-match.mjs';
import { LanguageOnlyEntriesHaveReasons } from './rules/language-only-entries-have-reasons.mjs';
import { LanguageOnlySourcesAreUsed } from './rules/language-only-sources-are-used.mjs';
import { LanguageOnlyTestsAreUsed } from './rules/language-only-tests-are-used.mjs';
import { LockedCopiesStayIdentical } from './rules/locked-copies-stay-identical.mjs';
import { ManifestEntriesHaveReasons } from './rules/manifest-entries-have-reasons.mjs';
import { ManifestPatternsMatchFiles } from './rules/manifest-patterns-match-files.mjs';
import { VariantsHaveSameSources } from './rules/variants-have-same-sources.mjs';
import { VariantsHaveSameTests } from './rules/variants-have-same-tests.mjs';

export class DriftValidator {
  #rules = [
    new LockedCopiesStayIdentical(),
    new IdenticalCopiesAreLocked(),
    new EveryFileIsClassified(),
    new ManifestEntriesHaveReasons(),
    new ManifestPatternsMatchFiles(),
    new DiffersEntriesStillDiffer(),
    new VariantsHaveSameSources(),
    new LanguageOnlySourcesAreUsed(),
    new VariantsHaveSameTests(),
    new LanguageOnlyTestsAreUsed(),
    new LanguageOnlyEntriesHaveReasons(),
    new IdenticalResourcesMatch(),
  ];

  validate(context) {
    return this.#rules.flatMap((rule) => rule.check(context).map((message) => `[${rule.constructor.name}] ${message}`));
  }
}
