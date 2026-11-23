# K8 ccNC 홈 테마 — 작업 규칙 (모든 AI 공통)

먼저 `docs/AI_HANDOFF.md` 맨 위(최신)를 읽는다. 차 확인 순서는 `docs/설정-차시험.md` 2-8절.

## 상단바·하단바 비율을 틀지 않는다

- 위쪽 줄(`#top-bar`)과 아래쪽 줄(`#detail-bottom-bar`, `assets/navbar.html`)의 크기·자리·바탕색은
  `tools/bar_baseline.json` 에 기준값으로 묶여 있다. 다른 화면을 고치다가 이 값이 바뀌면 안 된다.
- 고친 뒤 반드시 `node tools/bar_guard.cjs` 를 돌린다. "기준값과 같음"이 나와야 한다.
  `tools/build_nemesis.py` 도 빌드 전에 이 검사를 돌리고, 다르면 묶음을 만들지 않는다.
- 설정처럼 가운데 칸이 없는 화면의 하단바는 **순정 ccNC 와 같이 양쪽 칸만 있고 가운데가 비어 있는 것이 맞다.**
  한 줄로 이어 붙이지 않는다.
- 두 줄을 일부러 바꿔야 하면 사용자에게 먼저 묻고, 승인 뒤에만 `node tools/bar_guard.cjs --update` 로 기준값을 새로 쓴다.
  기준값 파일을 손으로 고치거나 검사를 끄지 않는다(`K8_SKIP_BAR_GUARD=1` 은 Edge 가 없는 PC 에서만).

## 차 화면(Chrome 30)

- `python tools/c30_check.py assets/index.html assets/navbar.html` 이 0곳이어야 한다.
- 차 흉내 비교: `node tools/c30_preview.cjs` (사진은 `captures/c30/`, 커밋하지 않음).

## 지켜야 할 것

- 차량 설정 쓰기, CAN/LIN/Binder 송신, 모듈 설치는 사용자 승인 전 금지.
- 도우미(helper) 코드나 앱 동작을 바꾸면 `AndroidManifest.xml` 버전과 `tools/build_nemesis.py` 의 `HOME_PACKAGE_VERSION` 을 올린다.
- `captures/`, `private/`, 설명서 사진, 차 IP·기기 식별 정보는 커밋하지 않는다.
