# igstoryplus

Instagram Stories에 자연스럽게 이어지는 오프라인 Android 편집기.

APK: [GitHub Releases](https://github.com/vividhyeok/igstoryplus/releases). 검증된 main은 개발용 Pre-release, 정식 버전은 태그로 배포. Android 10+, `com.vividhyeok.igstoryplus`.

사진/영상 선택 → 스타일·크롭 → 필요한 텍스트·개인 PNG·음악 → 인스타 스토리로 공유. 최종 게시·공식 음악·멘션·투표는 Instagram에서 진행합니다.

v0.2는 큰 캔버스, 사진 위 직접 텍스트 입력, 전체 화면 크롭과 인코딩 없는 실시간 재생을 중심으로 재설계했습니다. [편집기 UX·호환성](docs/EDITOR_UX.md).

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

개인 PNG는 번들하지 않습니다. androidTest의 생성 픽스처는 설치 앱 이모지 목록에 포함되지 않습니다.
