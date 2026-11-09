#!/usr/bin/env bash
set -euo pipefail
adb install -r -t build/android-test/api-debug-androidTest.apk
adb shell am instrument -w -r \
  io.grimoire.api.test/androidx.test.runner.AndroidJUnitRunner | tee build/android-test/result.log
grep -Fq 'OK (1 test)' build/android-test/result.log
grep -Fq 'INSTRUMENTATION_CODE: -1' build/android-test/result.log
if grep -Eq 'FAILURES|INSTRUMENTATION_(FAILED|ABORTED)' build/android-test/result.log; then exit 1; fi
