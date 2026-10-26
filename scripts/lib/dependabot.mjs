import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parse, stringify } from 'yaml';

const monthly = { interval: 'monthly' };

export function dependabotConfig(examples) {
  const multiEcosystemGroups = {};
  const updates = [toolingUpdate(), githubActionsUpdate()];
  for (const example of examples) {
    const source = parse(readFileSync(join(example.dir, '.github', 'dependabot.yml'), 'utf8'));
    for (const [group, settings] of Object.entries(source['multi-ecosystem-groups'] ?? {})) {
      multiEcosystemGroups[`${example.name}-${group}`] = settings;
    }
    updates.push(
      ...source.updates
        .filter((update) => update['package-ecosystem'] !== 'github-actions')
        .map((update) => relocatedUpdate(example, update)),
    );
  }
  return { version: 2, 'multi-ecosystem-groups': multiEcosystemGroups, updates };
}

function relocatedUpdate(example, update) {
  const relocated = { ...update, directory: `/examples/${example.name}${update.directory === '/' ? '' : update.directory}` };
  if (update['multi-ecosystem-group']) {
    relocated['multi-ecosystem-group'] = `${example.name}-${update['multi-ecosystem-group']}`;
  }
  if (update.groups) {
    relocated.groups = Object.fromEntries(Object.entries(update.groups).map(([group, settings]) => [`${example.name}-${group}`, settings]));
  }
  if (update['commit-message']) {
    relocated['commit-message'] = { ...update['commit-message'], prefix: update['commit-message'].prefix ?? 'build' };
  }
  relocated.labels = [...new Set([...(update.labels ?? []), `example:${example.name}`])];
  return relocated;
}

function toolingUpdate() {
  return {
    'package-ecosystem': 'npm',
    directory: '/',
    schedule: monthly,
    labels: ['dependencies', 'tooling'],
    'commit-message': { prefix: 'build', include: 'scope' },
    groups: {
      'monorepo-tooling': { 'applies-to': 'version-updates', patterns: ['*'] },
      'monorepo-tooling-security': { 'applies-to': 'security-updates', patterns: ['*'] },
    },
  };
}

function githubActionsUpdate() {
  return {
    'package-ecosystem': 'github-actions',
    directories: ['/', '/examples/*'],
    schedule: monthly,
    labels: ['dependencies'],
    'commit-message': { prefix: 'build', include: 'scope' },
    groups: {
      'github-actions': { 'applies-to': 'version-updates', patterns: ['*'] },
      'github-actions-security': { 'applies-to': 'security-updates', patterns: ['*'] },
    },
  };
}

export function renderDependabot(header, config) {
  return `${header}\n${stringify(config, { lineWidth: 0, aliasDuplicateObjects: false })}`;
}
