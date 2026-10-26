import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

export function starterAssetName(example, variant) {
  return `${example}-${variant}.tar.gz`;
}

function githubToken() {
  if (process.env.GITHUB_TOKEN || process.env.GH_TOKEN) {
    return process.env.GITHUB_TOKEN || process.env.GH_TOKEN;
  }
  const gh = spawnSync('gh', ['auth', 'token'], { encoding: 'utf8' });
  return gh.status === 0 ? gh.stdout.trim() : undefined;
}

async function githubApi(url, token, accept = 'application/vnd.github+json') {
  const response = await fetch(url, { headers: { accept, ...(token ? { authorization: `Bearer ${token}` } : {}) } });
  if (!response.ok) {
    const hint = response.status === 404 && !token ? ' (a private repository needs GITHUB_TOKEN or `gh auth login`)' : '';
    throw new Error(`GitHub answered ${response.status} for ${url}${hint}`);
  }
  return response;
}

export function latestReleaseOf(releases, example) {
  return releases
    .filter((release) => !release.draft && !release.prerelease && release.tag_name.startsWith(`${example}-v`))
    .sort((left, right) => new Date(right.published_at) - new Date(left.published_at))[0];
}

async function downloadStarter(repository, example, variant, ref) {
  const token = githubToken();
  const api = `https://api.github.com/repos/${repository}`;
  const release = ref
    ? await (await githubApi(`${api}/releases/tags/${ref}`, token)).json()
    : latestReleaseOf(await (await githubApi(`${api}/releases?per_page=100`, token)).json(), example);
  if (!release) {
    throw new Error(`${repository} has no release of ${example} yet`);
  }
  const asset = release.assets.find((candidate) => candidate.name === starterAssetName(example, variant));
  if (!asset) {
    throw new Error(`release ${release.tag_name} has no ${starterAssetName(example, variant)}`);
  }
  const archive = await (await githubApi(asset.url, token, 'application/octet-stream')).arrayBuffer();
  const file = join(mkdtempSync(join(tmpdir(), 'miragon-blueprint-')), asset.name);
  writeFileSync(file, Buffer.from(archive));
  return { archive: file, ref: release.tag_name };
}

function extract(archive, target) {
  execFileSync('tar', ['-xzf', archive, '-C', target], { stdio: 'inherit' });
}

export async function materializeStarter({ repository, example, variant, ref, from, fromCheckout }, target) {
  if (fromCheckout) {
    execFileSync(join(resolve(fromCheckout), 'tooling', 'starter', 'export.sh'), [example, variant, target], { stdio: 'inherit' });
    rmSync(join(target, '.git'), { recursive: true, force: true });
    return { ref: `checkout ${resolve(fromCheckout)}` };
  }
  if (from) {
    extract(resolve(from), target);
    return { ref: from };
  }
  const { archive, ref: resolvedRef } = await downloadStarter(repository, example, variant, ref);
  extract(archive, target);
  return { ref: resolvedRef };
}
