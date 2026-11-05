import { DriftRule } from '../drift-rule.mjs';
import { ManifestScope } from '../manifest-scope.mjs';

export class EveryFileIsClassified extends DriftRule {
  check(context) {
    const differs = new ManifestScope(context.manifest.differs ?? []);
    return [...context.snapshot.hashesByExample].flatMap(([example, hashes]) => [...hashes.keys()]
      .filter((path) => !context.isIdenticalCopy(example, path) && !differs.entryFor(example, path))
      .map((path) => `${example}: ${path} is neither identical to another example nor listed under differs in drift.yml`));
  }
}
