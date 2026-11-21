# 5W 순정 내비와 ccNC 내비 비교

2026-11-21 기준. 양쪽 모두 **기아 공식 한국어 매뉴얼의 화면 이미지**만 근거로 했다. 차량 실물 확인은 없다.

- 5W 근거: [STD_GEN5W 내비 매뉴얼](https://ownersmanual.kia.com/ivi/STD_GEN5W/AVNT/KOR/Korean/006_Navigation_basic.html) — `D-navi_guidance_a.png`, `D-navi_searchroute_main_a.png`, `D-navi_spilit1.png`
- ccNC 근거: [ccNC 지도 매뉴얼](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Map.html) · [경로 검색](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Map_search.html) · [경로 편집](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Map_editroute.html) — `D-map_main.png`, `D-map_guidance.png`, `D-map_route.png`, `D-map_route_edit.png`, `D-map_place_main.png`
- 받은 이미지는 `docs/images/nav-compare/`에 있다. 나란히 보는 페이지는 [nav-compare.html](nav-compare.html)이다.

## 1. 화면 골격

| 항목 | 5W 순정 내비 | ccNC 내비 |
|---|---|---|
| 지도 배경 | 연한 녹색 공원 + 베이지 바탕, 경로는 초록 | 회록 도로 + 흰 바탕, POI 표기가 훨씬 촘촘 |
| 왼쪽 세로 버튼 | 홈 · SE(나침반) · Nav(음성) · ＋ · Auto 400 m · － — **6개** | 검색 · 나침반 N · Nav(음성) · Auto 50 m · Scale — **5개** |
| 시계 위치 | 상단 **중앙** (옆에 카메라 버튼) | 우상단 (옆에 운전자 아이콘) |
| 안내 카드 | 우상단 스택 3장 (500 m · 1.6 km · 2.5 km) | 우측 스택 (240 m · 600 m · 1.1 km) + **지도 중앙 하단에 차선 안내 띠** |
| 하단 표시줄 | **내비 자체 메뉴줄**: 메뉴 · 경로 종료 · 플래그+287 km/14:32 · ⓘ 목적지(서초구 현릉로) · 오른쪽 끝 버튼 | **시스템 표시줄**: ← 뒤로 · ⌂ 홈 · ≡ 메뉴 ㅤ 중앙 알약 ㅤ ▶ Media Off |
| 내비 안 분할 | 있음. 우측 패널에 안내 카드 + 확대 지도가 함께 뜬다 (`D-navi_spilit1.png`) | 내비 안 분할은 없고, **시스템 위젯**으로 우측 영역을 연다 |

## 2. 검색과 목적지

| 항목 | 5W | ccNC |
|---|---|---|
| 검색 화면 | **검은 타일 메뉴**. 통합 검색 · 주변 검색 · 주소 검색 · 최근 목적지 · 등록 지점 · 기아오토라 · 경로 취소 · 경로 편집 · 경로 탐색 옵션 · 사고 속보 · 경치 좋은 길 | **검색창 + 한글 자판**. 오른쪽에 내비게이션 최근 검색 패널, 검색 결과는 별도 목록 화면 |
| 검색 하단 탭 | **우리집 · 사무실 · 즐겨찾기 1·2·3** | 최근 목적지 · 주변 검색 · 등록 지점 |
| 등록 지점 | 검색 타일 '등록 지점' + 우리집/사무실 탭 | 등록 지점 탭 + 목적지 설정 · 위치 저장 · 선택지 주변 검색 |
| 경로 편집 | 타일로 진입 | 왼쪽 패널(경유지 추가·삭제·순서 변경) + 전국 지도, 하단에 '경로 탐색' |

- 우리 집·회사가 5W 쪽은 **이미 우리집·사무실 탭으로 잡혀 있다.** ccNC는 '등록 지점'이라는 일반 개념으로만 들어 있다. 테마에서 '우리 집·회사 바로 안내'를 만들 때 5W의 우리집/사무실 탭을 그대로 여는 쪽이 정공법에 가깝다.

## 3. 하단 표시줄이 둘을 가르는 핵심

- 5W의 하단 줄은 **순정 내비 메뉴**다. `메뉴 / 경로 종료 / 남은 거리 / 목적지 / 출구`.
- ccNC의 하단 줄은 **시스템 바**다. `뒤로 · 홈 · 메뉴` + 중앙 알약 + 오른쪽 미디어 상자.
- 우리 테마가 재현한 하단바는 **ccNC 쪽**이다(`assets/index.html`의 `.bottom-bar`, `assets/navbar.html`이 같은 것을 쓴다).
- **결정: 순정 줄을 가리고 그 자리에 ccNC 바를 놓는다.** 0.4.7은 기본값을 높이 90 px, 아래 여백 0 px로 바꿨다. 순정 줄의 기능을 ccNC 중앙 알약으로 옮겨, 5W의 정보를 잃지 않는다.
  - 5W `메뉴` → ccNC 바의 `≡`가 순정 내비 메뉴를 연다(`com.hkmc.intent.action.ACTION_NAVI_MENU`).
  - 5W `경로 종료` → ccNC 바 중앙의 `✕`(`com.mobis.intent.action.CANCEL_ROUTE_SHORTCUT`).
  - 5W `남은 거리 / 시간 / 목적지` → ccNC 바 중앙의 `거리 · 시간 · 목적지 ⓘ`. 값은 내비 broadcast(`com.mnsoft.navi.action.Route_Guide_Status`)에서 읽는다.
  - 5W `ⓘ 목적지` → ccNC 바의 `ⓘ`(`com.hkmc.intent.action.Request_Info_Destination`).
  - 5W 오른쪽 끝 출구 버튼 → 대응 항목을 찾지 못했다. 내비 안에서만 쓸 수 있는 것으로 보고 대응하지 않았다.

## 4. 우리 쪽에 남는 판단

1. **ccNC look을 유지할까, 5W 메뉴줄처럼 만들까.** 유지하면 테마 안과 밖이 이어지고, 바꾸면 순정 내비와 한 덩어리로 보인다. 지금은 유지 상태다.
2. **중앙 알약에 길안내 정보를 채울 수 있는가.** 0.4.7에서 내비 broadcast(`Route_Guide_Status`의 `DestDistance`·`DestRemainTime`·`Address`·`PoiName`)를 읽는 경로를 붙였다. 값이 실제로 들어오는지는 실차 확인이 남았다.
3. **우리 집·회사.** 5W 검색 화면의 '우리집/사무실' 탭을 여는 방식으로 먼저 붙이는 쪽이 순정 코드 경로를 덜 헤맬 수 있다. 0.4.6 조사에서 순정 내비의 전체검색·현재 위치·좌표 경로 검색 코드는 이미 찾았다. 내비 ODEX에는 `com.mnsoft.navi.action.extra.registpoint`(등록 지점)과 `com.hkmc.intent.action.ACTION_ROUTE_SEARCH`도 보인다.
4. **왼쪽 세로 버튼과 상단 시계**는 순정 내비 것이므로 테마에서 다시 만들 대상이 아니다. 테마가 건드릴 자리는 하단 표시줄과 중앙 알약, 미디어 상자다.
5. **순정 줄을 덮으면 순정 내비의 출구 버튼이 안 눌릴 수 있다.** 내비 안에서 다른 길로 나갈 수 있는지 확인해야 한다.

## 5. 확인하지 못한 것

- 두 플랫폼의 실제 지도는 같은 공급자를 쓰는지, 렌더링 엔진과图层 구성이 같은지.
- 5W 쪽 ccNC 기능의 지원 범위(온라인 지도,live 주차,AR).
- 실제 차량 화면에서의 표시 크기·두께. 위 비교는 공식 매뉴얼 이미지 기준이다.
