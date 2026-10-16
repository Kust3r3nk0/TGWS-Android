#!/usr/bin/env bash
# The emulator action runs separate script lines in separate shells. Keep all
# dependent checks in this process so a wrong API or failed test cannot continue.
set -euo pipefail
requested_api=${1:?Requested Android API is required}
actual_api=$(adb -s emulator-5554 shell getprop ro.build.version.sdk | tr -d '\r')
if [[ "$actual_api" != "${requested_api%%.*}" ]]; then
  echo "Wrong emulator API: expected ${requested_api%%.*}, received $actual_api" >&2
  exit 1
fi
./gradlew --no-daemon :app:connectedDebugAndroidTest
python3 .github/scripts/verify_android_tests.py
mkdir -p app/build/reports/androidTests/connected/screenshots
adb -s emulator-5554 pull /sdcard/Download/tgws-ui/. app/build/reports/androidTests/connected/screenshots/
