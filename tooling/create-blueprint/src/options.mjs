import { parseArgs } from 'node:util';

export const languages = { kotlin: 'kotlin-gradle', java: 'java-maven' };
export const defaultPackage = 'io.miragon.blueprint';

const packagePattern = /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$/;
const projectNamePattern = /^[a-z][a-z0-9-]*$/;

export const usage = `Usage: npm create miragon-blueprint@latest [directory] -- [options]

Options:
  --example <name>     the blueprint, e.g. cibseven-embedded (see --list)
  --lang <kotlin|java> Kotlin + Gradle or Java + Maven
  --name <name>        project name (default: the directory name)
  --package <name>     base package (default: ${defaultPackage})
  --ref <tag>          release tag (default: the latest release of the example)
  --from <archive>     use a local starter archive (.tar.gz) instead of downloading one
  --from-checkout <dir> export the starter from a local checkout of the monorepo
  --list               list the blueprints and exit
  --yes                take the defaults instead of asking
  --help               show this help`;

export function parseOptions(argv) {
  const { values, positionals } = parseArgs({
    args: argv,
    allowPositionals: true,
    options: {
      example: { type: 'string' },
      lang: { type: 'string' },
      name: { type: 'string' },
      package: { type: 'string' },
      ref: { type: 'string' },
      from: { type: 'string' },
      'from-checkout': { type: 'string' },
      list: { type: 'boolean', default: false },
      yes: { type: 'boolean', default: false },
      help: { type: 'boolean', default: false },
    },
  });
  return { ...values, directory: positionals[0], fromCheckout: values['from-checkout'] };
}

export function validationErrors({ lang, name, package: basePackage }) {
  const errors = [];
  if (lang && !languages[lang]) {
    errors.push(`--lang must be one of ${Object.keys(languages).join(', ')}`);
  }
  if (name && !projectNamePattern.test(name)) {
    errors.push('--name must be lower-case letters, digits and dashes, starting with a letter');
  }
  if (basePackage && !packagePattern.test(basePackage)) {
    errors.push('--package must be a lower-case Java package name, e.g. com.acme.leasing');
  }
  return errors;
}
