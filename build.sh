#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"

cd "$(dirname "$0")"
./gradlew :app:assembleRelease

cp app/build/outputs/apk/release/app-release.apk app/build/outputs/apk/release/listentome.apk

echo "APK built: app/build/outputs/apk/release/listentome.apk"