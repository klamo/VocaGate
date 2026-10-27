#!/usr/bin/env bash
set -euo pipefail

[ "$#" -eq 2 ] || {
    printf 'Usage: %s <macos-arm64|windows-x64> <staging-directory>\n' "$0" >&2
    exit 2
}

TARGET="$1"
STAGING="$2"

case "$TARGET" in
    macos-arm64|windows-x64) ;;
    *)
        printf 'Unsupported target: %s\n' "$TARGET" >&2
        exit 2
        ;;
esac

require_path() {
    [ -e "$1" ] || {
        printf 'Missing required staged path: %s\n' "$1" >&2
        exit 1
    }
}

require_path "$STAGING/app/lib"
require_path "$STAGING/app/sidecar"
require_path "$STAGING/app/sidecar/models/face_landmarker.task"
require_path "$STAGING/app/THIRD_PARTY_NOTICES.txt"

if find "$STAGING" -type f \( -name '*.jks' -o -name '*signing*' -o -name 'pip' -o -name 'pip3' -o -name 'setup_mediapipe_sidecar.*' \) -print -quit | grep -q .; then
    printf 'Staging contains a development or signing file.\n' >&2
    exit 1
fi

if find "$STAGING" -type d -name '.venv-mediapipe' -print -quit | grep -q .; then
    printf 'Staging contains a Python virtual environment.\n' >&2
    exit 1
fi

case "$TARGET" in
    macos-arm64)
        if find "$STAGING/app/lib" -type f \( -iname '*windows*' -o -iname '*.dll' -o -iname '*macosx-x86_64*' \) -print -quit | grep -q .; then
            printf 'macOS ARM staging contains another platform native library.\n' >&2
            exit 1
        fi
        ;;
    windows-x64)
        if find "$STAGING/app/lib" -type f \( -iname '*macos*' -o -iname '*.dylib' \) -print -quit | grep -q .; then
            printf 'Windows staging contains a macOS native library.\n' >&2
            exit 1
        fi
        ;;
esac

printf 'Desktop release staging is valid: %s\n' "$STAGING"
