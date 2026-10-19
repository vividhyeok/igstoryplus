# igstoryplus

Instagram Stories에 자연스럽게 이어지는 오프라인 Android 편집기.

APK: [GitHub Releases](https://github.com/vividhyeok/igstoryplus/releases). 검증된 main은 개발용 Pre-release, 정식 버전은 태그로 배포. Android 10+, `com.vividhyeok.igstoryplus`.

사진/영상 선택 → 스타일·크롭 → 필요한 텍스트·개인 PNG·음악 → 인스타 스토리로 공유. 최종 게시·공식 음악·멘션·투표는 Instagram에서 진행합니다.

v0.4는 개인 이모지 10종 기본 번들, 검색·분류·SAF 폴더, 사진 스타일 10종, 음악 반응 4종, 음악 카드 8종과 숨김, 텍스트 10종을 제공합니다. 사진/영상의 직접 제스처와 레이어별 스타일·모션, 이름 있는 초안·시각적 프리셋을 지원합니다. [통합 결과](docs/INTEGRATION_V040.md), [진행 기록](docs/IMPLEMENTATION_PROGRESS.md).

## CLI

JDK17+, Android SDK35. Android Studio/VS Code GUI 불필요.

```sh
./gradlew :app:lintDebug :app:testDebugUnitTest :photoeditor:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
adb pull /sdcard/Android/data/com.vividhyeok.igstoryplus/files/validation validation
python scripts/verify_media.py validation
```

Windows는 `gradlew.bat`. 결과는 `app/build/outputs/apk/debug/app-debug.apk`.

`photoeditor/`는 MIT 주 편집 엔진. 앱은 비파괴 문서, GPU 스타일, SAF, 음악, Media3 출력과 공유를 확장합니다. 연구·라이선스: [OPEN_SOURCE](docs/OPEN_SOURCE.md), [Instagram 기준](docs/INSTAGRAM_BASELINE.md), [검증 결과](docs/VALIDATION.md).

CI Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`. 키는 Git에 넣지 않습니다. versionCode는 `10000 + run_number`. Secrets가 없으면 배포를 실패시킵니다. 로컬 release debug 서명 fallback은 테스트 전용입니다.

사용자가 제공한 로컬 PNG 10종을 정규화해 번들합니다. 원본은 수정하지 않으며 [검사 기록](docs/emoji-audit.json)에 해시를 남깁니다. SAF 개인 폴더도 유지합니다. 테스트 사진·생성 픽스처·이전 셰이더는 androidTest 전용이며 설치 앱에 포함되지 않습니다.
