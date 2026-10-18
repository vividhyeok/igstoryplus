# 검증 기록

진행 중. 실제 실행 결과로 갱신합니다.

- Windows CLI JDK21 / SDK35 / Gradle8.13: debug 컴파일·에뮬레이터 설치 성공.
- 앱 모델 단위 테스트 4개 통과.
- PhotoEditor 기존 테스트: Mockito/JDK 호환 수정 후 재실행 중.
- API36 AVD 시스템 Bluetooth 프로세스 crash와 로컬 디스크 부족으로 최초 계측 중단. 성공으로 계산하지 않음. GitHub Actions에서 독립 재검증.
- Galaxy S22 실기기 미연결. 기기 성능·카메라·문서 공급자·Instagram 실제 수신은 미검증.

## Galaxy S22 확인

APK 설치 후 사진/영상 선택과 촬영, 한글 키보드, 두 손가락 변형, 재크롭, PNG 폴더 선택 및 재실행 후 권한 유지, 음악 구간 재생, MP4 출력, Instagram 전용 공유를 확인합니다. 전용 공유 실패 시 일반 공유/갤러리 저장을 사용합니다.

JPEG1–2초/15초 MP4 3–8초는 목표이며 측정 전 보장하지 않습니다.
