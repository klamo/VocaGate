#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

find_gradle() {
  if [[ -x "$ROOT_DIR/gradlew" ]]; then
    echo "$ROOT_DIR/gradlew"
    return
  fi

  if command -v gradle >/dev/null 2>&1; then
    command -v gradle
    return
  fi

  return 1
}

GRADLE_CMD="$(find_gradle)" || {
  echo "Error: gradle not found and ./gradlew is missing." >&2
  exit 1
}

cd "$ROOT_DIR"
"$GRADLE_CMD" :desktop:run
