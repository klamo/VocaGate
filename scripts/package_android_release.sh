#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

fail() {
    printf 'Release packaging failed: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

read_property() {
    awk -v key="$1" '
        index($0, key "=") == 1 {
            value = substr($0, length(key) + 2)
        }
        END { print value }
    ' "$2"
}

normalize_digest() {
    printf '%s' "$1" | tr -d '[:space:]:' | tr '[:lower:]' '[:upper:]'
}

require_command java
require_command javac
require_command gradle

SDK_DIR=""
if [ -f local.properties ]; then
    SDK_DIR="$(read_property sdk.dir local.properties | sed 's#\\/#/#g; s#\\:#:#g')"
fi
SDK_DIR="${SDK_DIR:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
[ -n "$SDK_DIR" ] && [ -d "$SDK_DIR" ] || fail "Android SDK not found. Set sdk.dir in local.properties, ANDROID_HOME, or ANDROID_SDK_ROOT."

VERSION="$(read_property vocagate.version gradle.properties)"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "Invalid vocagate.version in gradle.properties: $VERSION"

RELEASE_DIR="$REPO_ROOT/release/$VERSION"
FINAL_APK="$RELEASE_DIR/VocaGate-$VERSION-android.apk"
TEMP_APK="$FINAL_APK.tmp.$$"
cleanup() {
    rm -f "$TEMP_APK"
}
trap cleanup EXIT

# Never let a failed attempt leave or reuse an artifact labeled as complete.
rm -f "$FINAL_APK"

SIGNING_FILE="$REPO_ROOT/release-signing.properties"
[ -f "$SIGNING_FILE" ] || fail "Missing required release signing config: $SIGNING_FILE"

for property in storeFile storePassword keyAlias keyPassword expectedCertificateSha256; do
    value="$(read_property "$property" "$SIGNING_FILE")"
    [ -n "$value" ] || fail "Missing required signing property '$property' in $SIGNING_FILE"
done

APKSIGNER="$(find "$SDK_DIR/build-tools" -type f -name apksigner -perm -111 2>/dev/null | sort | tail -n 1)"
[ -n "$APKSIGNER" ] || fail "Could not find apksigner under $SDK_DIR/build-tools"

gradle -Pvocagate.releaseSigningFile="$SIGNING_FILE" :app:assembleRelease

SOURCE_APK="$REPO_ROOT/app/build/outputs/apk/release/app-release.apk"
[ -f "$SOURCE_APK" ] || fail "Gradle completed without the expected APK: $SOURCE_APK"

mkdir -p "$RELEASE_DIR"
cp "$SOURCE_APK" "$TEMP_APK"
VERIFY_OUTPUT="$($APKSIGNER verify --verbose --print-certs "$TEMP_APK" 2>&1)" || {
    printf '%s\n' "$VERIFY_OUTPUT" >&2
    fail "apksigner verification failed"
}
printf '%s\n' "$VERIFY_OUTPUT"

if printf '%s\n' "$VERIFY_OUTPUT" | grep -Eqi 'CN=Android Debug|Android Debug'; then
    fail "Release APK is signed with a debug certificate"
fi

EXPECTED_DIGEST="$(read_property expectedCertificateSha256 "$SIGNING_FILE")"
ACTUAL_DIGEST="$(printf '%s\n' "$VERIFY_OUTPUT" | sed -n 's/.*certificate SHA-256 digest:[[:space:]]*//Ip' | head -n 1)"
[ -n "$ACTUAL_DIGEST" ] || fail "Could not read certificate SHA-256 digest from apksigner output"
[ "$(normalize_digest "$EXPECTED_DIGEST")" = "$(normalize_digest "$ACTUAL_DIGEST")" ] || \
    fail "Release certificate SHA-256 digest does not match expectedCertificateSha256"

mv -f "$TEMP_APK" "$FINAL_APK"
trap - EXIT
printf 'Release APK created: %s\n' "$FINAL_APK"
