import { DriftRule } from '../drift-rule.mjs';

export class LanguageOnlySourcesAreUsed extends DriftRule {
  check(context) {
    return context.variantPairs.flatMap((pair) => [...pair.allowedSources]
      .filter((source) => pair.kotlinSources.has(source) === pair.javaSources.has(source))
      .map((source) => `languageOnly in drift.yml: ${pair.name} ${source} is not in exactly one variant, remove it`));
  }
}
