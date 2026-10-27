#!/usr/bin/env bash
set -euo pipefail

[ "$#" -eq 1 ] || {
    printf 'Usage: %s <version>\n' "$0" >&2
    exit 1
}

VERSION="$1"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
    printf 'Version must use x.y.z format.\n' >&2
    exit 1
}

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
TEMPLATE="$REPO_ROOT/docs/release-notes-template.md"
OUTPUT_DIR="$REPO_ROOT/release/$VERSION"
OUTPUT="$OUTPUT_DIR/RELEASE_NOTES.md"

[ -f "$TEMPLATE" ] || {
    printf 'Missing release note template: %s\n' "$TEMPLATE" >&2
    exit 1
}

mkdir -p "$OUTPUT_DIR"
TEMP_OUTPUT="$OUTPUT.tmp.$$"
trap 'rm -f "$TEMP_OUTPUT"' EXIT
sed "s/{{version}}/$VERSION/g" "$TEMPLATE" > "$TEMP_OUTPUT"
if grep -Eq '\{\{[^}]+}}' "$TEMP_OUTPUT"; then
    printf 'Release note template has unresolved placeholders.\n' >&2
    exit 1
fi
mv -f "$TEMP_OUTPUT" "$OUTPUT"
trap - EXIT
printf 'Release notes created: %s\n' "$OUTPUT"
