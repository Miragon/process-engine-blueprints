import { DriftRule } from '../drift-rule.mjs';

export class IdenticalResourcesMatch extends DriftRule {
  check(context) {
    return context.variantPairs.flatMap((pair) => pair.example.identicalResources.flatMap((resource) => {
      const under = (paths) => paths.filter((path) => path.startsWith(`${resource}/`));
      const kotlinResources = under(pair.kotlinFiles);
      const javaResources = under(pair.javaFiles);
      return [...new Set([...kotlinResources, ...javaResources])].flatMap((path) => {
        if (!kotlinResources.includes(path) || !javaResources.includes(path)) {
          return [`${pair.name}: ${path} is missing in one variant (identicalResources)`];
        }
        if (pair.contentOf(`kotlin-gradle/${path}`) !== pair.contentOf(`java-maven/${path}`)) {
          return [`${pair.name}: ${path} differs between kotlin-gradle and java-maven (identicalResources)`];
        }
        return [];
      });
    }));
  }
}
