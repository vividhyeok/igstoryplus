#!/usr/bin/env bash
set -euo pipefail
mkdir -p validation
trap 'adb pull /sdcard/Android/data/com.vividhyeok.igstoryplus/files/validation/. validation/ >/dev/null 2>&1 || true' EXIT
adb shell wm size 1080x2340
adb shell wm density 420
adb shell settings put secure show_ime_with_hard_keyboard 1
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class 'com.vividhyeok.igstoryplus.ReleaseV052Test,com.vividhyeok.igstoryplus.ReleaseV051Test,com.vividhyeok.igstoryplus.StoryPlusTest,com.vividhyeok.igstoryplus.StoryUxTest,com.vividhyeok.igstoryplus.EditorUiTest#directTextIsLiveCancelableAndSurvivesRecreation,com.vividhyeok.igstoryplus.EditorUiTest#bundledEmojiWorkWithoutFolderGrantAndRestore,com.vividhyeok.igstoryplus.EditorUiTest#koreanTextDragUndoRedoDuplicateAndDelete,com.vividhyeok.igstoryplus.EditorUiTest#backgroundPhotoIsDirectAndReversible' com.vividhyeok.igstoryplus.test/androidx.test.runner.AndroidJUnitRunner | tee validation/plus-instrumentation.txt
grep -q 'OK (19 tests)' validation/plus-instrumentation.txt
