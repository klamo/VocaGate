#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

require_command() {
    command -v "$1" >/dev/null 2>&1 || {
        printf 'Required command not found: %s\n' "$1" >&2
        exit 1
    }
}

read_property() {
    awk -F= -v key="$1" '$1 == key { print substr($0, length(key) + 2) }' "$2"
}

[ "$(uname -s)" = "Darwin" ] && [ "$(uname -m)" = "arm64" ] || {
    printf 'macOS ARM64 packaging must run on an Apple Silicon Mac.\n' >&2
    exit 2
}

for command in java jdeps jlink jpackage gradle plutil codesign; do
    require_command "$command"
done

VERSION="$(read_property vocagate.version gradle.properties)"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
    printf 'Invalid vocagate.version: %s\n' "$VERSION" >&2
    exit 1
}
MAC_PACKAGE_VERSION="$VERSION"
if [[ "$MAC_PACKAGE_VERSION" == 0.* ]]; then
    MAC_PACKAGE_VERSION="1.${MAC_PACKAGE_VERSION#0.}"
fi

TARGET="macos-arm64"
STAGING="$REPO_ROOT/desktop/build/release-staging/$TARGET"
BUILD_ROOT="$REPO_ROOT/desktop/build/release-packaging/$TARGET"
RUNTIME="$BUILD_ROOT/runtime"
APP_IMAGE_DESTINATION="$BUILD_ROOT/app-image"
DMG_DESTINATION="$BUILD_ROOT/dmg"
RELEASE_DIR="$REPO_ROOT/release/$VERSION"
FINAL_DMG="$RELEASE_DIR/VocaGate-$VERSION-macos-arm64.dmg"

rm -rf "$BUILD_ROOT"
mkdir -p "$APP_IMAGE_DESTINATION" "$DMG_DESTINATION" "$RELEASE_DIR"

"$SCRIPT_DIR/prepare_mediapipe_sidecar.sh" "$TARGET"
gradle :desktop:stageReleaseApp -PtargetPlatform="$TARGET"

SIDECAR="$STAGING/app/sidecar/vocagate-mediapipe"
MODEL="$STAGING/app/sidecar/models/face_landmarker.task"
"$SIDECAR" --model "$MODEL" --healthcheck | grep -F '"status":"ok"' >/dev/null

MODULES="$(jdeps --ignore-missing-deps --multi-release 17 --print-module-deps \
    --class-path "$STAGING/app/lib/*" "$STAGING/app/lib/desktop.jar")"
case ",$MODULES," in
    *,jdk.unsupported,*) ;;
    *) MODULES="$MODULES,jdk.unsupported" ;;
esac

jlink --add-modules "$MODULES" --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "$RUNTIME"

jpackage --type app-image \
    --name VocaGate \
    --app-version "$MAC_PACKAGE_VERSION" \
    --vendor VocaGate \
    --input "$STAGING/app" \
    --main-jar lib/desktop.jar \
    --main-class com.vocagate.desktop.app.DesktopCompanionApp \
    --runtime-image "$RUNTIME" \
    --mac-package-identifier com.vocagate.desktop \
    --java-options "-Dapple.awt.application.name=VocaGate" \
    --dest "$APP_IMAGE_DESTINATION"

APP_IMAGE="$APP_IMAGE_DESTINATION/VocaGate.app"
[ -d "$APP_IMAGE" ] || {
    printf 'jpackage did not create the expected app image: %s\n' "$APP_IMAGE" >&2
    exit 1
}
plutil -replace NSCameraUsageDescription -string "VocaGate uses the camera to detect speech-related mouth movement locally on this Mac." "$APP_IMAGE/Contents/Info.plist"

# jpackage signs the app image before the permission description is changed.
# Restore the resource seal after all bundle edits. This is an ad-hoc signature,
# not a Developer ID signature or Apple notarization.
codesign --force --sign - --timestamp=none "$APP_IMAGE"
codesign --verify --deep --strict --verbose=2 "$APP_IMAGE"

jpackage --type dmg --name VocaGate --app-image "$APP_IMAGE" --dest "$DMG_DESTINATION"
GENERATED_DMG="$(find "$DMG_DESTINATION" -maxdepth 1 -type f -name '*.dmg' -print -quit)"
[ -n "$GENERATED_DMG" ] || {
    printf 'jpackage did not create a DMG.\n' >&2
    exit 1
}

cp "$GENERATED_DMG" "$FINAL_DMG"
"$SCRIPT_DIR/prepare_release_notes.sh" "$VERSION"
printf 'macOS release DMG created: %s\n' "$FINAL_DMG"
