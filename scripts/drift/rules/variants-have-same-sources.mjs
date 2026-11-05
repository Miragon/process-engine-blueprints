import { DriftRule } from '../drift-rule.mjs';

export class VariantsHaveSameSources extends DriftRule {
  check(context) {
    return context.variantPairs.flatMap((pair) => [['Kotlin', pair.kotlinSources, pair.javaSources], ['Java', pair.javaSources, pair.kotlinSources]]
      .flatMap(([language, own, other]) => [...own.keys()]
        .filter((source) => !other.has(source) && !pair.allowedSources.has(source))
        .map((source) => `${pair.name}: ${source} exists only in ${language}; add it to the other variant or to languageOnly in drift.yml`)));
  }
}
