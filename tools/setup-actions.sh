#!/usr/bin/env bash
# Standard GitHub-hosted Ubuntu runner only; no cache or paid services.
set -euo pipefail
export JAVA_HOME="${JAVA_HOME_21_X64:?JDK 21 is required}"
export ANDROID_HOME="${ANDROID_HOME:?Android SDK is required}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/build-tools/36.0.0:$PATH"
export ANDROID_USER_HOME="$RUNNER_TEMP/android-user"
mkdir -p "$ANDROID_USER_HOME"
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' >/dev/null
{
  echo "JAVA_HOME=$JAVA_HOME"
  echo "ANDROID_HOME=$ANDROID_HOME"
  echo "ANDROID_USER_HOME=$ANDROID_USER_HOME"
} >> "$GITHUB_ENV"
echo "$JAVA_HOME/bin" >> "$GITHUB_PATH"
echo "$ANDROID_HOME/build-tools/36.0.0" >> "$GITHUB_PATH"
