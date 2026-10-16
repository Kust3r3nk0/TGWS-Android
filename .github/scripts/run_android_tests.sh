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
test_status=0
./gradlew --no-daemon :app:connectedDebugAndroidTest || test_status=$?
if [[ "$test_status" != 0 ]]; then
  # Retain diagnostic frames without changing or masking the test failure.
  mkdir -p app/build/reports/androidTests/connected/screenshots
  if ! adb -s emulator-5554 pull /sdcard/Download/tgws-ui/. app/build/reports/androidTests/connected/screenshots/; then
    echo 'Failed test screenshot collection unavailable; preserving test exit status' >&2
  fi
  exit "$test_status"
fi
python3 .github/scripts/verify_android_tests.py
mkdir -p app/build/reports/androidTests/connected/screenshots
adb -s emulator-5554 pull /sdcard/Download/tgws-ui/. app/build/reports/androidTests/connected/screenshots/
