# 오픈소스 선정 (2026-10-19)

라이선스 원문 및 소스/빌드 설정을 GitHub API 또는 로컬 체크아웃에서 확인했다. 모든 후보를 실제 빌드한 것은 아니다. 아래 날짜는 최근 push 시각이며 품질 보증이 아니다.

| 후보 (GitHub) | 라이선스 / 최근 push | 판단 |
|---|---|---|
| burhanrashid52/PhotoEditor | MIT / 2026-09-27 | 주 엔진 채택. 실제 Kotlin 이미지·텍스트·레이어·선택·핀치·회전 모듈. 앱과 함께 빌드 검증 |
| ahmetbalkan/story_editor_pro | MIT / 2026-02-06 | pubspec 1.0.3, Dart ^3.8.1, native BoomerangProcessor. 카메라·스토리 UI 유용하나 로컬 음악/GPU 통합은 추가 필요. 빌드 미실행 |
| Atsumi3/story_editor | BSD-3-Clause / 2024-05-05 | pubspec 2.6.2, image_editor/extended_image 중심 정적 편집. 영상 파이프라인 별도 필요. 빌드 미실행 |
| takea-look/Memento | Apache-2.0 / 2025-08-16 | Kotlin Multiplatform/Compose 구조 확인. 이 앱은 작은 Android 전용 엔진 선택. 빌드 미실행 |
| virtualspirit/AndroidPhotoEditor | MIT / 2026-04-02 | PhotoEditor 계열, compileSdk34/Kotlin2 빌드 설정. upstream 직접 선택. 빌드 미실행 |
| camilo1498/stories_editor | CC0-1.0 / 2024-01-14 | pubspec 0.2.2의 Dart <3 제약과 여러 폰트·스티커 번들. 현대 SDK 이식 부담. 빌드 미실행 |
| androidx/media | Apache-2.0 | Media3 1.6.1 채택. MediaCodec/OpenGL 영상 출력·오버레이·재생 |
| Kashif-E/Media3-Editor | LICENSE 미발견 / 2025-11-19 | MediaEditManager와 SDK36 설정 조사. 코드 복사 안 함. 빌드 미실행 |
| naveenneog/ActionCut | LICENSE 미발견 / 2026-07-13 | 멀티트랙 범위가 과함. 코드 복사 안 함. 빌드 미실행 |

원본 링크는 `https://github.com/` 뒤에 위 저장소 이름을 붙인다.
PhotoEditor 고정 commit: `4a6967d3067d37010f9673034db27f53cf70e449`.
`photoeditor/` 저작권 주석 유지. 퍼블리싱 설정 제거, compileSdk35 적용, 문서 변형 콜백 노출, 첫 터치 선택·축소 범위·캔버스 밖 위치 처리와 helper 여백 조정. 레이어/터치/핀치/회전/선택은 엔진 재사용. 스타일·크롭 Undo/Redo는 앱 문서 모델 스냅샷으로 확장.

## 음악 분석

[rhythm-mp3](https://github.com/vividhyeok/rhythm-mp3)의 `src/analysis/onset.ts`, `dsp.ts`, `tempo.ts`를 검토했다. 소유자가 재사용을 명시 요청했다. 일반 오픈소스 LICENSE는 발견하지 못했다. `Audio.onsets`는 local mean + 1.35 × standard deviation의 적응형 peak selection을 Kotlin RMS novelty에 맞춰 축소 적용한다. 원 FFT·BPM 분류 전체를 이식한 것이 아니다.

[djbot](https://github.com/vividhyeok/djbot)의 Go `backend/dsp.go` FFT/onset/RMS/BPM 구조를 검토했다. 서버/다운로더와 해당 코드는 앱에 복사하지 않았다.

MediaCodec PCM 디코딩 → 50ms 에너지 엔벨로프 → onset 캐시. 효과 강도 변경은 캐시 키를 바꾸지 않는다. 정밀 비트 추적이라 주장하지 않으며 분석 실패 시 음악 합성은 유지한다.

## 라이선스 배포

PhotoEditor MIT 전문은 `third_party/PhotoEditor-LICENSE`와 앱 NOTICE에 포함. AndroidX/Media3·Kotlin·Gson은 Apache-2.0 전문 번들. Glide는 upstream LICENSE 전문(BSD/Apache 포함) 번들. 미사용 후보 코드는 배포하지 않는다.
