import { DriftRule } from '../drift-rule.mjs';

export class LanguageOnlyEntriesHaveReasons extends DriftRule {
  check(context) {
    return context.variantPairs.flatMap((pair) => pair.languageOnlyEntries
      .filter((entry) => !entry.reason?.trim())
      .map((entry) => `languageOnly in drift.yml: ${[...(entry.sources ?? []), ...(entry.tests ?? [])].join(', ')} has no reason`));
  }
}
