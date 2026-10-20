igstoryplus v0.4.0 — 개인 이모지와 음악이 있는 스토리

현재 최종 구현을 우선 배포해 달라는 사용자 요청에 따른 릴리스입니다. 빌드·패키지·기존 키 서명·업로드 후 다운로드 일치를 검사합니다. 전체 계측 통과 릴리스는 아닙니다. 앞선 CI에서 25개 계측 중 텍스트 편집 테스트 `directTextIsLiveCancelableAndSurvivesRecreation`가 `Two actual taps must reopen text`로 실패했습니다. 텍스트를 더블탭해 다시 편집하는 동작은 기기에 따라 불안정할 수 있습니다. 해당 CI: https://github.com/vividhyeok/igstoryplus/actions/runs/39289857392 . 이번 배포는 계측 테스트를 재실행하지 않습니다.

개인 PNG 10종을 512×512 투명 기본 이모지로 통합했습니다. 원본은 보존하며 기본/내 폴더/최근/즐겨찾기, 분류·검색을 지원합니다. 기존 SAF 개인 폴더도 계속 사용할 수 있습니다.

사진 스타일 10종: 기존 Grainy/Gritty/Lores/Handheld/Zoomblur/Flow Blur를 개선하고 Shutter Drag, Sensor Raw, Frame Echo를 추가했습니다. 모든 스타일의 강도 0%는 원본을 유지합니다. 사진·영상은 직접 이동·핀치·회전하며 실행 취소할 수 있습니다.

음악 구간의 반응 ON/OFF·강도·종류를 재생 중 조절합니다. 에너지·대역·플럭스·온셋·엔벨로프 분석을 캐시하며 빛 반응, 사진 배경 전용 Ambient Breathing/Album Aura, 카드 Music Dot을 제공합니다. 영상에는 빛 반응/Music Dot을 제공합니다. 원본 소리와 추가 음악은 독립 볼륨입니다.

음악 카드 Mini Credit / Cover Caption / Mono Tag / Progress Line, 텍스트 Film Caption / Mono Stamp / Editorial Serif / Margin Note를 추가했습니다. 실제 진행선과 반응점은 프리뷰/내보내기에서 같은 그리기를 사용합니다. 기존 카드·텍스트·개인 폰트는 유지합니다.

사진 레이어별 필터, 종류·강도를 선택하는 미세 모션, 여러 이름 있는 초안과 프리셋 썸네일을 지원합니다. 초안·레이어·사용자 URI는 기존 형식을 읽습니다.

Android 10+ / SDK35. universal APK 하나를 제공합니다. 네이티브 ABI 종속 라이브러리가 없어 Galaxy S22에서도 동일한 APK를 설치합니다. 지속 서명키를 재사용합니다. 로컬 release의 debug 서명은 배포에 사용하지 않습니다.

검증 자료: validation-report.json, ux-screenshots.zip, style-comparisons.zip, signature.txt, package.txt, SHA256SUMS. 실사진 5장×10종×3강도, 이전/이후 비교, 0% 동일성과 루프·한글·온셋·동적 카드, 실제 JPEG/H.264/AAC 출력과 전체 디코딩을 검사합니다. 상세 범위: docs/INTEGRATION_V040.md.

Galaxy S22 발열·장시간 성능, Samsung 카메라/문서 공급자, 설치된 Instagram 앱의 실제 공유 수신은 실기기 확인이 필요합니다. 개별 사진 필터는 정적이고 레이어 변환 모션을 제공합니다. Ambient/Aura는 사진 배경 전용입니다. Instagram 공유는 게시 성공을 보장하지 않습니다.
