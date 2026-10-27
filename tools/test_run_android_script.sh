#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

help_output="$("$ROOT_DIR/scripts/run_android.sh" --help)"

if [[ "$help_output" != *"Usage: scripts/run_android.sh"* ]]; then
  echo "Expected help output to include usage line" >&2
  exit 1
fi

if [[ "$help_output" != *"--no-start"* ]]; then
  echo "Expected help output to document --no-start" >&2
  exit 1
fi

echo "run_android.sh smoke test passed"
