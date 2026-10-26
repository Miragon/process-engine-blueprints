import { existsSync, readdirSync, readFileSync, renameSync, rmdirSync, mkdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, sep } from 'node:path';

const skippedDirectories = new Set(['.git', 'node_modules', 'build', 'target', '.gradle']);

function* files(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory() && !skippedDirectories.has(entry.name)) {
      yield* files(path);
    } else if (entry.isFile()) {
      yield path;
    }
  }
}

function* directories(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory() && !skippedDirectories.has(entry.name)) {
      const path = join(dir, entry.name);
      yield path;
      yield* directories(path);
    }
  }
}

function isText(buffer) {
  return !buffer.subarray(0, 8000).includes(0);
}

export function projectNameOf(root) {
  const settings = join(root, 'settings.gradle.kts');
  if (existsSync(settings)) {
    return readFileSync(settings, 'utf8').match(/rootProject\.name\s*=\s*"([^"]+)"/)?.[1];
  }
  const pom = join(root, 'pom.xml');
  if (existsSync(pom)) {
    const withoutParent = readFileSync(pom, 'utf8').replace(/<parent>[\s\S]*?<\/parent>/, '');
    return withoutParent.match(/<artifactId>([^<]+)<\/artifactId>/)?.[1];
  }
  return undefined;
}

export function replaceInTextFiles(root, replacements) {
  let changedFiles = 0;
  for (const file of files(root)) {
    const buffer = readFileSync(file);
    if (!isText(buffer)) {
      continue;
    }
    const original = buffer.toString('utf8');
    const replaced = replacements.reduce((text, [from, to]) => text.replaceAll(from, to), original);
    if (replaced !== original) {
      writeFileSync(file, replaced);
      changedFiles += 1;
    }
  }
  return changedFiles;
}

function removeEmptyParents(dir, stopAt) {
  let current = dir;
  while (current !== stopAt && existsSync(current) && readdirSync(current).length === 0) {
    rmdirSync(current);
    current = dirname(current);
  }
}

export function movePackageDirectories(root, fromPackage, toPackage) {
  const fromPath = fromPackage.split('.').join(sep);
  const toPath = toPackage.split('.').join(sep);
  const packageRoots = [...directories(root)].filter((dir) => dir.endsWith(`${sep}${fromPath}`) && statSync(dir).isDirectory());
  for (const packageRoot of packageRoots) {
    const sourceRoot = packageRoot.slice(0, -fromPath.length);
    const target = join(sourceRoot, toPath);
    mkdirSync(dirname(target), { recursive: true });
    renameSync(packageRoot, target);
    removeEmptyParents(dirname(packageRoot), sourceRoot.replace(/[\\/]$/, ''));
  }
  return packageRoots.length;
}

export function renameProject(root, { fromPackage, toPackage, toName }) {
  const replacements = [];
  if (toPackage && toPackage !== fromPackage) {
    replacements.push([fromPackage, toPackage], [fromPackage.replaceAll('.', '/'), toPackage.replaceAll('.', '/')]);
    movePackageDirectories(root, fromPackage, toPackage);
  }
  const fromName = projectNameOf(root);
  if (toName && fromName && toName !== fromName) {
    replacements.push([fromName, toName]);
  }
  return replaceInTextFiles(root, replacements);
}
