#!/usr/bin/env bash
set -euo pipefail
mkdir -p validation
trap 'adb pull /sdcard/Android/data/com.vividhyeok.igstoryplus/files/validation/. validation/ >/dev/null 2>&1 || true' EXIT
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
# Match android_test.sh: let a cold API 35 system settle and stop only its launcher.
sleep 15
adb shell am force-stop com.android.launcher3
adb shell am force-stop com.android.launcher3.quickstep
adb shell dumpsys window windows > validation/plus-windows-before.txt
adb shell screencap -p /sdcard/plus-before.png
adb pull /sdcard/plus-before.png validation/plus-before.png
adb shell wm size 1080x2340
adb shell wm density 420
adb shell settings put secure show_ime_with_hard_keyboard 1
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class 'com.vividhyeok.igstoryplus.ReleaseV052Test,com.vividhyeok.igstoryplus.ReleaseV051Test,com.vividhyeok.igstoryplus.StoryPlusTest,com.vividhyeok.igstoryplus.StoryUxTest,com.vividhyeok.igstoryplus.EditorUiTest#directTextIsLiveCancelableAndSurvivesRecreation,com.vividhyeok.igstoryplus.EditorUiTest#bundledEmojiWorkWithoutFolderGrantAndRestore,com.vividhyeok.igstoryplus.EditorUiTest#koreanTextDragUndoRedoDuplicateAndDelete,com.vividhyeok.igstoryplus.EditorUiTest#backgroundPhotoIsDirectAndReversible' com.vividhyeok.igstoryplus.test/androidx.test.runner.AndroidJUnitRunner | tee validation/plus-instrumentation.txt
adb shell dumpsys window windows > validation/plus-windows-after.txt
adb logcat -d -s AndroidRuntime:E ActivityManager:I TestRunner:I > validation/plus-runtime.txt
grep -q 'OK (19 tests)' validation/plus-instrumentation.txt
