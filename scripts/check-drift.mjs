import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parse, stringify } from 'yaml';
import { loadExamples, repoRoot } from './lib/examples.mjs';

const driftFile = join(repoRoot, 'drift.yml');
const trackedOutsideDrift = /(^|\/)(gradlew(\.bat)?|mvnw(\.cmd)?|gradle-wrapper\.(jar|properties)|maven-wrapper\.properties|package(-lock)?\.json|docker-compose(\.[a-z]+)?\.yml|dependabot\.yml|pom\.xml|[^/]*\.gradle\.kts|libs\.versions\.toml)$|^\.github\/workflows\/|\/src\/(main|test)\/(kotlin|java)\//;

function contentHash(example, file) {
  const path = join(repoRoot, 'examples', example, file);
  return existsSync(path) ? createHash('sha256').update(readFileSync(path)).digest('hex') : null;
}

export function violations(rules, hashOf) {
  const found = [];
  for (const rule of rules) {
    for (const file of rule.files) {
      const hashes = rule.examples.map((example) => ({ example, hash: hashOf(example, file) }));
      const missing = hashes.filter(({ hash }) => hash === null).map(({ example }) => example);
      const distinct = new Set(hashes.filter(({ hash }) => hash !== null).map(({ hash }) => hash));
      if (missing.length > 0) {
        found.push(`${rule.name}: ${file} is missing in ${missing.join(', ')}`);
      } else if (distinct.size > 1) {
        found.push(`${rule.name}: ${file} differs between ${rule.examples.join(', ')}`);
      }
    }
  }
  return found;
}

function trackedFiles(example) {
  return execFileSync('git', ['ls-files'], { cwd: join(repoRoot, 'examples', example), encoding: 'utf8' })
    .split('\n')
    .filter((file) => file && !trackedOutsideDrift.test(file));
}

function proposedRules(exampleNames) {
  const sharedBy = new Map();
  for (const example of exampleNames) {
    for (const file of trackedFiles(example)) {
      const key = `${file}\0${contentHash(example, file)}`;
      sharedBy.set(key, [...(sharedBy.get(key) ?? []), example]);
    }
  }
  const filesByExampleSet = new Map();
  for (const [key, examples] of sharedBy) {
    if (examples.length >= 2) {
      const set = examples.join(',');
      filesByExampleSet.set(set, [...(filesByExampleSet.get(set) ?? []), key.split('\0')[0]]);
    }
  }
  return [...filesByExampleSet]
    .sort(([left], [right]) => right.split(',').length - left.split(',').length || left.localeCompare(right))
    .map(([set, files]) => ({ name: set.split(',').length === exampleNames.length ? 'all examples' : set.split(',').join(' + '), examples: set.split(','), files: files.sort() }));
}

function loadRules() {
  return existsSync(driftFile) ? (parse(readFileSync(driftFile, 'utf8'))?.identical ?? []) : [];
}

if (process.argv.includes('--propose')) {
  console.log(stringify({ identical: proposedRules(loadExamples().map((example) => example.name)) }, { lineWidth: 0 }));
} else if (import.meta.url === `file://${process.argv[1]}`) {
  const rules = loadRules();
  const found = violations(rules, contentHash);
  if (found.length > 0) {
    console.error(`The examples drifted apart (see drift.yml):\n  ${found.join('\n  ')}`);
    console.error('Apply the change to every example of the rule, or move the file out of the rule with a reason.');
    process.exit(1);
  }
  console.log(`${rules.length} drift rules hold.`);
}
