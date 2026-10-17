#!/usr/bin/env bash
set -euo pipefail

ZIP="$(realpath "${1:?usage: jdk-src.sh <src.zip> <out.tar>}")"
OUT="$(realpath -m "${2:?usage: jdk-src.sh <src.zip> <out.tar>}")"
[ -f "$ZIP" ] || { echo "JDK sources not found at $ZIP" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

unzip -q "$ZIP" 'java.base/java/*' 'java.base/javax/*' -d "$WORK"
cd "$WORK/java.base"
find . -name package-info.java -delete
mkdir -p "$(dirname "$OUT")"
find java javax -name '*.java' | LC_ALL=C sort | tar cf "$OUT" --owner=0 --group=0 --numeric-owner --mtime='1970-01-01' -T -
echo "jdk-src.tar: $(tar tf "$OUT" | wc -l) files, $(stat -c %s "$OUT") bytes"
