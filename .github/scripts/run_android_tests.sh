#!/usr/bin/env bash
# The emulator action runs separate script lines in separate shells. Keep all
# dependent checks in this process so a wrong API or failed test cannot continue.
set -euo pipefail
requested_api=${1:?Requested Android API is required}
if [[ ! "$requested_api" =~ ^[0-9]+([.][0-9]+)?$ ]] || (( ${requested_api%%.*} < 36 )); then
  echo 'Android tests require API 36 or newer' >&2
  exit 1
fi
actual_api=$(adb -s emulator-5554 shell getprop ro.build.version.sdk | tr -d '\r')
if [[ "$actual_api" != "${requested_api%%.*}" ]]; then
  echo "Wrong emulator API: expected ${requested_api%%.*}, received $actual_api" >&2
  exit 1
fi
python3 .github/scripts/collect_android_environment.py --output app/build/reports/android-environment/before.json
# The installer test must not revoke its own app-op: Android kills that UID.
# The test grants it after Gradle installs the target; restore it from the host
# after instrumentation, including failed tests. No APK permission is auto-granted.
test_package=io.github.kust3r3nk0.tgwsandroid
previous_install_mode=default
if [[ $(adb -s emulator-5554 shell pm path "$test_package") == package:* ]]; then
  previous_install_op=$(adb -s emulator-5554 shell appops get "$test_package" REQUEST_INSTALL_PACKAGES)
  if [[ "$previous_install_op" =~ REQUEST_INSTALL_PACKAGES:\ (allow|deny|ignore|default|foreground) ]]; then
    previous_install_mode=${BASH_REMATCH[1]}
  fi
fi
restore_install_op() {
  local test_exit=$?
  trap - EXIT
  # Gradle may already have uninstalled the test target; its app-op then no longer exists.
  if [[ $(adb -s emulator-5554 shell pm path "$test_package") == package:* ]]; then
    if ! adb -s emulator-5554 shell appops set "$test_package" REQUEST_INSTALL_PACKAGES "$previous_install_mode"; then
      echo 'Failed to restore emulator install permission' >&2
      if [[ "$test_exit" == 0 ]]; then test_exit=1; fi
    fi
  fi
  exit "$test_exit"
}
trap restore_install_op EXIT
test_status=0
./gradlew --no-daemon :app:connectedDebugAndroidTest || test_status=$?
environment_status=0
python3 .github/scripts/collect_android_environment.py --output app/build/reports/android-environment/after.json || environment_status=$?
if [[ "$test_status" != 0 ]]; then
  # Retain diagnostic frames without changing or masking the test failure.
  mkdir -p app/build/reports/androidTests/connected/screenshots
  if ! adb -s emulator-5554 pull /sdcard/Download/tgws-ui/. app/build/reports/androidTests/connected/screenshots/; then
    echo 'Failed test screenshot collection unavailable; preserving test exit status' >&2
  fi
  exit "$test_status"
fi
if [[ "$environment_status" != 0 ]]; then
  exit "$environment_status"
fi
python3 .github/scripts/verify_android_tests.py
mkdir -p app/build/reports/androidTests/connected/screenshots
adb -s emulator-5554 pull /sdcard/Download/tgws-ui/. app/build/reports/androidTests/connected/screenshots/
