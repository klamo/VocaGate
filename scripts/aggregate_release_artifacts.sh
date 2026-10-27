#!/usr/bin/env bash
set -euo pipefail

[ "$#" -eq 1 ] || {
    printf 'Usage: %s <release-directory>\n' "$0" >&2
    exit 2
}

RELEASE_DIR="$1"
VERSION="$(awk -F= '$1 == "vocagate.version" { print $2 }' gradle.properties)"
for artifact in \
    "VocaGate-$VERSION-android.apk" \
    "VocaGate-$VERSION-macos-arm64.dmg" \
    "VocaGate-$VERSION-windows-x64.zip" \
    "RELEASE_NOTES.md"; do
    [ -f "$RELEASE_DIR/$artifact" ] || {
        printf 'Missing release input: %s\n' "$RELEASE_DIR/$artifact" >&2
        exit 1
    }
done

if [ -f "$RELEASE_DIR/VocaGate-$VERSION-windows-x64.exe" ]; then
    printf 'Including optional Windows EXE installer in checksum manifest.\n'
fi

(
    cd "$RELEASE_DIR"
    find . -maxdepth 1 -type f \( -name 'VocaGate-*.apk' -o -name 'VocaGate-*.dmg' -o -name 'VocaGate-*.zip' -o -name 'VocaGate-*.exe' \) \
        -print0 | sort -z | xargs -0 shasum -a 256 > SHA256SUMS.txt
)
printf 'Release checksum manifest created: %s/SHA256SUMS.txt\n' "$RELEASE_DIR"
