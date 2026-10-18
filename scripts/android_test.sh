#!/usr/bin/env bash
# Preserve rendered evidence even when an assertion fails; never turn a test failure into success.
set +e
adb shell settings put secure show_ime_with_hard_keyboard 1
# Fail fast on keyboard/gesture regressions before spending minutes encoding 60-second media.
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vividhyeok.igstoryplus.EditorUiTest --console=plain
ui_result=$?
if [ "$ui_result" -ne 0 ]; then
  adb pull /sdcard/Android/data/com.vividhyeok.igstoryplus/files/validation validation
  exit "$ui_result"
fi
./gradlew :app:connectedDebugAndroidTest --console=plain
test_result=$?
adb pull /sdcard/Android/data/com.vividhyeok.igstoryplus/files/validation validation
exit "$test_result"
