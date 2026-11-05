import { DriftRule } from '../drift-rule.mjs';

export class LockedCopiesStayIdentical extends DriftRule {
  check(context) {
    return context.drifts.map(({ path, examples, stillAlike }) => {
      const drifted = examples.filter((example) => !stillAlike.includes(example));
      return `${path} drifted in ${drifted.join(', ')}: must stay identical to ${stillAlike.join(', ') || 'the other copies'}`;
    });
  }
}
