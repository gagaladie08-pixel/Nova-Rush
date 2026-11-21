#!/usr/bin/env bash
# Preserve each real-device run before Gradle replaces connected-test reports on the next run.
set -euo pipefail

run_device() {
  local label="$1" result=0
  local destination="artifacts/device-verifications/$label"
  mkdir -p "$destination"
  {
    echo "Device run: $label"
    adb shell getprop ro.build.version.sdk
    adb shell wm size
    adb shell wm density
    adb shell settings get secure navigation_mode
    adb shell cmd overlay list --user 0 | grep 'navbar' || true
  } > "$destination/device.txt"
  scripts/gradle-ci.sh connectedDebugAndroidTest || result=$?
  cp artifacts/diagnostics/gradle.log "$destination/gradle.log"
  for path in app/build/reports/androidTests app/build/outputs/androidTest-results app/build/outputs/connected_android_test_additional_output; do
    if [ -d "$path" ]; then
      mkdir -p "$destination/$(dirname "$path")"
      cp -a "$path" "$destination/$path"
    fi
  done
  return "$result"
}

if [ "${1:-}" = "navigation" ]; then
  for mode in gestural threebutton; do
    overlay="com.android.internal.systemui.navbar.$mode"
    adb shell cmd overlay enable-exclusive --category --user 0 "$overlay"
    adb shell cmd overlay list --user 0 | tr -d '\r' | grep -F "[x] $overlay"
    run_device "android-35-$mode"
  done
else
  run_device "android-29-default"
fi
