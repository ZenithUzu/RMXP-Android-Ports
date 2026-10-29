#!/usr/bin/env bash
set -euo pipefail
# Keep screenshots even on test failure; MediaStore pictures survive test-app cleanup.
trap 'adb pull /sdcard/Pictures/rmxp-ui capturas-ui >/dev/null 2>&1 || true' EXIT
android/gradlew -p android -PprebuiltLibs=/tmp/rmxp-ui-empty --no-daemon :uitest:connectedDebugAndroidTest
