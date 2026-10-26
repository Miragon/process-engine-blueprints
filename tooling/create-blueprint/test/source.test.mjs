import assert from 'node:assert/strict';
import { test } from 'node:test';
import { latestReleaseOf, starterAssetName } from '../src/source.mjs';

const release = (tag, publishedAt, extra = {}) => ({ tag_name: tag, published_at: publishedAt, draft: false, prerelease: false, ...extra });

test('picks the newest published release of the example only', () => {
  const releases = [
    release('cibseven-embedded-v1.0.0', '2026-10-01T00:00:00Z'),
    release('cibseven-embedded-v1.2.0', '2026-10-20T00:00:00Z', { draft: true }),
    release('cibseven-embedded-v1.1.0', '2026-10-10T00:00:00Z'),
    release('cibseven-embedded-remote-v9.0.0', '2026-10-25T00:00:00Z'),
    release('cibseven-remote-v2.0.0', '2026-10-24T00:00:00Z'),
  ];
  assert.equal(latestReleaseOf(releases, 'cibseven-embedded').tag_name, 'cibseven-embedded-v1.1.0');
  assert.equal(latestReleaseOf(releases, 'zeebe-native'), undefined);
});

test('names the starter assets like the release workflow does', () => {
  assert.equal(starterAssetName('zeebe-native', 'java-maven'), 'zeebe-native-java-maven.tar.gz');
});
