# v0.2 편집기 재설계

기준 소스: e78367d, 최신 main 새 클론에서 시작. 기존 PhotoEditor/MIT 제스처 및 Media3/GPU/Exporter를 유지한다.

## 구조와 흐름

- EditorChrome: 52dp 상단 아이콘, 남은 공간의 최대 9:16 캔버스, 60dp 단일 하단 작업 영역. 상태 문구/두 개의 영구 메뉴/별도 공유 행을 제거했다. 시스템 바·디스플레이 컷아웃·IME 인셋을 처리한다.
- CanvasTextEditor: 사진 위 실제 Android EditText. 조합 중 setText를 호출하지 않는다. 한글·줄바꿈·글꼴·색상·정렬·스타일 변경은 즉시 보인다. 다른 사진/PNG 레이어를 키 입력마다 재생성하지 않는다.
- 새 텍스트는 960px 참조 폭·880px 줄바꿈·동일 글꼴/자간/패딩을 Artwork와 공유한다. 기존 텍스트는 textLayoutVersion=0 경로로 이전 출력과 호환된다. 명시적인 재편집 완료 시 새 레이아웃으로 변환된다.
- 텍스트 취소/완료는 트랜잭션이며 Undo는 이전 프로젝트로 복귀한다. 작성 중 텍스트는 text-edit.json에 별도 보존한다. 뒤로가기에서 IME를 닫은 뒤 편집을 완료해도 입력이 사라지지 않는다.
- EditorSheet: 기본 도구를 작은 하단 시트로 표시한다. 헤더를 아래로 밀거나 뒤로가기로 닫는다. 오류/파괴적 확인/출력 진행은 여전히 확인 다이얼로그를 쓴다.
- 크롭은 별도 작은 팝업 대신 캔버스 전체 모드. 원본/1:1/4:5/9:16/자유 비율, 드래그·핀치, 90도 회전·반전을 사용하고 완료 또는 취소한다. 배경 더블탭 또는 사진 메뉴에서 접근한다.
- PreviewClock: Exporter와 분리된 재생/일시정지. 사진 GPU, 레이어 모션, 원본 영상, 선택 음악 구간을 동기화한다. 짧은 음악 구간을 반복한다. 음악 볼륨은 미리보기와 최종 PCM 처리에 적용한다.
- 빈 곳 탭은 레이어 선택 해제, 텍스트 더블탭은 직접 편집. 드래그/핀치/회전은 PhotoEditor 엔진을 사용한다. 순서·모션 등 보조 도구는 선택 레이어의 추가 작업에 넣었다.

## 호환성과 검증 원칙

applicationId와 기존 지속 서명키를 유지한다. 초안/프리셋의 기존 필드를 지우지 않고 새 필드는 기본값으로 읽는다. versionCode는 Actions 실행 번호에 따라 증가한다. 테스트 실패 빌드는 배포하지 않는다.

새 테스트는 직접 입력/취소/복원/Undo, 캔버스 비율과 IME 가시성, 미리보기 중 출력 파일 미생성, 전체 캔버스 크롭, PCM 볼륨, 새 텍스트 참조 레이아웃을 검사한다. 기존 PNG·음악·5/15/30/60초 파일 검사도 유지한다. 최종 결과는 Actions 보고서와 VALIDATION.md를 따른다.

## 실기기 확인 및 알려진 경계

Galaxy S22 물리 기기는 이 환경에 연결되어 있지 않다. Samsung 한글 키보드의 실제 조합/커서/선택 핸들, 제스처 내비게이션·홀펀치, Bluetooth 오디오 지연, 열/프레임 속도, 실제 Instagram 수신은 직접 확인이 필요하다. 자동 replaceText 테스트를 실물 한글 키보드 테스트로 주장하지 않는다.

사진 스타일은 기존과 같이 배경/원본 영상 전체에 적용한다. 개별 사진 레이어만 별도의 GPU 스타일을 갖는 새 엔진은 추가하지 않았다. 원본 배경의 위치·확대는 크롭 모드에서, 추가 레이어의 위치·크기·회전은 직접 제스처로 조작한다.

요청 메시지는 5.4 이후가 두 차례 깨진 상태로 전달되었다. 읽을 수 없는 후속 조항은 확정 요구사항으로 추측하지 않고, 확인 가능한 1~5번과 앞서 확인된 전체 캔버스 크롭 방향을 구현한다.

볼륨 처리 참고: [Android Media3 ChannelMixingAudioProcessor](https://developer.android.com/reference/androidx/media3/common/audio/ChannelMixingAudioProcessor), [ChannelMixingMatrix](https://developer.android.com/reference/androidx/media3/common/audio/ChannelMixingMatrix). 현재 고정 의존성 1.6.1의 API로 컴파일·검사한다.
