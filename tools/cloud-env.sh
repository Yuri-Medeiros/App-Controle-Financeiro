#!/usr/bin/env bash
# Source this file in each cloud shell. No workstation paths are needed.
export FINANCE_CLOUD_ROOT="${FINANCE_CLOUD_ROOT:-/workspace/cloud-android}"
export JAVA_HOME="$FINANCE_CLOUD_ROOT/jdk"
export ANDROID_HOME="$FINANCE_CLOUD_ROOT/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$FINANCE_CLOUD_ROOT/user"
export GRADLE_USER_HOME="$FINANCE_CLOUD_ROOT/gradle-user"
export XDG_DATA_HOME="$FINANCE_CLOUD_ROOT/data"
export XDG_CACHE_HOME="$FINANCE_CLOUD_ROOT/cache"
export XDG_CONFIG_HOME="$FINANCE_CLOUD_ROOT/config"
export PATH="$JAVA_HOME/bin:$FINANCE_CLOUD_ROOT/gradle/gradle-8.14.3/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0:$ANDROID_USER_HOME/bin:$PATH"

