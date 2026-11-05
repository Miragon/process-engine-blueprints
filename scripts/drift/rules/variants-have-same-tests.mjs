import { DriftRule } from '../drift-rule.mjs';

export class VariantsHaveSameTests extends DriftRule {
  check(context) {
    return context.variantPairs.flatMap((pair) => pair.testMismatches
      .filter(({ key }) => !pair.allowedTests.has(key))
      .map(({ source, test, language }) => `${pair.name}: test ${test} in ${source} exists only in ${language}; name it alike in both variants or add it to languageOnly tests in drift.yml`));
  }
}
