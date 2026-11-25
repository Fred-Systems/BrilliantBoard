#!/usr/bin/env bash
# Fetches the official Stockfish Android binaries into app/src/main/jniLibs.
# Offline environments are supported: an existing binary is preserved and a missing
# download only prints a warning so local unit tests can still run.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RELEASES="https://github.com/official-stockfish/Stockfish/releases/latest/download"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fetch() {
  local abi="$1" asset="$2"
  local target="$ROOT/app/src/main/jniLibs/$abi/libstockfish.so"
  if [[ -s "$target" ]]; then
    echo "Stockfish already present for $abi"
    return 0
  fi
  mkdir -p "$(dirname "$target")"
  if curl -fL --retry 2 --max-time 120 "$RELEASES/$asset" -o "$TMP/$asset"; then
    tar -xzf "$TMP/$asset" -C "$TMP" 2>/dev/null || true
    local binary
    binary="$(find "$TMP" -maxdepth 2 -type f -name 'stockfish*' ! -name "$asset" | head -n1)"
    if [[ -n "$binary" ]]; then
      cp "$binary" "$target"
      chmod +x "$target"
      echo "Installed $target"
      return 0
    fi
  fi
  echo "WARNING: could not fetch Stockfish for $abi (offline fallback)" >&2
  return 0
}

fetch arm64-v8a stockfish-android-arm64-universal.tar.gz

# Official Stockfish releases no longer publish an Android x86_64 binary. The app keeps
# the x86_64 jniLibs slot for a user-supplied build, but engine features are optional there.
echo "x86_64: no official Stockfish Android asset; skipping (engine disabled on that ABI)"
