#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Gradle uses JAVA_HOME when set, otherwise java from PATH.
if ! command -v adb >/dev/null 2>&1; then
    echo "Error: add Android SDK platform-tools to PATH before installing." >&2
    exit 1
fi

cd "$PROJECT_DIR"
echo "==> Building Portal Gemini Live APK..."
./gradlew assembleDebug

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH"
    exit 1
fi

mkdir -p "$PROJECT_DIR/dist"
cp "$APK_PATH" "$PROJECT_DIR/dist/portal-gemini-live.apk"

echo "==> Installing on Meta Portal..."
adb install -r -g "$APK_PATH"

echo "==> Launching Gemini Live..."
adb shell am start -n dev.portalgemini/.MainActivity

echo "==> Done!"
