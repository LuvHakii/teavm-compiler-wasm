#!/usr/bin/env bash
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$REPO/teavm-javac"
OUT="$(realpath -m "${1:-$REPO/out}")"
BINARYEN_VER=133

want="$(git -C "$REPO" rev-parse :teavm-javac) $(cat "$REPO"/patches/* "$REPO"/scripts/apply-rules.sh $(find "$REPO"/overlay -type f | sort) | git hash-object --stdin)"
if [ "$(cat "$SRC/.patched" 2>/dev/null)" != "$want" ]; then
  if [ -e "$SRC/.git" ]; then git -C "$SRC" reset -q --hard && git -C "$SRC" clean -fdq; fi
  git -C "$REPO" submodule update --init --depth 1 teavm-javac
  "$REPO/scripts/apply-rules.sh" "$SRC" "$REPO"/patches/*.yml
  for patch in "$REPO"/patches/*.patch; do
    applied=$(git -C "$SRC" apply -v "$patch" 2>&1) || { echo "$applied"; exit 1; }
    echo "$applied"
    if grep -q offset <<<"$applied"; then echo "$patch applied at an offset, regenerate it"; exit 1; fi
  done
  cp -r "$REPO/overlay/." "$SRC/"
  echo "$want" > "$SRC/.patched"
fi

WASM_OPT="${WASM_OPT:-$(command -v wasm-opt || true)}"
if [ -z "$WASM_OPT" ]; then
  dir="$REPO/.cache/binaryen-version_$BINARYEN_VER"
  if [ ! -x "$dir/bin/wasm-opt" ]; then
    mkdir -p "$REPO/.cache"
    curl -fsSL "https://github.com/WebAssembly/binaryen/releases/download/version_${BINARYEN_VER}/binaryen-version_${BINARYEN_VER}-x86_64-linux.tar.gz" | tar xz -C "$REPO/.cache"
  fi
  WASM_OPT="$dir/bin/wasm-opt"
fi

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JDK_SRC_ZIP="${JDK_SRC_ZIP:-$(dirname "$(dirname "$(readlink -f "$(command -v "$JAVA")")")")/lib/src.zip}"

cd "$SRC"
# shellcheck disable=SC2086
./gradlew ${GRADLE_ARGS:-} :compiler:exportAssets -PassetsDir="$OUT" -PwasmOpt="$WASM_OPT" \
  -PapplyRules="$REPO/scripts/apply-rules.sh" -PjdkSrcZip="$JDK_SRC_ZIP"
"$REPO/scripts/jdk-src.sh" "$JDK_SRC_ZIP" "$OUT/jdk-src.tar"
