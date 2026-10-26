import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync } from 'node:fs';
import { basename, resolve } from 'node:path';
import { defaultPackage, languages, parseOptions, usage, validationErrors } from './options.mjs';
import { createPrompt } from './prompt.mjs';
import { renameProject } from './rename.mjs';
import { materializeStarter } from './source.mjs';

const catalog = JSON.parse(readFileSync(new URL('../catalog.json', import.meta.url), 'utf8'));

function listing() {
  return catalog.examples
    .map((example) => `  ${example.name.padEnd(30)} ${example.topology.padEnd(9)} ${example.variants.join(', ').padEnd(26)} ${example.summary}`)
    .join('\n');
}

function exampleNamed(name) {
  const example = catalog.examples.find((candidate) => candidate.name === name);
  if (!example) {
    throw new Error(`unknown example "${name}". Known examples:\n${listing()}`);
  }
  return example;
}

async function chooseExample(prompt) {
  const topologies = [...new Set(catalog.examples.map((example) => example.topology))];
  const topology = await prompt.choose(
    'Where does the process engine run?',
    topologies.map((value) => ({ value, label: value === 'embedded' ? 'embedded in the Spring Boot service' : 'remote, in its own engine host or cluster' })),
  );
  const candidates = catalog.examples.filter((example) => example.topology === topology);
  return prompt.choose(
    'Which blueprint?',
    candidates.map((example) => ({ value: example.name, label: `${example.name}: ${example.summary}` })),
  );
}

async function chooseLanguage(prompt, example) {
  const available = Object.entries(languages).filter(([, variant]) => example.variants.includes(variant));
  return prompt.choose(
    'Which language and build tool?',
    available.map(([lang, variant]) => ({ value: lang, label: variant === 'kotlin-gradle' ? 'Kotlin + Gradle' : 'Java + Maven' })),
  );
}

async function completeOptions(options) {
  const needsPrompt = !options.yes && (!options.example || !options.lang || !options.directory);
  const prompt = needsPrompt ? createPrompt() : undefined;
  try {
    const directory = options.directory ?? (prompt ? await prompt.ask('Directory of the new project', 'my-process-service') : 'my-process-service');
    const example = exampleNamed(options.example ?? (prompt ? await chooseExample(prompt) : catalog.examples[0].name));
    const lang = options.lang ?? (prompt ? await chooseLanguage(prompt, example) : example.variants[0] === 'java-maven' ? 'java' : 'kotlin');
    const name = options.name ?? basename(resolve(directory));
    const basePackage = options.package ?? (prompt ? await prompt.ask('Base package', defaultPackage) : defaultPackage);
    return { ...options, directory, example, lang, name, package: basePackage };
  } finally {
    prompt?.close();
  }
}

function initializeGit(target, example, variant, ref) {
  const git = (...args) => spawnSync('git', args, { cwd: target, encoding: 'utf8' });
  if (git('init', '-q', '-b', 'main').status !== 0) {
    return 'git is not installed, the project has no repository yet.';
  }
  git('add', '-A');
  const commit = git('commit', '-q', '-m', `chore: start from the ${example} blueprint (${variant}, ${ref})`);
  return commit.status === 0 ? 'Created the first commit.' : 'Files are staged; commit them once git knows your name and e-mail.';
}

export async function main(argv) {
  const parsed = parseOptions(argv);
  if (parsed.help) {
    console.log(usage);
    return;
  }
  if (parsed.list) {
    console.log(listing());
    return;
  }
  const options = await completeOptions(parsed);
  const variant = languages[options.lang];
  const errors = validationErrors(options);
  if (options.package.startsWith(`${defaultPackage}.`)) {
    errors.push(`--package must not live inside ${defaultPackage}`);
  }
  if (!options.example.variants.includes(variant)) {
    errors.push(`${options.example.name} has no ${variant} variant (available: ${options.example.variants.join(', ')})`);
  }
  if (errors.length > 0) {
    throw new Error(errors.join('\n'));
  }

  const target = resolve(options.directory);
  if (existsSync(target) && readdirSync(target).length > 0) {
    throw new Error(`${target} exists and is not empty`);
  }
  mkdirSync(target, { recursive: true });
  try {
    const { ref } = await materializeStarter(
      { repository: catalog.repository, example: options.example.name, variant, ref: options.ref, from: options.from, fromCheckout: options.fromCheckout },
      target,
    );
    renameProject(target, { fromPackage: defaultPackage, toPackage: options.package, toName: options.name });
    const gitStatus = initializeGit(target, options.example.name, variant, ref);
    console.log(`\nCreated ${options.name} from ${options.example.name} (${variant}, ${ref}) in ${target}.\n${gitStatus}`);
    console.log(`Next: cd ${options.directory} and follow README.md.`);
  } catch (error) {
    rmSync(target, { recursive: true, force: true });
    throw error;
  }
}
