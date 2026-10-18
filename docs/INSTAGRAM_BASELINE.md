# Instagram Stories baseline

조사: 2026-10-19. 공식 공개 자료와 저장소를 확인했다. 현재 Android Instagram 앱에 로그인해 화면을 직접 관찰한 검증은 수행하지 않았다. 계정·지역·앱 버전별 기능 차이를 확정하지 않는다.

## 확인한 공식 근거

- [Meta: New Stickers in Instagram Stories, 2024-05-03](https://about.fb.com/news/2024/05/new-stickers-in-instagram-stories/): Add Yours Music, Frames, Reveal, Cutouts. 사진·영상에서 커스텀 스티커를 만드는 Cutouts와 흔들어 공개하는 Frames는 기존 기능이다.
- [Meta Design: Instagram brand evolution, 2022-05-23](https://www.meta.com/design-at-meta/blog/behind-instagrams-brand-evolution-movement-inclusivity-and-a-new-purpose/): Stories의 Instagram Sans Regular / Headline / Condensed와 움직임·표현에 관한 디자인 근거. 이 서체를 앱에 무단 번들하지 않는다.
- [Meta: Edits, 2025-04-22, 이후 업데이트 포함](https://about.fb.com/news/2025/04/introducing-edits-streamlined-video-creation-app/): 타임라인·효과·워터마크 없는 내보내기는 별도 영상 편집 앱 Edits의 기능이다. Stories 본체의 기능으로 혼동하지 않는다.
- [Android Media3 Transformer](https://developer.android.com/media/media3/transformer): MediaCodec 하드웨어 인코딩과 OpenGL 처리.
- [Instagram Stories sharing API](https://developers.facebook.com/docs/instagram-platform/sharing-to-stories/): 문서 접근이 제한되어 현재 요구 파라미터 전체를 확인하지 못했다. ADD_TO_STORY / Content URI 계약을 구현하고 실패 시 Android 공유·갤러리 저장을 제공한다. 실제 Instagram 수신·게시 성공은 미검증이다.

## 영역별 판단

| 영역 | 이미 존재 / 기본 기능 | 개선 여지 | igstoryplus에서 제공할 차별점 | Instagram 후처리 |
|---|---|---|---|---|
| 생성·미디어 선택 | 사진·영상 스토리와 갤러리 기반 제작은 기본 흐름 | 원본 보존과 반복 재편집 | 오프라인 초안, 사진별 비파괴 재크롭 | 최종 게시 |
| 변형·레이아웃 | 확대·이동·회전과 사진 레이어는 새로운 발명이 아님 | 자르기 접근·재크롭 | 선택 사진 자르기, 원본/비율/위치/회전/반전 | 계정별 레이아웃 옵션 |
| 텍스트 | 여러 글꼴·색·강조·애니메이션을 신규 기능으로 홍보하지 않음 | 한글 가독성·사용자 폰트 | 얇은 Soft Label, 절제된 Offset/Echo, Whisper, 작은 Drift | 현재 계정의 공식 폰트·애니메이션 |
| 스티커 | Cutouts, Frames, Reveal, Add Yours Music 공식 확인 | 직접 만든 PNG를 반복 가져오는 번거로움 | 사용자가 고른 SAF 폴더 동기화·즐겨찾기·최근·투명 여백 제거 | 멘션·투표·질문·링크·위치 |
| 음악 | 곡명·아티스트·앨범 아트 형태를 신규 발명이라 주장하지 않음 | 공식 카탈로그에 없는 자작곡 | 로컬 메타데이터 수정·구간·카드·약한 Beat Glow | 공식 음악 링크·카탈로그·권리 처리 |
| 사진 표현 | 정적 필터·필름 입자는 기존의 일반적 편집 방식 | 강도와 모션을 작게 조절 | 가장자리 Flow Blur, 시간에 따른 미세 질감·Handheld | 계정별 AR/필터 |
| 저장·공유 | 저장/스토리 게시 기본 흐름 | 외부 앱 실패 복구 | 자동 이미지/영상 결정·출력 검사·실패 대체 경로 | 공개 범위·실제 게시 |

사진 스티커의 정확한 최신 모양 목록, 현재 Android의 모든 폰트 이름과 음악 카드 변형, 화면 버튼 위치, 세부 크롭 제스처, 60초 전용 공유 지원 범위는 공식 근거 또는 실기기 관찰로 확정하지 못했다. 이 문서는 완전한 2026 기능 목록이 아니다.

## 구현에 반영한 디자인 판단

검정 배경, 큰 9:16 콘텐츠, 48dp 터치 영역, 선택 레이어의 문맥 도구를 사용한다. 모션·비트 효과는 기본 OFF이고 스타일 강도는 낮게 시작한다. 흐림/굴절은 가장자리 중심으로 제한한다. 인터랙티브 스티커와 공식 음악 등록은 복제하지 않는다. 결과물에 로컬 음악이라는 별도 배지를 넣지 않는다. 앱에서 Instagram 게시가 완료되었다고 표시하지 않는다.
