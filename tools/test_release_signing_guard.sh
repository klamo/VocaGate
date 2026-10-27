#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT_DIR"

command -v gradle >/dev/null 2>&1 || {
    printf 'Required command not found: gradle\n' >&2
    exit 1
}

if [ -e release-signing.properties ]; then
    printf 'Credentialless signing guard test requires no release-signing.properties file.\n' >&2
    exit 1
fi

RELEASE_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
rm -f "$RELEASE_APK"

assert_credentialless_release_guard() {
    local task="$1"
    if gradle "$task"; then
        printf 'Expected credentialless %s to fail.\n' "$task" >&2
        exit 1
    fi

    if [ -e "$RELEASE_APK" ]; then
        printf 'Credentialless %s produced a release APK: %s\n' "$task" "$RELEASE_APK" >&2
        exit 1
    fi
}

assert_credentialless_release_guard :app:assemble
assert_credentialless_release_guard :app:build

gradle :app:assembleDebug
printf 'Release signing guard test passed.\n'
