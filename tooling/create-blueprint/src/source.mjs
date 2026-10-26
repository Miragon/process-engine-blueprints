import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

export function cloneUrls(repository) {
  return [`https://github.com/${repository}.git`, `git@github.com:${repository}.git`];
}

function shallowClone(repository, ref) {
  const checkout = join(mkdtempSync(join(tmpdir(), 'miragon-blueprints-')), 'repository');
  for (const url of cloneUrls(repository)) {
    const clone = spawnSync('git', ['clone', '--quiet', '--depth', '1', '--branch', ref, url, checkout], { stdio: ['ignore', 'ignore', 'pipe'], encoding: 'utf8' });
    if (clone.status === 0) {
      return checkout;
    }
    rmSync(checkout, { recursive: true, force: true });
  }
  throw new Error(`could not clone ${repository} at "${ref}" over https or ssh; check the ref and your git access to the repository`);
}

function exportStarter(checkout, example, variant, target) {
  execFileSync(join(checkout, 'tooling', 'starter', 'export.sh'), [example, variant, target], { stdio: ['ignore', 'ignore', 'inherit'] });
  rmSync(join(target, '.git'), { recursive: true, force: true });
}

export function materializeStarter({ repository, example, variant, ref, fromCheckout }, target) {
  if (fromCheckout) {
    exportStarter(resolve(fromCheckout), example, variant, target);
    return { ref: `checkout ${resolve(fromCheckout)}` };
  }
  const checkout = shallowClone(repository, ref);
  try {
    exportStarter(checkout, example, variant, target);
    return { ref };
  } finally {
    rmSync(join(checkout, '..'), { recursive: true, force: true });
  }
}
