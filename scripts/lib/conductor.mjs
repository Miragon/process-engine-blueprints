import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parse, stringify } from 'smol-toml';

export function conductorSettings(examples) {
  const run = {};
  const archives = [];
  for (const example of examples) {
    const settingsFile = join(example.dir, '.conductor', 'settings.toml');
    if (!existsSync(settingsFile)) {
      continue;
    }
    const scripts = parse(readFileSync(settingsFile, 'utf8')).scripts ?? {};
    const inExample = (command) => `cd examples/${example.name} && ${command.trim()}`;
    if (scripts.setup) {
      run[`${example.name}:setup`] = { command: inExample(withoutHookInstall(scripts.setup)), icon: 'download' };
    }
    for (const [target, settings] of Object.entries(scripts.run ?? {})) {
      run[`${example.name}:${target}`] = { ...settings, command: inExample(settings.command) };
    }
    if (scripts.archive) {
      archives.push(`(${inExample(scripts.archive)})`);
    }
  }
  return {
    $schema: 'https://conductor.build/schemas/settings.repo.schema.json',
    scripts: {
      run_mode: 'nonconcurrent',
      setup: 'npm ci\nnpm run hooks:install\n',
      archive: `${archives.join('\n')}\n`,
      run,
    },
  };
}

function withoutHookInstall(setup) {
  return setup
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line !== '' && !line.includes('hooks:install'))
    .join(' && ');
}

export function renderConductor(header, settings) {
  return `${header}\n${stringify(settings)}\n`;
}
