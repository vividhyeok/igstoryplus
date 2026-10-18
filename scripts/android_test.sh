#!/usr/bin/env bash
# Preserve rendered evidence even when an assertion fails; never turn a test failure into success.
set +e
./gradlew :app:connectedDebugAndroidTest --console=plain
test_result=$?
adb pull /sdcard/Android/data/com.vividhyeok.igstoryplus/files/validation validation
exit "$test_result"
