#!/usr/bin/env bash
# apply-rules.sh <src> <rules.yml>...: each rule must match exactly `metadata: { expect: N }` times, then all apply.
# Scans from <src>, so `files:` globs in rules are relative to the upstream root.
set -eo pipefail
SG=${SG:-npx -y -p @ast-grep/cli@0.45.3 ast-grep}
src=$1; shift
for f in "$@"; do
  f=$(realpath "$f")
  hits=$(cd "$src" && $SG scan -r "$f" . --json=stream | jq -r .ruleId)
  grep -oP '^id: \K\S+|expect: \K\d+' "$f" | paste - - | while read -r id want; do
    n=$(grep -cx "$id" <<<"$hits" || true)
    [ "$n" = "$want" ] || { echo "$f: rule $id matched $n times, expected $want; update it for this upstream"; exit 1; }
  done
  (cd "$src" && $SG scan -r "$f" . -U >/dev/null)
  echo "applied $f"
done
