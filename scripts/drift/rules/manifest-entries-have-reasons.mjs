import { DriftRule } from '../drift-rule.mjs';

export class ManifestEntriesHaveReasons extends DriftRule {
  check(context) {
    return context.manifestSections().flatMap(([section, entries]) => entries
      .filter((entry) => !entry.reason?.trim())
      .map((entry) => `${section} in drift.yml: ${entry.files.join(', ')} has no reason`));
  }
}
