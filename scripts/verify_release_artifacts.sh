#!/usr/bin/env bash
set -euo pipefail

[ "$#" -eq 3 ] && [ "$1" = "--platform" ] || {
    printf 'Usage: %s --platform macos-arm64 <release-directory>\n' "$0" >&2
    exit 2
}

PLATFORM="$2"
RELEASE_DIR="$3"

[ "$PLATFORM" = "macos-arm64" ] || {
    printf 'Unsupported host verifier platform: %s\n' "$PLATFORM" >&2
    exit 2
}

VERSION="$(awk -F= '$1 == "vocagate.version" { print $2 }' gradle.properties)"
[ -n "$VERSION" ] || {
    printf 'Missing vocagate.version in gradle.properties\n' >&2
    exit 1
}

DMG="$RELEASE_DIR/VocaGate-$VERSION-macos-arm64.dmg"
NOTES="$RELEASE_DIR/RELEASE_NOTES.md"
[ -f "$DMG" ] || {
    printf 'Missing macOS release artifact: %s\n' "$DMG" >&2
    exit 1
}
[ -f "$NOTES" ] || {
    printf 'Missing release notes: %s\n' "$NOTES" >&2
    exit 1
}

MOUNT_DIR="$(mktemp -d /tmp/vocagate-dmg-verify.XXXXXX)"
MOUNTED=0
cleanup() {
    if [ "$MOUNTED" = "1" ]; then
        hdiutil detach "$MOUNT_DIR" -quiet || true
    fi
    rmdir "$MOUNT_DIR" 2>/dev/null || true
}
trap cleanup EXIT

hdiutil attach -readonly -nobrowse -mountpoint "$MOUNT_DIR" "$DMG" >/dev/null
MOUNTED=1
APP="$MOUNT_DIR/VocaGate.app"
SIDECAR="$APP/Contents/app/sidecar/vocagate-mediapipe"
MODEL="$APP/Contents/app/sidecar/models/face_landmarker.task"
NOTICES="$APP/Contents/app/THIRD_PARTY_NOTICES.txt"

for required in "$APP" "$SIDECAR" "$MODEL" "$NOTICES"; do
    [ -e "$required" ] || {
        printf 'DMG is missing required payload: %s\n' "$required" >&2
        exit 1
    }
done

codesign --verify --deep --strict --verbose=2 "$APP"
"$SIDECAR" --model "$MODEL" --healthcheck | grep -F '"status":"ok"' >/dev/null
shasum -a 256 "$DMG" > "$RELEASE_DIR/VocaGate-$VERSION-macos-arm64.dmg.sha256"
printf 'macOS release artifact verified: %s\n' "$DMG"
