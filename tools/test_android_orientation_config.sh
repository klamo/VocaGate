#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT_DIR/app/src/main/AndroidManifest.xml"

if grep -q 'android:screenOrientation="portrait"' "$MANIFEST"; then
  echo "Android activities should no longer be locked to portrait" >&2
  exit 1
fi

if ! grep -q 'android:screenOrientation="fullSensor"' "$MANIFEST"; then
  echo "MainActivity should follow sensor orientation" >&2
  exit 1
fi

if ! grep -q 'android:configChanges="keyboardHidden|orientation|screenSize"' "$MANIFEST"; then
  echo "MainActivity should handle orientation changes without resetting the active gate session" >&2
  exit 1
fi

echo "Android orientation config smoke passed"
