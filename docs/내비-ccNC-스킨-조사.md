# 순정 내비를 ccNC처럼 보이게 하기 — PC 조사 (2026-11-22, Claude)

순정 내비(`com.mnsoft.navi`, 차에서 받은 앱을 풀어 본 것)와 프레임워크 클러스터 라이브러리를 보고 정리했다.
차에는 아무것도 보내지 않았다.

## 결론

| 방법 | 가능 여부 | 이유 |
|---|---|---|
| 내비 앱·지도 그림 파일을 고쳐 넣기 | 하지 않음 | 시스템 앱(순정 서명)이라 시스템 영역 변경 필요(승인 필요), 업데이트 때 덮어써짐. 지도 색은 `/storage/navi/navi`의 지도 데이터 안(네이티브 렌더러). |
| 내비 흰색 테마(ccNC 설명서 그림처럼 밝은 지도) | 차 설정에 달림 | 내비는 시스템 낮/밤 모드(`uimode`)를 따라 흰/검은 테마를 고른다. 단, 빌드 기능 `USE_ECOGUI_COMMON`이 켜져 있고 `USE_NIGHT_ONLY`·`USE_ADVANCEDUI_HYUNDAI`가 꺼져 있을 때만. 이 값은 프레임워크(`com.mobis.config.MobisBuildFeature`)에 있는데 이번 수집에서 프레임워크를 다 못 받아 확인 못 함. |
| 회전 안내(TBT)·차로 안내를 우리가 다시 그리기 | 불가 | 내비는 TBT를 네이티브 → `com.hkmc.cluster.NavigationCluster`로 클러스터에만 보낸다. `IClusterService`의 리스너(`INavigationClusterListener`)는 클러스터→내비 응답(배터리·DTE·제한속도 SLIF 등)만 받고 TBT는 돌려주지 않는다. 공개 방송도 없다(`Route_Guide_Status`·`DestinationInfo`·`NaviCameraAlertState` 정도). |
| 내비가 그린 요소 위에 ccNC 모양 "덮개"를 얹기 | 가능(제한적) | 아래 참고. |

## 덮개 방식

- 덮개 창을 **터치 통과**(`FLAG_NOT_TOUCHABLE`)로 띄우면 모양만 ccNC로 보이고, 누르면 아래 순정 버튼이 그대로 동작한다. 다른 앱에 터치를 주입할 권한은 필요 없다.
- 내비 상태는 이미 쓰는 `IInterfaceService`로 알 수 있다: `isMapView_State`(지도 화면인지), `getNaviDrawViewState`, `getStateFromNavi`(경로 상태). 메뉴·검색 화면에서는 덮개를 숨긴다.
- 덮을 수 있는 것: 왼쪽 줄의 홈·`+`·`−`(모양 고정), Nav 음성(음소거 방송으로 상태를 앎), 시계(우리 데이터로 ccNC 글꼴), 하단바(이미 함).
- 덮으면 안 되는 것(내비만 값을 앎): 나침반 방향·2D/3D, 축척(`Auto 50 m`), 오른쪽 위 회전 안내판, 차로 안내, 제한속도 표지. 덮으면 값이 틀려진다.
- ccNC 설명서(`D-map_guidance.png`) 왼쪽 줄: 검색·나침반·Nav 음성·Auto 축척·Scale. 5W: 홈·나침반·Nav 음성·`+`·Auto 축척·`−`. 배치가 거의 같아 모양(둥근 사각, 색)만 다르다.

## 다음에 차에서 (한 번에)

1. `tools/car_capture.ps1`을 다시 돌려 `/system/framework`를 끝까지 받기 → `MobisBuildFeature` 값으로 흰 테마 가능 여부 확인.
2. 길안내 중 내비 화면 캡처(지도·메뉴·검색) → 덮개 위치 실측.
3. 덮개 시험판은 1·2 결과를 보고 만든다.

## 0.4.53에서 만든 것

덮개 시험판(`assets/naviskin.html`). 자세한 내용은 `AI_HANDOFF.md` 0.4.53.
