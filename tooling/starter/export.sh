#!/usr/bin/env bash
# Exports one example in one variant as a flat, self-contained project with a fresh git history.
#
#   tooling/starter/export.sh <example> <kotlin-gradle|java-maven> <target-dir>
#
# Examples that carry both variants are turned into a single-variant starter by their own
# scripts/create-starter.sh --flat; single-variant examples are copied as they are.
set -euo pipefail

usage() { echo "usage: $0 <example> <kotlin-gradle|java-maven> <target-dir>" >&2; exit 1; }
[[ $# -eq 3 ]] || usage

example=$1 variant=$2 target=$3
root="$(cd "$(dirname "$0")/../.." && pwd)"
source_dir="$root/examples/$example"

[[ -f "$source_dir/blueprint.yaml" ]] || { echo "unknown example: $example" >&2; exit 1; }
grep -qE "^  $variant:" "$source_dir/blueprint.yaml" || { echo "$example has no $variant variant" >&2; exit 1; }
[[ ! -e "$target" || -z "$(ls -A "$target")" ]] || { echo "$target is not empty" >&2; exit 1; }

mkdir -p "$target"
target="$(cd "$target" && pwd)"

git -C "$source_dir" ls-files -z --cached --others --exclude-standard -- . \
  | (cd "$source_dir" && tar --null -T - -cf -) \
  | tar -xf - -C "$target"
rm -f "$target/blueprint.yaml"

commit_all() {
  git -C "$target" add -A
  git -C "$target" -c user.name="process-engine-blueprints" -c user.email="blueprints@miragon.io" \
    -c commit.gpgsign=false commit -q -m "$1"
}

git -C "$target" init -q -b main
commit_all "chore: export $example"

if [[ -x "$target/scripts/create-starter.sh" ]]; then
  (cd "$target" && scripts/create-starter.sh "$variant" --flat > /dev/null)
fi

rm -rf "$target/.git"
git -C "$target" init -q -b main
commit_all "chore: start from the $example blueprint ($variant)"

echo "Exported $example ($variant) to $target"
