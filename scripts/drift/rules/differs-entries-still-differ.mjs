import { DriftRule } from '../drift-rule.mjs';
import { positivePatterns } from '../drift-context.mjs';

export class DiffersEntriesStillDiffer extends DriftRule {
  check(context) {
    const isIdenticalNow = ({ example, path }) => context.isLocked(example, path) || !context.snapshot.has(example, path);
    return (context.manifest.differs ?? []).flatMap((entry) => positivePatterns(entry)
      .filter((pattern) => {
        const hits = context.filesMatching(entry, pattern);
        return hits.length > 0 && hits.every(isIdenticalNow);
      })
      .map((pattern) => `differs in drift.yml: every file of ${pattern} is identical now, remove it`));
  }
}
