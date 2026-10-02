#!/usr/bin/env bash
# Installs the Android SDK pieces needed to build Mjolnir (Linux, e.g. a Claude Code cloud session)
# and points local.properties at it. Safe to re-run.
#
# Network: needs dl.google.com (SDK + Google Maven), plus downloads.gradle.org for the Gradle
# wrapper and repo.maven.apache.org / plugins.gradle.org for other dependencies.
#
# Usage: scripts/setup-android-sdk.sh [sdk-dir]   (default: $ANDROID_HOME or ~/android-sdk)
set -euo pipefail

SDK_DIR="${1:-${ANDROID_HOME:-$HOME/android-sdk}}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Keep in sync with compileSdk in app/build.gradle.kts.
PACKAGES=("platform-tools" "platforms;android-34" "build-tools;34.0.0")

if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Installing Android command-line tools into $SDK_DIR"
  tmp="$(mktemp -d)"
  curl -fsSL "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" -o "$tmp/tools.zip"
  mkdir -p "$SDK_DIR/cmdline-tools"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -rf "$tmp"
fi

SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses > /dev/null || true
"$SDKMANAGER" --sdk_root="$SDK_DIR" "${PACKAGES[@]}"

echo "sdk.dir=$SDK_DIR" > "$REPO_ROOT/local.properties"
echo "Android SDK ready at $SDK_DIR (local.properties updated)."
echo "Build with: ./gradlew assembleDebug   or   ./gradlew assembleRelease"
