import { DriftRule } from '../drift-rule.mjs';

export class LanguageOnlyTestsAreUsed extends DriftRule {
  check(context) {
    return context.variantPairs.flatMap((pair) => {
      const mismatched = new Set(pair.testMismatches.map(({ key }) => key));
      return [...pair.allowedTests]
        .filter((test) => !mismatched.has(test))
        .map((test) => `languageOnly in drift.yml: ${pair.name} test ${test} is in both variants or in none, remove it`);
    });
  }
}
