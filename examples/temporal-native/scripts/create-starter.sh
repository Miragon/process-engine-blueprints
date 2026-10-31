#!/usr/bin/env bash
# Turns a checkout of this example into a plain Spring Boot project, in place: the build moves from
# kotlin-gradle/ to the repo root and the paths in the workflows, Dependabot, Conductor settings and
# docs follow. Nothing is committed; review the result with `git status`.
#
#   scripts/create-starter.sh kotlin-gradle --flat
set -euo pipefail

[[ "${1:-}" == kotlin-gradle && "${2:-}" == --flat ]] || { echo "usage: $0 kotlin-gradle --flat" >&2; exit 1; }
kept=kotlin-gradle

cd "$(git rev-parse --show-toplevel)"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "The working tree has uncommitted changes. Commit or discard them first." >&2
  exit 1
fi

rewrite() {
  local file=$1 rewritten
  shift
  rewritten=$(mktemp)
  "$@" "$file" > "$rewritten"
  cat "$rewritten" > "$file"
  rm "$rewritten"
}

append_the_variant_readme_to_the_root_readme() {
  local merged
  merged=$(mktemp)
  {
    sed '/^## .*Contributing/,$d' README.md
    sed -e '1,/^## /{/^## /!d;}' -e 's|\.\./||g' "$kept/README.md"
    echo
    sed -n '/^## .*Contributing/,$p' README.md
  } > "$merged"
  cat "$merged" > README.md
  rm "$merged"
  git rm -q -f "$kept/README.md"
}

git rm -q scripts/create-starter.sh
append_the_variant_readme_to_the_root_readme

git ls-files -- '*.md' '*.yml' '*.toml' '*.json' '*.kts' '*.xml' .gitignore ':!docs/adr' | while read -r file; do
  rewrite "$file" sed \
    -e "/working-directory: $kept\$/d" \
    -e "s|cd $kept && ||g" \
    -e "s|\"/$kept\"|\"/\"|g" \
    -e "s|\`$kept/\`|the repo root|g" \
    -e "s|$kept/||g"
done

git ls-files -- "$kept" | cut -d/ -f2 | sort -u | while read -r entry; do
  git mv "$kept/$entry" "$entry"
done
find "$kept" -type d -empty -delete
git add -A

echo "Created the $kept starter. Review it with: git status"
