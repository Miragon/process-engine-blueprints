import { EngineNormalizer } from './engine-normalizer.mjs';

const sourcePattern = /^(.*)\/src\/(main|test)\/(?:kotlin|java)\/(.*)\.(?:kt|java)$/;
const testAnnotation = String.raw`@(?:Test|ParameterizedTest|RepeatedTest)\b(?:\([^)]*\))?(?:\s*@\w+(?:\([^)]*\))?)*\s*`;
const testDeclaration = {
  Kotlin: new RegExp(`${testAnnotation}(?:(?:internal|public)\\s+)?fun\\s+(\`[^\`]+\`|\\w+)`, 'g'),
  Java: new RegExp(`${testAnnotation}(?:(?:public|protected)\\s+)?void\\s+(\\w+)`, 'g'),
};

export function comparableTestName(name) {
  return name.replaceAll("'", '').toLowerCase().replace(/[^a-z0-9]+/g, '').replaceAll('null', 'empty');
}

export class VariantPair {
  constructor({ example, kotlinFiles, javaFiles, contentOf, languageOnly, normalizer }) {
    this.example = example;
    this.name = example.name;
    this.kotlinFiles = kotlinFiles;
    this.javaFiles = javaFiles;
    this.contentOf = contentOf;
    this.kotlinSources = VariantPair.#sources(kotlinFiles, normalizer);
    this.javaSources = VariantPair.#sources(javaFiles, normalizer);
    this.languageOnlyEntries = languageOnly.filter((entry) => entry.examples.includes(example.name));
    this.allowedSources = new Set(this.languageOnlyEntries.flatMap((entry) => entry.sources ?? []));
    this.allowedTests = new Set(this.languageOnlyEntries.flatMap((entry) => (entry.tests ?? []).map((test) => {
      const [source, name] = test.split('#');
      return `${source}#${comparableTestName(name)}`;
    })));
    this.testMismatches = this.#findTestMismatches();
  }

  static of(example, variantFiles, contentOf, manifest) {
    const kotlinFiles = variantFiles['kotlin-gradle'];
    const javaFiles = variantFiles['java-maven'];
    if (!kotlinFiles || !javaFiles) return undefined;
    const normalizer = new EngineNormalizer(manifest.engines);
    return new VariantPair({ example, kotlinFiles, javaFiles, contentOf, languageOnly: manifest.languageOnly ?? [], normalizer });
  }

  static #sources(paths, normalizer) {
    return new Map(paths.map((path) => [path, path.match(sourcePattern)]).filter(([, match]) => match)
      .map(([path, [, module, set, type]]) => [normalizer.normalize(`${module}/src/${set}/${type}`), path]));
  }

  static #testNames(language, source) {
    return new Set([...source.matchAll(testDeclaration[language])].map(([declaration, name]) =>
      comparableTestName(declaration.match(/@DisplayName\("([^"]*)"\)/)?.[1] ?? name.replaceAll('`', ''))));
  }

  #findTestMismatches() {
    const mismatches = [];
    for (const [source, kotlinPath] of this.kotlinSources) {
      if (!source.includes('/src/test/') || !this.javaSources.has(source)) continue;
      const kotlinTests = VariantPair.#testNames('Kotlin', this.contentOf(`kotlin-gradle/${kotlinPath}`));
      const javaTests = VariantPair.#testNames('Java', this.contentOf(`java-maven/${this.javaSources.get(source)}`));
      for (const [language, own, other] of [['Kotlin', kotlinTests, javaTests], ['Java', javaTests, kotlinTests]]) {
        for (const test of own) {
          if (!other.has(test)) mismatches.push({ source, test, language, key: `${source}#${test}` });
        }
      }
    }
    return mismatches;
  }
}
