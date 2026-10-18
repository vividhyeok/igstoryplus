# 검증 기록

## 확인된 실행

- [CI 39152724934](https://github.com/vividhyeok/igstoryplus/actions/runs/39152724934), commit `d0d7949`: 32개 테스트, lint, 실제 미디어의 ffprobe/전체 디코딩/모션·루프 검사, 릴리스 서명과 APK 검사, Pre-release 게시 및 재다운로드 비교까지 전체 성공. [개발용 APK 릴리스](https://github.com/vividhyeok/igstoryplus/releases/tag/dev-7-d0d7949).

- Windows CLI JDK21 / SDK35 / Gradle8.13: APK 컴파일·설치, lint, 앱 단위4개 + PhotoEditor 단위20개 통과.
- [CI 39151774858](https://github.com/vividhyeok/igstoryplus/actions/runs/39151774858), commit `2c2238e`: lint·단위 테스트 및 Android API35 계측8개 전부 통과. 당시 배포는 UTP 종료 후 앱 자동 삭제로 미디어 추출이 실패해 차단되었다. 이 실행을 전체 CI 성공으로 계산하지 않는다.
- `leaveApksInstalledAfterRun=true`로 테스트 후 산출물을 보존한다. [AndroidX의 동일 설정](https://github.com/androidx/androidx/blob/androidx-main/gradle.properties) 참고.
- 로컬 최초 API36은 시스템 Bluetooth crash, API35 장시간 실행은 호스트 VM 중단으로 전체 계측 미완주. 로컬의 부분 통과와 독립 CI 완주를 구분한다.
- Galaxy S22 실기기 미연결. 기기 성능·카메라·문서 공급자·Instagram 실제 수신은 미검증.

## 자동 검증 내용

| 분야 | 검증 |
|---|---|
| 모델4 | 출력 자동 결정, 사진/음악/영상 길이, Undo/Redo 원본·크롭·텍스트·음악 보존, 루프/모션 기본값, 에너지 온셋 |
| PhotoEditor20 | upstream graphic manager, 텍스트 스타일, 저장 설정 등 기존 단위 테스트 |
| 편집 화면2 | 한글 입력, 드래그·핀치·회전, Undo/Redo, 복제·삭제, 초안 재생성 / 원본 영상의 GPU 디코딩 미리보기 |
| 파이프라인6 | JPEG1080×1920, 추가 사진 크롭, 7개 GPU 스타일, 실제 SAF URI grant·persist·PNG 추가/이름변경/삭제/빈폴더/알파/잘못된 폴더, 15초 음악+Flow Blur+Drift 카드 합성, 5초 모션, 30초/60초 영상+음악 출력, 초안·프리셋·없는 원본·취소, MP3/M4A/FLAC 메타데이터·PCM 디코딩 |
| 독립 파일 검사 | ffprobe H.264/AAC·표시 해상도·길이·fps, FFmpeg 전체 프레임 디코딩, 움직임과 루프 경계 픽셀 차이. 30초 입력 픽스처는60fps로 생성하여30fps 변환도 검증 |
| 공유 | ADD_TO_STORY·content URI·ClipData·읽기 권한 구성. 실제 Instagram 수신·게시는 미검증 |
| APK | aapt applicationId/version, apksigner, SHA256, Release 업로드 후 재다운로드 바이트 비교 |

정식 [v0.1.0](https://github.com/vividhyeok/igstoryplus/releases/tag/v0.1.0)은 같은 검증 게이트를 다시 통과한 APK만 게시한다. Release의 `validation-report.json`에 해당 커밋·실행 URL·테스트 수·실제 파일 스트림/루프 검사값을 첨부한다.

## Galaxy S22 확인

APK 설치 후 사진/영상 선택과 촬영, 한글 키보드, 두 손가락 변형, 재크롭, PNG 폴더 선택 및 재실행 후 권한 유지, 음악 구간 재생, MP4 출력, Instagram 전용 공유를 확인합니다. 전용 공유 실패 시 일반 공유/갤러리 저장을 사용합니다.

JPEG1–2초/15초 MP4 3–8초는 목표이며 측정 전 보장하지 않습니다.
GitHub x86_64 소프트웨어 에뮬레이터는 하드웨어 인코더가 있는 S22의 성능 측정 대체물이 아닙니다. 에뮬레이터 출력은 목표 시간보다 느립니다.
초기 성공 계측 실행에서 15초 음악/Flow Blur 테스트는 약86초, 5초 모션과 30초/60초 영상 합성 묶음은 약268초였습니다. 테스트 전체 소요이며 S22 출력 벤치마크가 아닙니다.
