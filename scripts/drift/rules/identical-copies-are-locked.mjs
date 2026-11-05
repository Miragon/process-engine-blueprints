import { DriftRule } from '../drift-rule.mjs';

export class IdenticalCopiesAreLocked extends DriftRule {
  check(context) {
    const driftedExamples = new Map();
    for (const { path, examples } of context.drifts) driftedExamples.set(path, [...(driftedExamples.get(path) ?? []), ...examples]);
    const found = [];
    for (const [path, sets] of context.currentByPath) {
      const locked = context.lockedByPath.get(path) ?? [];
      const splitFromDrift = (set) => set.split(', ').every((example) => driftedExamples.get(path)?.includes(example));
      for (const set of sets.filter((set) => !locked.includes(set) && !splitFromDrift(set))) {
        found.push(`${path} is identical in [${set}] but not locked: run npm run drift:update`);
      }
    }
    return found;
  }
}
