#!/usr/bin/env bash
# The owner of this repository is written down once, in the root package.json. Moving the repository to
# another organisation then means changing that line and regenerating, nothing else.
# Historical records (ADRs, migration notes, the source repositories in blueprint.yaml) keep the old links.
set -euo pipefail

owner=$(node -p 'require("./package.json").repository.url.match(/github\.com\/([^/]+)\//)[1]')

if git grep -n -F "$owner/" -- \
  ':!package.json' \
  ':!tooling/create-blueprint/package.json' \
  ':!tooling/create-blueprint/catalog.json' \
  ':!README.md' \
  ':!examples/*/blueprint.yaml' \
  ':!examples/*/docs/adr/*' \
  ':!examples/*/docs/*-migration.md' \
  ':!examples/*/docs/*-spike.md' \
  ':!docs/adr/*' \
  ':!scripts/check-owner.sh'; then
  echo "Hard-coded owner \"$owner\" found above: use a relative link, or read it from package.json." >&2
  exit 1
fi
echo "The owner \"$owner\" appears only in package.json and the files generated from it."
