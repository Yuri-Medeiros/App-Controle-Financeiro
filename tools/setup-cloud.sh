#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/cloud-env.sh"
[[ "$(uname -sm)" == 'Linux x86_64' ]] || { echo 'Expected Linux x86_64'; exit 1; }
mkdir -p "$FINANCE_CLOUD_ROOT"/{downloads,user,cache,data,config,gradle-user}
fetch() {
  local url="$1" target="$2" expected="$3"
  if [[ ! -f "$target" ]] || ! echo "$expected  $target" | sha256sum -c --status; then
    curl -fsSL --retry 2 "$url" -o "$target.part"
    echo "$expected  $target.part" | sha256sum -c --status || { echo "Checksum mismatch: $target"; exit 1; }
    mv "$target.part" "$target"
  fi
}
fetch 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz' \
  "$FINANCE_CLOUD_ROOT/downloads/temurin.tar.gz" d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
  mkdir -p "$JAVA_HOME"
  tar -xzf "$FINANCE_CLOUD_ROOT/downloads/temurin.tar.gz" -C "$JAVA_HOME" --strip-components=1
fi
fetch 'https://downloads.gradle.org/distributions/gradle-8.14.3-bin.zip' \
  "$FINANCE_CLOUD_ROOT/downloads/gradle.zip" bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531
if [[ ! -f "$FINANCE_CLOUD_ROOT/gradle/gradle-8.14.3/bin/gradle" ]]; then
  unzip -q "$FINANCE_CLOUD_ROOT/downloads/gradle.zip" -d "$FINANCE_CLOUD_ROOT/gradle"
fi
chmod +x "$FINANCE_CLOUD_ROOT/gradle/gradle-8.14.3/bin/gradle"
fetch 'https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip' \
  "$FINANCE_CLOUD_ROOT/downloads/commandline.zip" 0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8
if [[ ! -x "$ANDROID_HOME/cmdline-tools/23.0/bin/android" ]]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q "$FINANCE_CLOUD_ROOT/downloads/commandline.zip" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/23.0"
fi
# The official launcher bootstraps Android CLI. Refuse silent version drift.
"$ANDROID_HOME/cmdline-tools/23.0/bin/android" --no-metrics --version
echo 'b34ed0baeeffa2aaee2720d82491b975835696e2e56840449078bec885436564  '"$ANDROID_USER_HOME/bin/android-cli" | sha256sum -c --status || {
  echo 'Android CLI changed: review and update the pinned checksum before continuing.'; exit 1;
}
android-cli --no-metrics --sdk="$ANDROID_HOME" sdk install 'platforms;android-36'
android-cli --no-metrics --sdk="$ANDROID_HOME" sdk install 'build-tools;36.0.0'
android-cli --no-metrics --sdk="$ANDROID_HOME" sdk install platform-tools
javac -version
gradle --version
test -x "$ANDROID_HOME/platform-tools/adb"
echo 'Platform tools installed. ADB/device execution is a separate validation.'
apksigner version

