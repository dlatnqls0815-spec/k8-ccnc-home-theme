# K8 Gen5W 홈

## 0.4.41 모든 상세 화면 열을 하단 바에 맞춤 (2026-11-22, Claude)

- 사용자 지적: 사운드 화면의 왼쪽 목록·오른쪽 그림이 아래 뒤로·홈·메뉴 조각(0~24.4%)과 재생 상자(75.2%~)의 경계와 어긋남.
- `index.html` 끝에 공용 열 규칙: 왼쪽 열은 화면 폭 기준 24.4vw에서 끝나고(2.2vw 여백 안쪽 `--k8-left: 22.2vw`), 오른쪽 열은 75.2vw에서 시작(`--k8-right: 22.6vw`). 적용: 사운드, 설정 상세(일반·디스플레이·차량 등), 설정 첫 화면 4칸 격자(첫 칸 끝 463, 마지막 칸 시작 1449, 사이 간격 10px이 경계를 걸침), 미디어, 전화, 음성 메모, 검색(키보드를 75.2vw까지 넓히고 결과 목록을 오른쪽 열로).
- 페이지 여백을 `2.2%`에서 `2.2vw`로 바꿔, 오른쪽 분할 위젯이 열려 본문 폭이 줄어도 왼쪽 열이 42~468px을 유지한다. 분할 위젯은 정확히 오른쪽 열(1444~1878)을 채우고, 열려 있는 동안 미디어 앨범 상자·전화 기기 칸은 숨긴다(`#view-detail.split-on`).
- 측정(1920×720): 사운드·설정 상세·미디어·메모 왼쪽 열 42~468, 가운데 468~1444, 오른쪽 1444~1878. 화면구성은 원래 맞아 있었다. 기존 검사 전부 통과.

## 0.4.40 앰프 설정 읽기, 후석 취침·음소거 상태 아이콘 (2026-11-22, Claude)

- `StockVehicle`에 `MicomAudioManager` 값 반환 조회만 추가: `getUserMuteState`(순정 음소거), `getRearSleepMode`(후석 취침), `getActiveSoundDesign`, `getAutoAdjustingVolumeSetting`, `getDimVolumeWhileInReverse`, `getNaviDuringCall`. 같은 클래스의 `getBtVolume()`·`getNaviVolume()` 등 반환값 없는(void) 메서드는 앰프에 요청을 보내는 것이라 쓰지 않는다.
- 사운드 화면: 고급 음향에 능동 사운드 디자인·자동 음량 조절, 안내음에 통화 중 내비 안내, 운전자 보조에 후진 시 음량 줄임, 새 항목 "후석 취침 모드". 켜짐/꺼짐으로 보이고, 값이 바뀌면 화면을 다시 그린다(미디어 음량 화면 제외).
- 시계 옆: 후석 취침이 켜져 있으면 달 아이콘, 순정 음소거가 켜져 있으면 음량 0이 아니어도 음소거 아이콘.
- 그 밖에 본 것: 내비 `RouteSearchState`는 경로 시작/종료 알림으로 기존 `Route_Guide_Status`와 겹치고, `SOUND_ON/OFF`는 내비가 받는 쪽이라 넣지 않았다. `DisplaySettingFwManager`·`ThemeSettingFwManager`에는 쓸 조회값이 없다.
- 확인: `verify-0440.cjs`, 기존 검사 전부 통과.

## 0.4.39 하이브리드 에코 점수·CO₂ 절감·에너지 사용, 날씨 오류 (2026-11-22, Claude)

- `StockHybrid`가 서비스가 이미 들고 있는 배열을 읽는다(요청 송신 없음, IHEVService 조회만): `getMyDrivingMode()`(`MyDrivingModeIndex` 0 에코 점수), `getCO2Rdc()`(0 거리, 1 CO₂ 절감), `getDrivingHistory()`(0 구동, 1 발전, 2 공조, 3 전장), `getPwrConsumption()`. 시작할 때와 에너지 흐름 상태가 바뀔 때마다 다시 읽는다. 컴파일용 껍데기에 메서드 4개 추가(APK에 `com/hkmc` 클래스 0개 확인).
- 설정 → 차량에 에코 운전 점수(0~100일 때만), CO₂ 절감, 에너지 사용, 소비 전력 값 줄. 단위는 차에서 확인 중이라 숫자 그대로 보인다. 일부는 전기차·PHEV 전용 값일 수 있다.
- `StockWeather`: `weather_info_unavailable`/`weather_update_error`(순정 분할 화면이 듣는 방송)를 받으면 마지막 값은 두고 분할 화면 날씨 위젯에 "날씨 서비스 사용 불가"/"날씨 갱신 실패"를 붙인다.
- 그 밖에 본 것: `VehicleOptionManager`는 CPU 변형 코드뿐, `NModeManager`는 험로 모드(K8 해당 없음), `ON_HEV_STATUS_CHANGED`는 순정 분할 화면의 전기차 전용 길이라 넣지 않았다.
- 확인: `verify-0439.cjs`, 기존 검사 전부 통과.

## 0.4.38 남은 순정 앱 타일, 키보드·음성인식 중 모서리 숨김 (2026-11-22, Claude)

- 차에서 뽑은 `hmgapi.log`(앰비언트 저장소 `private/vehicle-usb`)의 설치 앱 목록과 대조해, 테마에 바로가기가 없던 순정 앱을 전체 메뉴에 넣었다: 빌트인 캠(`com.daudio.app.dvrs`), 하이브리드(`com.hkmc.daudio.app.hev2`), DMB(`com.daudio.av.app.dmb`), USB 동영상(`com.daudio.av.app.usbvideo`), 음악 스트리밍(`com.daudio.app.musicstreaming`), 후석 케어(`com.android.rear_care`), 주차 위치(`com.daudio.app.findmyvehicle`, 이름은 패키지 뜻으로 붙임), 테마(`com.android.themesettings`). 전체 메뉴가 4페이지가 됐다.
- 타일 동작은 `pkg:<패키지>` → `HomeActivity`가 실행 진입점이 있는 설치 앱만 연다. 없으면 "이 차에서 해당 순정 앱을 열 수 없습니다".
- `CornerService`: 순정 한글 키보드(`com.android.inputmethod.Korean.action.showwindow/hidewindow`), 내비 키보드(`com.mobis.intent.action.navi_keyboard_show/hide`), 음성인식(`VR_START/VR_FINISH`, `start_dialog_vr/end_dialog_vr`) 중에는 떠 있는 모서리 조각을 숨긴다. 닫힘 방송을 놓쳐도 2분 뒤 다시 나온다. 이름은 순정 분할 화면이 듣는 방송에서 가져왔다.
- 확인: `verify-0438.cjs`, 메뉴 검사(3페이지 6칸으로 기대값 갱신) 등 기존 검사 통과. 차에서 볼 것: 새 타일 8개가 각각 열리는지, 주차 위치 앱의 실제 이름, 키보드가 뜰 때 모서리가 숨는지.

## 0.4.37 휴대폰 신호·배터리, 주행 모드 (2026-11-22, Claude)

- 순정 분할 화면(`SplitScreenHome`, 수신기 `c`, `f/f`)이 쓰는 Broadcom 핸즈프리 방송을 받는다: `MOBIS_BT_SIGNAL_STRENGTH`(`STRENGTH`), `MOBIS_BT_BATTERY_LEVEL`(`LEVEL`), 값 0~5로 보고 -1은 지운다. 시작할 때 순정 분할 화면처럼 `com.mobis.intent.action.GetBatteryLevel`을 보내 배터리 값을 다시 받는다(조회 요청, 차량 명령 아님). 통신사 이름(`CARRIER`)은 쓰지 않는다.
- 차량 설정 `DriveModeHandleService`의 `DRIVE_MODE_CHANGED`(`DriveMode` = `Dic$MODE` 이름: SMART, ECO, COMFORT, SPORT, INDIVIDUAL, SNOW…)를 받아 한글 이름으로 보여 준다. 처음 값은 바뀔 때까지 없다.
- 화면: 시계 옆에 ccNC 상태 줄처럼 휴대폰 신호 막대 4칸과 배터리(1 이하면 주황). 설정 → 차량에 주행 모드·휴대폰 신호·배터리 줄. 분할 화면 차량 위젯과 에너지 흐름 위젯에 주행 모드.
- 확인: `verify-0437.cjs`, 기존 검사 통과. 차에서 볼 것: 신호·배터리 값 범위가 0~5인지, 주행 모드 버튼을 눌렀을 때 `K8StockVehicle event …DRIVE_MODE_CHANGED`가 찍히는지.

## 0.4.36 차량 상태 방송, 연료 부족 알림, 주변 주유소·주차장 (2026-11-22, Claude)

- `StockVehicle`이 순정 상태 방송을 받는다(읽기만): `SYSTEM_STATE_ILLUMINATE_ON/OFF`(미등·전조등), `SYSTEM_STATE_DRIVE_ON/OFF`(주행), `SYSTEM_STATE_FUELWANING_ON/OFF`(연료 경고, 순정 철자 그대로), `VALET_ON/OFF`(발레 모드), `com.mobis.intent.action.WPC_STATUS_CHANGED`(`WPC_STATUS`, 무선 충전기 경고). 이름은 `ModeManager`·`PgsConstants`·`VehicleInfoManager` 상수에서 가져왔다.
- 새 읽기: `VehicleInfoManager.getGearPStatus/getAvTailStatus/getRheostatLevel/getAvailableRheostatLevel/getEcoCoastingStatus/getWirelessChargingStatus`, `MicomPowerManager.isDisplayOffMode`. 무선 충전 값 7은 응답 없음이라 표시하지 않는다.
- 화면: 설정 → 차량에 기어, 주행 상태, 미등·전조등, 실내 조명 밝기, 연료 경고, 발레 모드, 에코 관성 주행, 무선 충전기 줄. 시계 옆에 발레 모드 아이콘과 주황 연료 아이콘. 연료 경고가 켜지면 위쪽에 ccNC식 알림 "연료가 부족합니다 [주변 주유소] [닫기]"가 경고 한 번에 한 번 뜬다.
- 내비 화면 왼쪽 도구 줄에 주변 주유소·주변 주차장 버튼. 내비 `NaviETCReceiver`의 `ACTION_INFO_PETROLSTATIONS`/`ACTION_INFO_PARKING`을 보낸다(내비가 앞에 있으면 내비가 무시하므로 내비를 띄우기 전에 보냄). 1.5초 뒤에도 내비가 앞에 없으면 내비 검색 화면을 연다. 이 방송은 내비 코드에 "TomTom phase 2"로 적혀 있어 국내판에서 동작하는지는 차에서 확인한다.
- 확인: `verify-0436.cjs`(아이콘·알림·버튼 동작), 기존 차량·상태 아이콘·블루라이트·하이브리드·분할·날씨·모서리 검사 통과. 차에서 볼 것: `logcat -s K8StockVehicle`의 `event …` 줄, 무선 충전 값의 뜻, 주변 주유소 버튼이 목록을 여는지.

## 0.4.35 시계 옆 상태 아이콘 (2026-11-22, Claude)

- ccNC 순정 화면(사용자 실차 사진)처럼 시계 왼쪽에 글자 없는 상태 아이콘: 블루투스 전화 연결(통화 기기 있음), 통화 중(전화 상태 있음, 초록), 음소거(음량 0). 기존 데이터(`phoneChanged`, `controlsChanged`)만 사용, 홈과 상세 화면 모두 표시. 숨겨져 있던 `BT 상태 대기` 글자 배지는 그대로 숨김.
- 배포 `deliverables/K8-5W-홈-0.4.35.apk`, `K8-ccNC-Home-NEMESIS-0.4.35-USB.zip`. 이전 검사와 상태 아이콘 시험 통과.

## 0.4.34 순정 블루라이트 상태, 남은 통로 조사 결과 (2026-11-22, Claude)

- `BluelightManager`(automotive.odex, `BluelightConstants`: 모드 0 자동·1 예약, `isEnable` 0/1)의 켜짐·모드·단계·예약 시간을 `StockVehicle`이 함께 읽는다(짧은 글자 값도 읽도록 확장). 설정 → 디스플레이 → 블루라이트에 `순정 블루라이트` 줄, 순정이 켜져 있으면 테마 효과와 겹친다는 안내.
- 조사했지만 넣지 않은 것: `homeaidl`의 `IHomeServiceManager`·`IHome*Listener`는 분할 화면 허브처럼 `onDoCommand(int)`만 있는 명령 통로(데이터 아님). `DVRSManager`(빌트인 캠)는 11개 숫자 콜백뿐이고 순서 뜻 미상. `DisplaySettingFwManager`는 `isAdmEnable`/`isRhstaSwista`뿐. `RegulationActivityManager`는 생성자가 비공개. `FavoriteSongManager`는 화면에 쓸 곳 없음.
- 배포 `deliverables/K8-5W-홈-0.4.34.apk`, `K8-ccNC-Home-NEMESIS-0.4.34-USB.zip`. 이전 검사와 블루라이트 시험 통과.

## 0.4.33 차량 상태 전체, 운전자, 시작 오디오 모드 (2026-11-22, Claude)

- `StockVehicle.java`: 자동차 라이브러리 관리자(`TMUManager`, `BCMManager`, `USMManager`, `ModeManager`, `UserProfileManager`)를 반사로 만들어 값이 있는 무인자 getter만 3초마다 백그라운드 스레드에서 읽는다(서비스 거부·클래스 없음이면 그 값만 빠짐, 같은 경고는 한 번만 기록). 개인 식별 정보 getter는 목록에 없음.
- 화면: 설정 → 차량에 속도·연료량·누적/구간 주행거리·평균 연비·이번 주행 시간·앞문·문 잠금·트렁크·후드·선루프·창문·주차 브레이크·운전석/조수석/핸들 열선·엔진·엔진 회전수 줄. 분할 화면 `차량 정보` 위젯(열림 경고 우선, 연료·주행 가능 거리·구간·평균 연비). 홈 왼쪽 위 운전자 이름을 순정 프로필(게스트 / 사용자 종류 1·2)에 맞춤. 미디어 위젯 방송 전에는 `ModeManager.getCurrentMode`로 시작 소스.
- 가정(차에서 확인): 열림·잠김·열선은 0이 닫힘/꺼짐, `getDriveTime`은 초, `getFuelTankRatio`는 %, 사용자 종류 1/2가 운전자 1/2. `logcat -s K8StockVehicle`의 `connected …`와 `status {...}`.
- 전체 연결 현황과 일부러 뺀 것: [펌웨어 분석 4번](펌웨어-분석-2026-11-22.md).
- 배포 `deliverables/K8-5W-홈-0.4.33.apk`, `K8-ccNC-Home-NEMESIS-0.4.33-USB.zip`(SHA-256 `cad81199991e3cf2fc946cdeee45f925bee53758fd7cb83e5f38dbc9416a2e8c`). 이전 검사 전부와 차량 상태 시험 통과.

## 0.4.32 오디오 모드 전환 즉시 반영, 주행 가능 거리 (2026-11-22, Claude)

- 근거: `automotive.odex` `ModeManager.ACTION_MODE_CHANGED` = `com.hkmc.intent.action.MODE_CHANGED`, `EXTRA_MODE_TYPE` = `com.hkmc.mode.EXTRA_MODE_TYPE`, 값은 `ModeConstants$ModeType`(FM1 0x2, FM2 0x3, AM 0x4, USB 0x21, USB_AUDIO 0x31, BLUETOOTH 0x36, DIPO 0x73, NATURE_SOUND 0x81, NAVI 0x60, CLIMATE 0x76 …).
- 구현: `StockMedia`가 이 방송을 받아 오디오 소스가 바뀌면 곧바로 새 소스(FM/AM/블루투스 오디오/USB 음악/자연의 소리/CarPlay)로 바꾼다. 곡명 등은 이어서 오는 위젯 방송이 채운다. 오디오가 아닌 모드(내비·공조 등)는 무시. 홈·하단바·모서리 미디어 상자 모두 같은 클래스라 함께 적용.
- 설정 → 차량에 `주행 가능 거리`(TOTAL_DTE), `연료 주행 가능 거리`(GAS_DTE) 줄(0.4.27부터 읽던 값, 단위 km 가정).
- 차에서 볼 것: 소스를 바꿀 때 미디어 상자가 바로 바뀌는지, `EXTRA_MODE_TYPE`이 정수로 오는지, 주행 가능 거리 숫자가 계기판과 같은지.
- 배포 `deliverables/K8-5W-홈-0.4.32.apk`, `K8-ccNC-Home-NEMESIS-0.4.32-USB.zip`(SHA-256 `dc5b676780fe5c37e0e04497bf5fa35067bd352493e06a6a5ba7a636f3eab30e`).

## 0.4.31 현재 위치를 ccNC처럼 "○○시 ○○구 부근" (2026-11-22, Claude)

- 근거(내비 odex): `com.mnsoft.navi.action.Request.NaviAddressInfo`(`NaviETCReceiver`, extras `com.mnsoft.navi.action.extra.lat`/`lon`/`addressdisp` 모두 `getDoubleExtra`) → 답 `com.mnsoft.navi.action.Response.NaviAddressInfo`(`NativeCall.sendNaviInsideData`): `admincode`, `lat`, `lon`, `countrycode`, `language`, `address.step1`~`step4`.
- 구현(`StockRoute`): `NAVI_GPS_CHANGED`의 `LAT`/`LON` 원값을 한국 범위에 들어오는 배율(1, 360000, 1e6, 1e7, 1e5)로 도 단위로 바꾸고, 1분마다 주소 요청. 답의 step1+step2 + " 부근"을 현재 위치로 쓰고(5분 지나면 버림), 없으면 기존 도로명. 홈 지도 카드·분할 지도·순정 내비 하단바가 모두 이 값을 쓴다. 배율 변환은 JVM 단위 시험(소수, ×1e6, ×1e7, ×360000, 문자열, 0) 통과.
- 같은 분배기(`sendNaviInsideData`)에서 본 다른 방송: `NaviCameraAlertState`(`CamAlertState`·`Time_ms`, sticky, 값 뜻 미상 → 안 씀), `com.mnsoft.navi.intent.action.road.roadClassification`(도로 등급), `Navi_View_Status`(분할 화면 표시 상태), `navi_ev3_dte_status`.
- 차에서 볼 것: `logcat -s K8StockRoute`의 `area reply ok keys=[...]`. 답이 안 오면 GPS 배율이 틀렸거나 답이 내비 안에서만 쓰이는 것.
- 배포 `deliverables/K8-5W-홈-0.4.31.apk`, `K8-ccNC-Home-NEMESIS-0.4.31-USB.zip`(SHA-256 `f04b5b89f619e0269fb15dcff9eefff64fe6e19bedbe3319b085cc6118dd8b10`).

## 0.4.30 순정 날씨 (2026-11-22, Claude)

- 근거(내비 odex): 내비가 `com.hkmc.intent.action.request_weather_from_navi`를 방송하면(`NativeCall` "VIT_REQ_WEATHER_INFO(5303) req weather info to system(weather app)") 순정 날씨(기아 커넥트) 쪽이 `com.hkmc.intent.action.response_weather_to_navi`로 답한다(`NaviETCReceiver`). 값: `com.hkmc.extras.weather.navi_result`(참/거짓), `navi_temp`(실수), `navi_weathertype`, `navi_sunrise`/`navi_sunset`(글자), `navi_precipitation`(정수).
- 구현: `StockWeather.java`가 같은 요청을 20분마다 보내고 답을 받는다. 분할 화면 `날씨` 위젯은 순정 기온 → 없으면 공조 바깥 기온 순. 대기 화면 날씨 칸(차에서는 숨겨져 있던 것)에 기온·현재 도로·일출·일몰.
- 확인 못 한 것: 답 방송이 내비 패키지로만 보내지는지(그러면 테마가 못 받음), 날씨 종류 번호의 뜻, 강수 값이 %인지. 차에서 `logcat -s K8StockWeather`의 `weather {...} keys=[...]`.
- 배포 `deliverables/K8-5W-홈-0.4.30.apk`, `K8-ccNC-Home-NEMESIS-0.4.30-USB.zip`(SHA-256 `64c01ce76629d7e060b173ab016560c4a8a8ee92c4aee14aed155b787fac3d39`). 이전 검사 전부와 순정 날씨 시험 통과.

## 0.4.29 후방 카메라 위에는 아무것도 띄우지 않음 (2026-11-22, Claude)

- 근거: `automotive.odex` `com.hkmc.mode.ModeManager`의 `ACTION_SYSTEM_STATE_REARVIEW_ON/OFF` = `com.hkmc.intent.action.SYSTEM_STATE_REARVIEW_ON` / `_OFF`.
- `CornerService`는 켜짐 방송을 받으면 두 조각을 바로 떼고 꺼짐 방송까지 다시 붙이지 않는다(이름 기반 숨김 목록은 그대로 둠). `NavBarService`는 켜짐 방송에서 세션을 끝내 순정 하단줄로 돌아간다.
- 전화 상태는 기존 `com.hkmc.phone.widgetupdate` 등으로 이미 연결돼 있어 손대지 않음.
- 차에서 볼 것: 후진 기어에서 방송이 오는지와 두 조각·하단바가 사라지는지, 후진을 풀면 두 조각이 돌아오는지.
- 배포 `deliverables/K8-5W-홈-0.4.29.apk`, `K8-ccNC-Home-NEMESIS-0.4.29-USB.zip`(SHA-256 `f8fd05d8a606ea816e4a56045919c7459dcef9010234e776a481ad01fee44060`).

## 0.4.28 우리 집·회사 바로 안내 (2026-11-22, Claude)

- 다른 세션이 만든 펌웨어 복호화 도구·자료(`fw_decrypt.py`, `fw_key_pull.sh`, `FIRMWARE_DECRYPT.md`, `CCNC_5W_KNOWLEDGE.md`, `ccnc-5w-data/` 102MB)는 사용자 요청으로 `private/firmware/`로 옮김(제조사 키 포함이라 공개 저장소 금지).
- 내비 저장 지점은 `content://com.mnsoft.navi/RegistPnt_*`에 있지만 내비 targetSdk 19라 제공자가 비공개 → 다른 앱이 못 읽음.
- 대신 `DestinationInfo` 방송의 `LAT`/`LON`/`registpoint`를 `StockRoute`가 기억(경로 종료 시 지움). 홈 지도 카드의 우리 집·회사 버튼: 저장된 곳이 없으면 "지금 목적지 저장", 있으면 내비 공개 API `com.hkmc.intent.action.ACTION_ROUTE_SEARCH`(`com.hkmc.navi.EXTRA_LATITUDE`/`EXTRA_LONGITUDE`는 도 단위 문자열 — 내비가 `Double.valueOf` 후 ×360000, `EXTRA_KEYWORD` 이름)로 바로 경로 검색 후 내비 열기.
- 차에서 볼 것: `logcat -s K8StockRoute`의 `destination info lat=set type=… registpoint=…`(좌표가 오는지, 우리 집/회사일 때 registpoint 값), `K8StockNavigation`의 `route search sent`, 내비가 경로 화면을 띄우는지. 좌표가 내비 내부 단위(×360000)로 오면 `StockNavigation.degrees`가 자동 변환.
- 내비 `IInterfaceService` 메서드 목록은 [펌웨어 분석](펌웨어-분석-2026-11-22.md) 1번에 정리됨(지식 문서의 "모르는 것 3번" 해결).
- 배포 `deliverables/K8-5W-홈-0.4.28.apk`, `K8-ccNC-Home-NEMESIS-0.4.28-USB.zip`(SHA-256 `312ca1c9c46010b8e3b342d3fb8fe448594c33459ac4a85383ad4d89fad60e54`). 이전 검사 전부 통과.

## ccNC / 5W 지식 문서 (다른 AI에 넘길 정리)

**화면 구성·차량 API·내비 데이터 구조·펌웨어 상태를 한 장에 모은 문서가 [CCNC_5W_KNOWLEDGE.md](CCNC_5W_KNOWLEDGE.md)다.**
다른 AI(Claude 등)에 넘길 때는 이 문서와 `deliverables/CCNC-5W-지식자료.zip`(원본 데이터·근거·도구 포함)만 주면 된다.
추출된 원본: `docs/ccnc-5w-data/`(내비 설정 JSON·POI 분류·충전소 DB 41,190건), `docs/images/nav-compare/`(공식 화면),
`docs/vehicle-api.json`(차량 앱 20개 매니페스트), `docs/vehicle-nav-strings.txt`(내비 dex 문자열표).
주요 발견: 길안재 값은 `com.hkmc.telematics.gis.action.NAVI_GPS_CHANGED`의 `com.hkmc.telematics.gis.extra.DestDistance/DestRemainTime`(숫자, 매초),
경로 상태는 `com.mnsoft.navi.action.Route_Guide_Status`(sticky, `ROUTE_GUIDE_STATE`), 목적지는 `com.mnsoft.navi.action.DestinationInfo`(`PoiName`/`Address`/`registpoint`).

## 0.4.27 하이브리드 에너지 흐름 (2026-11-22, Claude)

- 근거: `automotive.odex`의 `com.hkmc.hev.HEVManager`(읽기: `getHEVStatus(int)`, 리스너 `HEVListener.onHEVStatusChanged(flag, value)`), 플래그 `HEVManager$HEVFlag`(HEV_SYSTEM_MODE 0x6, DISPLAY_SOC 0x19, ELECTRIC_MOTOR_USAGE 0x5, AVG_FCU 0x1, AVG_FCI 0x2, ECO_LEVEL 0x8, HEV_RDY 0x1a, GAS_DTE 0xa, TOTAL_DTE 0xb). 순정 분할 화면(`RightScreenHome` `z/a`)의 12×7 표 `UD`로 시스템 모드별 흐름을 풀었다: 열 0 엔진↔바퀴(2 엔진→바퀴, 3 바퀴→엔진), 1 모터↔바퀴(2 바퀴→모터, 3 모터→바퀴), 2 엔진→모터(2), 3 모터↔배터리(2 모터→배터리, 3 배터리→모터), 4 엔진 그림, 5 모터 그림(0 꺼짐, 1 활성, 2 구동), 6 배터리 활성. 배터리 그림은 0~8칸.
- 모드 이름(표에서 추정): 0 준비, 1 정지, 2 전기 모터 주행, 3 엔진·모터 주행, 4 엔진 주행, 5 엔진으로 충전, 6 회생 제동 충전, 7 엔진 감속, 8 엔진 주행·충전, 9 모터 주행·엔진 충전, 10 회생 제동·엔진 충전, 11 감속 회생 충전.
- 구현: `StockHybrid.java`(읽기 전용, 요청·설정 호출 없음, 라이브러리 참조는 안쪽 클래스에만), 컴파일용 껍데기 `stubs/com/hkmc/hev/*`. 분할 화면에 `에너지 흐름` 위젯(지도 다음, 예전 저장값에도 자동 추가)과 설정 → 차량에 에너지 흐름·배터리 표시·전기 모터 사용·평균 연비 값·하이브리드 준비 줄.
- 확인 못 한 것: 순정 하이브리드 앱이 HEV_SYSTEM_MODE 값을 분할 화면 표 번호로 그대로 넘기는지(가장 큰 가정), DISPLAY_SOC가 칸 수인지 %인지, 모터 사용·연비의 단위. 차에서 `logcat -s K8StockHybrid`로 `hybrid listener added {...}`와 `status flag=0x6 value=...`를 보며 주행 상태와 맞춰 볼 것. `hybrid service unavailable`이면 일반 앱 거부.
- 검사: 빌드·서명(APK에 `com/hkmc` 클래스 0개), 이전 검사 전부와 에너지 흐름 시험(모드 2/4/5/6에서 흐름 선 2/1/2/2개, 화면 오류 없음). 배포 `deliverables/K8-5W-홈-0.4.27.apk`, `K8-ccNC-Home-NEMESIS-0.4.27-USB.zip`(SHA-256 `4715ce53b4cb4b5240ab72e332a79854c9b7278b16dfe9f790211b422a1490ad`).

## 0.4.26 분할 화면 지도에 순정 내비 화면 (2026-11-22, Claude)

- 사용자가 D 드라이브에 ccNC·5W 펌웨어를 받아 둠. 둘 다 시스템 이미지가 제조사 암호화라 풀지 않음. 자세한 분석과 새로 찾은 통로는 [펌웨어-분석-2026-11-22.md](펌웨어-분석-2026-11-22.md).
- 차에서 뽑아 둔 순정 분할 화면(`RightScreenHome`)을 풀어 보니, 내비가 넘겨받은 화면(Surface)에 지도·경로·회전 화살표를 직접 그린다(`IInterfaceService` 22번 생성, 16번 해제, 권한·호출자 확인 없음). 테마 분할 화면의 `지도` 페이지에 같은 방식으로 내비 화면을 올림(`StockMapPreview` split 모드, `HomeActivity.updateSplitBounds`, 페이지 `syncNativeSplitMap`). 누르면 내비, 오른쪽으로 밀면 닫기, 위아래로 밀면 위젯 바꾸기(`K8Home.splitGesture`). 내비 그림이 들어오면 아래 상태 카드는 숨김.
- 검사: 빌드·서명, `verify-integration`, 메뉴 호환 사본, 분할 화면 밀기, 날씨 위젯, 분할 지도 위치 보고·몸짓 시험(화면 오류 없음). 배포 `deliverables/K8-5W-홈-0.4.26.apk`, `K8-ccNC-Home-NEMESIS-0.4.26-USB.zip`(SHA-256 `c23cf94ffaca290669a7b6f4773bcd4b91405738c942d16d6db6aacb07b3acf2`).
- 차에서 볼 것은 분석 문서 1번 끝. 다음 후보: 하이브리드 에너지 흐름(`HEVManager`).

## 0.4.25 하단 모서리 곡률·비율 실측 맞춤, 차 없이 되는 연결 (2026-11-21, Claude)

- 실측(`D-settings_theme_widget.png` 944×354): 왼쪽 조각 0–230(24.4%), 오른쪽 710–944(24.8%), 높이 44(12.4%), 색 #1c1c1c, 위 테두리 한 줄이 살짝 밝음, 가운데 띠 #131313. 미디어 상자의 앨범 칸은 상자 높이만큼 정사각형으로 왼쪽에 붙음(#292d2e). 화면 안쪽 위 모서리가 둥글다(1920 기준 12px로 적용).
- 테마 안: 하단줄 비율 24.4/50.8/24.8, 안쪽 위 모서리 둥글게, 앨범 칸 정사각형(옛 웹뷰용으로 `12.5vh`). `▷ Off`는 공식처럼 사각 테두리 안 ▷. 화면구성·설정 상세의 왼쪽 메뉴 칸과 오른쪽 그림 칸을 아래 두 조각과 같은 폭으로 맞춤(바깥 여백 없앰, 메뉴 글자 2.9% 안쪽). 분할 화면 위젯 폭도 24.8%. 화면구성 ≡ 손잡이를 공식 크기로 키움.
- 순정 화면 위 조각(`CornerService`): 창 폭 24.4%/24.8%, 창을 투명하게 하고 페이지가 둥근 조각을 그림.
- 차 없이 연결한 것: 내비 화면 하단 가운데(경로 없을 때)가 `길안내 정보 연결 대기` 대신 0.4.21의 현재 위치(없으면 `목적지 검색`). 분할 화면 `날씨` 위젯이 0.4.22 공조의 바깥 기온과 운전석·조수석 온도, 에어컨 상태를 보여 줌(예보는 인터넷이 필요해 없음). `스포츠`는 인터넷이 필요하다고 사실대로 표시.
- 검사: 빌드·서명, `verify-integration`, 메뉴 호환 사본, 분할 화면 밀기, 공조 표시, 모서리 페이지, 날씨 위젯·내비 현재 위치 시험 모두 화면 오류 없음. 배포 `deliverables/K8-5W-홈-0.4.25.apk`, `K8-ccNC-Home-NEMESIS-0.4.25-USB.zip`(SHA-256 `3344a2e820a3f6f8cdb67c5cea3bed84644b2de9b71783ff4b2706d0ae67acfd`).

## 0.4.24 하단 모서리 두 조각을 순정 화면에도 (2026-11-21, Claude)

사용자 확인: ccNC는 상세 화면마다 왼쪽 아래 `‹ ⌂ ≡`과 오른쪽 아래 현재 재생 상자가 **항상 떠 있다**(순정 설정 화면 사진). 홈 화면에는 없다(공식 홈 그림).
- 테마 안: 상세 화면 하단줄의 가운데가 비어 있으면 투명(`.bottom-bar.no-center`)하게 바꿔 두 조각만 떠 보이게 함. 미디어가 없을 때 상자는 공식처럼 `▷ Off`(사각 테두리 안 재생 표시).
- 순정 화면 위: 새 `CornerService` + `assets/corners.html`. 화면의 24%×12.5%(왼쪽)와 26%×12.5%(오른쪽) 창 두 개를 `TYPE_PHONE`으로 띄운다. 0.4초마다 맨 위 화면을 보고 테마, 런처(HOME 앱), 순정 내비(내비는 `NavBarService` 몫), 이름에 phoneprojection·carplay·androidauto·camera·rvm·avm·pdw가 들어간 화면에서는 숨긴다.
- 버튼: ⌂ → 테마 홈, ≡ → 테마 전체 기능, 미디어 상자 → 재생 중인 순정 플레이어(없으면 테마 미디어 화면). ‹ 는 일반 앱이 다른 앱에 뒤로 키를 보낼 수 없어서(0.4.8에서 키 도우미 제거) **바로 전에 쓰던 화면을 앞으로**(`moveTaskToFront`, `REORDER_TASKS` 권한 추가) 가져온다. 순정 앱 안의 한 단계 뒤로는 순정 화면의 뒤로 화살표를 쓴다.
- 설정: 설정 → 일반 → `순정 화면에도 하단 버튼 띄우기`(기본 꺼짐). 켜면 시동 후 `BootReceiver`와 테마 시작 때 서비스가 다시 뜬다.
- 검사: 빌드·서명(같은 인증서), `verify-integration`, 메뉴 호환 사본, 분할 화면 밀기, 공조 표시, 모서리 페이지(버튼 3개·미디어 상자 동작, 화면 오류 없음). 배포 `deliverables/K8-5W-홈-0.4.24.apk`, `K8-ccNC-Home-NEMESIS-0.4.24-USB.zip`(SHA-256 `e149c4cecdd135a47736a4b87f1bd9f682820a63dbab959e30d60391780a3f62`).
- 차에서 볼 것: 순정 설정·라디오·블루투스 화면에서 두 조각이 뜨는지(`logcat -s K8HomeCorners`에 `overlay window was refused`가 없어야 함), 순정 화면 아래 모서리의 순정 버튼을 가리지 않는지, 후방 카메라·CarPlay·안드로이드 오토에서 숨는지(숨지 않으면 그 화면의 패키지 이름을 숨김 목록에 추가), ‹ 가 직전 화면으로 가는지.

## 0.4.23 공식 매뉴얼 화면에 맞춤 (2026-11-21, Claude)

차이 목록은 [ccNC-디자인-차이.md](ccNC-디자인-차이.md). 이번에 고친 것:
- 글꼴: 굵기 600~900을 모두 500으로(맑은 고딕에서는 보통 굵기로 보임). `strong/h1~h3`도 500.
- 기본 배경을 ccNC 검정으로. 예전 저장값(`design` 표시 없음)은 한 번 검정으로 바뀐다. 5W 파란 배경은 설정 → 디스플레이 → 화면 밝기 탭의 `홈 배경` 줄로 옮김. 검정일 때 배경 무늬 투명도 0.
- 홈: 위 가운데 손잡이 막대와 `BT 상태 대기` 숨김(위에서 끌어내리기는 그대로). 아래는 페이지 점 4개만(배경화면·전체 메뉴 글자, 대기 화면 점, 5W 파란배경·화면구성 버튼 숨김). 미디어 카드는 짙은 바탕 + 왼쪽 정사각형 앨범, `미디어` 꼬리표·진행선 없앰.
- 전체 기능: 모든 카드 같은 짙은 회색(#25211f/#181615), 카드별 색·줄무늬·`연결됨` 꼬리표 없앰. 끌 때는 점선 대신 카드가 밝아짐.
- 미디어·전화 아래 탭: 아이콘만(글자 숨김), 아이콘 크게. 미디어 `목록/한 곡/플레이어` 버튼 없앰 → 앨범 그림을 누르면 플레이어, 플레이어의 ☰가 목록. 재생 목록 줄은 번호 + 곡명만. 앨범 그림과 재생 버튼을 크게.
- 분할 화면: 기본 닫힘(예전 저장값도 한 번 닫힘). 하단 오른쪽 미디어 상자를 위로 밀면 열리고 위젯을 오른쪽으로 밀면 닫힘. 오른쪽 끝 손잡이, 제목·▲▼⚙ 머리줄 숨김. ‹ ›는 위젯을 만질 때 2.5초만 보임. 너비 26%(미디어 상자와 같은 줄).
- 퀵 컨트롤: `퀵 컨트롤` 제목 없앰(편집 중에만 `퀵 컨트롤 편집`). 오른쪽 열 아래를 비움.
- 화면구성: 분할 화면 ▲▼ 숨김(≡ 길게 눌러 끌기로 순서 변경), `지도` 줄 흐리게. 디지털 액자 줄 끝 `›`. 안내문을 공식 문장에 가깝게(차에서 실제로 하는 일과 어긋나는 부분은 사실대로: 디지털 액자는 "선택한 이미지", 그래픽 스타일은 "홈 화면"만). 대기 화면 톱니 크게.
- 순정 내비 위 하단바: 경로가 없을 때 가운데 글자가 0.4.21의 현재 위치(없으면 `목적지 검색`).
- 검사: 빌드·서명(같은 인증서), `verify-integration.cjs` 통과. `verify-menu-compat.cjs`는 이제 숨긴 `전체 메뉴 ›` 버튼을 누르려다 멈추므로, 그 단계만 화면 전환 호출로 바꾼 사본(스크래치)으로 통과. 분할 화면 밀어 열기·닫기 시험 통과. 배포 `deliverables/K8-5W-홈-0.4.23.apk`, `K8-ccNC-Home-NEMESIS-0.4.23-USB.zip`(SHA-256 `6fedb7e85409f5a466e894601168bd0763898f0b4bce3de0936ba82391c4aa6d`).
- 남은 차이: 공식 글꼴(얇고 둥근 서체) 파일이 없어 맑은 고딕/차의 기본 글꼴을 쓴다. 화면구성 오른쪽 그림(겹친 화면, 액자), 블루투스 플레이어의 `⌄ 블루투스 오디오` 접기 버튼, 미디어 상자를 짧게 눌렀을 때의 동작(지금은 미디어 화면 열기)은 그대로.

## 0.4.22 공조 값 읽기 (2026-11-21, Claude)

- 근거: `automotive.odex`의 `com/hkmc/datc`. `DATCManager(Context)`, `addDATCListener(DATCListener)`, `getClimateInfo(int DATCFlag)`, `getTemperatureUnit()`. 수신 클래스 `DATCListener`는 일반 클래스(인터페이스 아님)라 상속한다. `onDatcStatus(DatcStatus)`의 `getDrTempC/getPsTempC/getAmbientTempC`(°C 실수), `getAirconOnOff`, `getAuto`, `getMainBlowerLevel`, `getModeDrive`, `getIntake`, `getOperationStatus`; `onTemperatureInformation(...)`도 온도를 실수로 준다. `addDATCListener`는 등록 직후 저장된 상태를 한 번 보내는 것으로 보인다(smali 흐름).
- 번호 뜻(`DATCFlag$DATCConstants`): 켜짐 1/꺼짐 0, 바람 세기 0=꺼짐·2~9=1~8단, 바람 방향 1 얼굴·2 얼굴+발·3 발·4 발+앞유리·5 앞유리·6 자동 김서림, 내기 순환 1/외기 0, 단위 1=°C·2=°F. 바람 세기·방향은 `DatcStatus` 값에도 같은 번호를 쓰는지 차에서 확인 필요.
- 구현: `src/io/github/k8home/StockClimate.java`(읽기만, 명령 송신 없음). 라이브러리 클래스 참조는 안쪽 클래스 `Binding`/`Receiver`에만 있어 라이브러리가 없거나 서비스가 거부해도 테마는 그대로 뜬다. 컴파일용 껍데기 `stubs/com/hkmc/datc/*`는 `build.py`가 컴파일 때만 쓰고 APK에는 안 들어간다(dexdump로 `com/hkmc` 클래스 0개 확인).
- 화면: 설정 → 차량에 운전석·조수석·외부 온도, 에어컨, 오토, 바람 세기·방향, 공기 유입 줄. 값이 하나도 없으면 줄이 숨는다.
- 검증: 빌드·서명(0.4.19와 같은 인증서), 코덱스 검사 2종, 공조 표시 시험(1920×720, 화면 오류 없음). 배포 `deliverables/K8-5W-홈-0.4.22.apk`, `K8-ccNC-Home-NEMESIS-0.4.22-USB.zip`(SHA-256 `f1482aec8184518fa1ca387273104f89b3a720ecff166019103d8a55438f5aec`).
- 차에서 볼 것: `logcat -s K8StockClimate`에 `climate listener added`와 `status dr=...`가 찍히는지. `climate service unavailable`이면 공조 서비스가 일반 앱을 거부한 것(그때는 네메시스 system 도우미로 읽는 방법을 검토). 공조를 바꾸면서 바람 세기·방향 번호가 화면과 맞는지.

## 0.4.21 PC 조사로 찾은 통로 연결 (2026-11-21, Claude)

근거: 차에서 뽑은 `HKMC_Navi_ExSL.odex`(research/k8-ambient-sync/private/nowplaying/apps)와 `automotive.odex`(research/k8-ecs-damping/private/vs)를 smali로 풀어 확인. 풀기 도구는 apktool.jar 안의 dexlib2/baksmali(`DexBackedOdexFile`)로 클래스 전체를 덤프하는 짧은 Java 파일.

넣은 것(PC에서만 확인, 차 미확인):
- **현재 도로명·주소**: `com.hkmc.intent.action.Request_Info_CurrentLoc`를 내비에 보내면 `Response_Info_CurrentLoc`(extras `address` 문자열, `on_road`)이 온다. NaviService가 `Request_Info_Destination`과 같은 필터로 받는다(권한 없음). `StockRoute`가 5초 간격 이상으로 요청하고, 길안내가 없을 때 홈 지도 카드 아래 줄과 우측 지도 위젯에 표시한다. `on_road`의 형식은 로그 `K8StockRoute current location`으로 확인.
- **내비 상태 방송**: `com.mnsoft.navi.action.DAYNIGHT_CHANGED`(`daynight_status`), `android.intent.action.VIT_NOTI_NAVI_MUTE_STATE`(`Value`), `android.intent.action.VIT_NOTI_NAVI_BOOTEND_SERVICE`(`appVersion`, `mapVersion`). 설정 → 일반에 지도 버전·내비 주야간·안내 음소거 줄을 추가. 주야간·음소거 숫자의 뜻은 해석 전이라 "신호 N (해석 전)"으로 보인다. BOOTEND는 내비가 켜질 때만 오므로 테마가 그 뒤에 켜지면 지도 버전이 안 보일 수 있다.
- **순정 앰프 값**: `com.hkmc.media.MicomAudioManager(Context).getStatus()` → `MicomAudioStatus`의 공개 필드 `mTreble`, `mMiddle`, `mBass`, `mBalance`, `mFade`, `mSDVC`, `mSoundPriority`, `mAmpType`. 리플렉션으로 읽어 사운드 화면의 고음/중음/저음·밸런스·속도 연동 음량·안내 우선에 "순정 값 N"으로 표시. 서비스가 일반 앱을 거부하면 이전처럼 "순정 연동 대기"로 남는다. 숫자 단위는 차에서 확인.

찾았지만 못 넣는 것:
- **회전·차선·교차로·제한속도**: 내비가 `com.hkmc.cluster.NavigationCluster`/`ClusterManager`로 계기판에 보내는 한 방향 통로다. `INavigationClusterListener`는 계기판→내비 방향(배터리, DTE, HDA 등)만 있다. 앱 주입 없이는 테마가 읽을 수 없다(주입기 새로 만들기는 하지 않기로 함). 카메라 경고는 `com.mnsoft.navi.action.NaviCameraAlertState`(`CamAlertState`, `Time_ms`)만 있다.
- **내비 AIDL `IInterfaceService` 거래 21**: `getNaviCurrentLocationInfo(lat, lon)` → 문자열(네이티브). 방송 쪽이 더 단순해 쓰지 않았다. 거래 8은 `getNaviInfoToSXM(lat, lon)`(SXM 전용).
- **공조**: `com.hkmc.datc.DATCManager.getClimateInfo(int)` 등 읽기 메서드가 있으나 번호 뜻(`DATCConstants`)을 아직 대조하지 않았다. 다음 후보.
- **프로필**: `UserProfileManager`에 `getDownSync*Property`, `getSystemSetupJson(int)`, `getTmsProfileImage(int)`가 있다. 운전자별 사진·설정의 다음 후보.

검증: JDK 21 빌드·v1 서명, 코덱스 검사 2종(1920×720·1280×480), 새 시험(현재 도로명이 홈 카드에 표시, 길안내 중에는 기존 경로 줄 유지, 화면 오류 없음). 배포: `deliverables/K8-5W-홈-0.4.21.apk`, `K8-ccNC-Home-NEMESIS-0.4.21-USB.zip`(SHA-256 `e2ca16b1daa0fcd33a2dfbe5caf751c3f6f5983c4e7bec1f5cda36dfc492bdfd`), 서명 인증서 0.4.19·0.4.20과 같음.

차에서 볼 것(0.4.21 설치 뒤, 정차): `logcat -s K8StockRoute`로 현재 위치 응답과 `on_road` 형식, 내비 상태 방송 키; 사운드 화면에 순정 값이 뜨는지(안 뜨면 MicomAudio 서비스가 일반 앱을 거부한 것).

## 0.4.20 차량 데이터·순정 버튼 연동 (2026-11-21, 코덱스가 중간에 멈춤 → Claude 이어받음)

- 코덱스가 19:19(한국 시간)에 사용자 요청으로 멈췄다. 이 절은 Claude가 코덱스 대화 기록과 파일로 정리했다.
- 넣은 것(PC에서만 확인): 홈·우측 지도 위젯도 하단바와 같은 길안내 값(목적지·남은 거리·남은 시간·도착 시각)을 받음(`RouteMetrics`, `StockRoute`). 순정 라디오 주파수·프리셋, USB 반복·셔플(`StockMedia`). 통화 상태·연결 기기·순정 사용자 이름·폰 프로젝션 설정·헤드유닛 설정값 읽기(`StockPhone`, `HeadUnitInfo`). USB·자연의 소리 재생 버튼과 라디오 이전·다음은 순정 위젯과 같은 명령(`StockTransport`). 근거 매니페스트·스말리 사본은 `C:/AI-Work/2026-11-21-k8-ccnc-offline-integrations/checks/`.
- 확인한 것: 현재 소스가 JDK 21(`C:/Users/dlatn/.local/share/rea/jdk/jdk-21.0.12.1+1`, 문서의 JDK 17 경로는 지금 없음)로 빌드되고 v1 서명 검사를 통과한다. 코덱스 검사 `verify-integration.cjs`(경로·출처·전화·시스템 값·순정 요청), `verify-menu-compat.cjs`(카드 크기·앱 실행·넘김·배치 저장)가 1920×720·1280×480에서 모두 통과했다. 코덱스 산출물 `k8-home-0.4.20.apk`, `K8-ccNC-Home-NEMESIS-0.4.20-USB.zip`은 위 AI-Work 폴더에만 있고 deliverables에는 아직 없다(마지막 소스 수정이 APK보다 1분 늦음 → 배포 전 다시 빌드).
- 배포(20:08): Claude가 다시 빌드해 `deliverables/K8-5W-홈-0.4.20.apk`, `K8-ccNC-Home-NEMESIS-0.4.20-USB.zip`(SHA-256 `b8fe93dc30b9f87b8d181663385a9a4b72e5207b9f90813a8e611ca466b85eec`)을 두었다. 묶음 안 APK와 바이트 일치, 서명 인증서가 0.4.19와 같다(`9bc243ec…`).
- 남은 일: ① (끝남) ② 실차(정차)에서 확인: 길안내 중 홈 위젯 값과 단위(`DestDistanceType=2`, `DestRemainTimeType=1` 해석), 라디오 주파수·이전/다음, USB·자연의 소리 재생, 통화 상태, 0.4.18~0.4.19 카드 3개(네메시스·마이드라이브·유튜브) 실행. ③ 0.4.7 이후 작업 대부분(새 Java 파일 다수 포함)이 커밋되지 않았다. 커밋할 때 `private/`는 넣지 말 것. GitHub `main`은 0.4.6 상태.

## 0.4.19 네메시스 앱 실행 연결

- K8 마이드라이브와 유튜브 카드가 각각 k8ecs.damping 및 Gen5W SmartTube 네메시스 패키지에 선언된 Android 화면을 직접 연다. 마이드라이브 0.4.1과 SmartTube v32.71-gen5w.1의 패키지 선언 및 APK 매니페스트를 확인했다. 0.4.18의 기존 앱 선택값이 새 연결에 적용되지 않도록 실행 키를 갱신했다.
- 1920×720·1280×480 브라우저에서 세 카드 실행 요청과 배치 저장, API 19 APK 서명, USB ZIP의 내부 APK 일치 및 SHA-256을 확인했다. 차량 설치 및 카드 탭은 확인 전이다. 배포본은 C:/AI 통합/네메시스/deliverables, 작업본과 보관 기록은 C:/AI-Work/2026-11-21-k8-ccnc-nemesis-app-targets에 있다.

## 0.4.18 전체 기능 카드

- 전체 기능 3페이지에 네메시스, K8 마이드라이브, 유튜브 카드를 추가했다. 기아 전체 기능 화면 자료와 Astryx Grid·ClickableCard를 대조했고, 기존 6열 크기와 흰색 선 아이콘을 사용한다. 저장된 카드 순서는 유지되며 신규 카드에도 스와이프와 1초 길게 누르기 재배치가 적용된다.
- 네메시스는 com.nemesis.manager/.MainActivity, 마이드라이브는 io.github.k8ecs.hu의 실행 화면을 사용한다. 대상 패키지는 로컬 APK 선언에서 검증했다. 유튜브는 설치된 com.google.android.youtube를 열고 기본 앱을 열 수 없을 때 기존 앱 선택을 사용한다.
- 1920×720·1280×480 브라우저, 구형 WebView 호환 레이아웃, APK/USB ZIP과 API 19 서명을 검증했다. 차량 설치와 실제 앱 실행은 확인 전이다. 배포 파일은 C:/AI 통합/네메시스/deliverables, 캡처와 검증 기록은 C:/AI-Work/2026-11-21-k8-ccnc-extra-cards에 있다. G 보관 드라이브 미연결로 C 작업본을 유지했다.

## 0.4.17 내비 하단바 및 경로 요약

- 순정 ccNC 길안내 사진과 사용자 첨부를 대조했다. 거리와 시각 숫자를 같은 크기와 기준선에 두고 AM/PM은 단위 크기로 같은 줄에 표시한다. 도착 예정 시각이 기본이고 시각 터치로 남은 시간을 표시한다.
- 블루투스 표지는 오른쪽 미디어의 검은 88×88px 칸에 놓았다. 표지 탭은 재생/일시정지, 곡 제목 탭은 재생 패널, 왼쪽 스와이프는 다음 곡, 오른쪽은 이전 곡 키로 보낸다. 장식용 끝 목록 아이콘을 제거했다.
- 목적지 제목은 전체 화면을 바꾸지 않고 하단바 위 432×166px 경로 요약을 열도록 했다. 순정 응답에서 확인된 거리·시간·목적지 값만 표시한다. 순정 내비의 교차로 확대 화면 자체는 이 앱에서 크기를 제어할 경로를 확인하지 못했다.
- 순정 내비에서 방향과 교차로 데이터를 계기판으로 전달하는 코드가 있다. 앱에서 읽을 수 있는 방향·차선 송신 경로는 미확인이다. 방향 카드 시안은 `C:/AI-Work/2026-11-21-k8-ccnc-navigation-polish/direction-preview.png`에 있다. 예시 방향을 앱에 넣지 않았다.
- ADB 연결 없음. Android 19 빌드와 브라우저 검증을 수행했다. 실제 표지 수신, 곡 전환, 경로 응답 및 오버레이 위치는 실차에서 확인할 항목이다. G 동기화 드라이브가 연결되지 않아 C 원본을 유지했다.

## 0.4.16 순정 버튼 연동과 내비 하단 정보

- 기존 전체 메뉴의 카드 영역과 73% 구분선을 복원하고 홈을 그 기준에 맞췄다. 1920×720 브라우저에서 기존 전체 메뉴, 수정 전체 메뉴, 수정 홈의 위·아래·구분선 좌표가 같았다.
- 순정 5W 앱 실행 코드를 `StockApps`로 모았다. 차량에서 확인한 지도·전화·미디어·BT·USB·라디오·자연의 소리·폰 프로젝션·설정·차량 설정·프로필 진입점을 사용한다. USB 미장착이면 미디어 선택 화면을 연다. FM/AM은 순정 라디오의 공개 서비스에 요청하며 실제 대역 전환을 확인하지 못했다.
- 내비 하단 오른쪽 곡 정보 버튼과 목록 아이콘은 지도 위 재생 패널을 연다. 순정 위젯의 현재 곡과 재생 상태를 보여 주고 이전·재생/일시정지·다음 키 및 확인된 순정 플레이어 실행을 제공한다. 순정 BT 대기열은 위젯 방송에 없으며 내부 목록 액티비티/서비스는 외부 진입을 제공하지 않는다.
- 내비 하단 중앙은 순정 `Response_Info_Destination` 응답의 `remain_dist`, `arrival_time`, `poi`, `address`를 사용한다. 남은 시간을 기본으로 표시하고 누르면 도착 예정으로 바꾼다. 출처 없는 숫자 단위는 추정하지 않는다. 검색 중에는 순정 하단줄을 쓰고 새 길안내 확인 시 테마 줄을 복귀하도록 연결했다. [기아 내비 매뉴얼](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Map.html)
- 빌드/검증 및 테스트 캡처는 `C:/AI-Work/2026-11-21-k8-ccnc-5w-buttons`에 있다. 0.4.16이 차량에 설치되지 않아 실제 ETA 응답과 검색·경로 취소·미디어 키 결과는 확인 전이다. G 보관 위치가 연결되지 않아 C 원본을 유지했다.

## 0.4.15 홈 카드·설정·미디어 디자인 수정

- 홈 카드와 전체 메뉴의 위·아래 경계 및 라벨 구분선을 통일했다. 홈 본문 65% 비율과 미디어 표지 위치는 기아 공식 홈 이미지에 맞췄다. KitKat 설정 카드는 두 행의 높이와 열 간격을 명시했다. 미디어는 한 곡 보기를 기본으로 하고 재생 목록 행은 54px로 제한했다. 분할 화면 설정에는 SVG 아이콘을 사용한다. [기아 홈 화면](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Systemoverview_home.html)
- K8의 순정 내비와 런처 산출물에서 홈 지도 렌더링 인터페이스를 대조했다. 홈 카드에 `TextureView`를 배치하고 전경에서만 순정 내비의 내보낸 `InterfaceService`에 연결한다. 퀵 컨트롤과 화면 전환 시 지도 표면을 숨긴다. 블루투스 앨범 표지는 기존 나우플레잉이 사용하는 AVRCP 캐시의 읽기 가능한 완료 파일을 받아 홈과 플레이어에 표시한다. USB 표지 경로는 확인하지 못했다.
- 1920×720 브라우저에서 KitKat 레이아웃 분기로 13개 화면을 렌더링했다. 홈·전체 메뉴의 세로 경계가 일치했고 4×2 설정 8개 카드 높이, 지도 카드 터치 요청, 재생 목록 행 높이, 앨범 이미지 렌더링을 확인했다. 비교판의 지도와 앨범은 테스트 이미지다.
- Android API 19 빌드 및 v1 서명을 확인했다. 0.4.15는 실차 미설치. 실차에서는 기본 홈 선호도에 `io.github.k8home/.HomeActivity`가 `mAlways=true`로 저장돼 있는 것을 읽었다. 물리 키의 복귀 동작과 새 버전의 지도 표면·앨범 표지는 차량 연결이 종료돼 시험하지 못했다.
- Astryx 0.7.17 CLI의 `Grid`, `ClickableCard`, `GridGalleryExample`을 검토했다. 기존 KitKat WebView 구현에 간격과 카드 구조 원칙을 적용했다. 비교판과 로그는 `C:/AI-Work/2026-11-21-k8-ccnc-home-cards`에 있다. G 보관 드라이브는 연결되지 않아 C 원본을 유지했다.

## 0.4.14 순정 내비 화면과 ccNC 하단바 실차 연동

- K8 정차 로그를 확인하고 테마의 **설정 → 일반 → 순정 내비에서 ccNC 하단바 시험**을 켰다. 테마 홈의 지도 카드를 눌러 `com.mnsoft.navi/.NaviApp`이 전면에 표시되고 `K8HomeNavBar` 창이 그 위에 붙는 것을 확인했다.
- 1920×720 실차 캡처에서 순정 지도와 차선 표시를 유지하면서 기존 하단줄 자리에 ccNC 뒤로·홈·전체 기능, 목적지 검색, 미디어 표시가 그려졌다. 순정 내비 방송을 통해 거리·시간 원시값도 수신했다. 경로가 걸려 있지 않아 둘 다 0이었다.
- 이후 차량이 움직이는 것을 속도 로그에서 확인했다. 터치 시험을 중단하고 지도와 하단바는 열린 상태로 두었다. 하단바 홈 터치·검색·경로 취소·재생 제어, 안내 중 거리·시간 단위, 홈 카드 안 실시간 지도 표시를 확인할 단계가 남았다.
- 위치가 포함된 실차 캡처와 작업 기록은 `C:/AI-Work/2026-11-21-k8-ccnc-nav-live`에 있다. G 보관 경로가 연결되지 않아 C 원본을 유지했다.

## 0.4.14 K8 실차 설치·실행 확인

- 정차 상태에서 기존 네메시스의 패키지 설치 경로로 `k8home.install 0.4.14`를 설치했다. 차량 호환 판정은 `full`이다. 설치된 `io.github.k8home 0.4.10`의 SHA-256이 배포용 APK와 일치했다.
- 네메시스 Launch 후 `io.github.k8home/.HomeActivity`가 전면에 표시됐다. 1920×720 실차 캡처에서 테마의 블루투스 오디오 화면과 실제 곡명·가수가 하단에 표시되는 것을 확인했다.
- 순정 내비 위 하단바, 물리 HOME, 시동 후 자동 실행, 재생 제어 및 길안내 수치는 이번 설치에서 시험하지 않았다.
- 실차 캡처와 설치 기록은 `C:/AI-Work/2026-11-21-k8-ccnc-home-install`에 있다. G 보관 드라이브는 연결되지 않아 C 원본을 유지했다.

## 0.4.14 네메시스 ccNC 홈 독립 실행 패키지

- `deliverables/K8-ccNC-Home-NEMESIS-0.4.14-USB.zip`에 최신 로컬 ccNC 홈 APK 0.4.10을 원본 그대로 넣었다. 기존 0.4.10 USB 안 APK는 같은 버전명이지만 길안내 방송 수신을 추가하기 전의 DEX였다.
- 패키지 ID는 `k8home.install`이고, 네메시스 매니저의 **K8 ccNC Home Theme → Launch**는 `io.github.k8home/.HomeActivity`를 가리킨다. 서개모, HMG-API 및 별도 패키지 스크립트 의존성이 없다. USB 묶음은 매니저 USB 탭의 `type=list`를 사용한다. [설치 안내](NEMESIS_INSTALL.md)
- ZIP 두 층과 APK의 무결성, 매니저 등록 항목 및 APK 매니페스트의 액티비티 식별자, 최신 APK와의 바이트 일치, 길안내 방송 식별자, 재빌드 일치, 기존 순정 바로가기 11개 및 잘못된 APK 입력 거부를 확인했다. REA APK 인벤토리 `ev_036b7bddbb6e4772506f48f00c2865621f1e0ed338afc132faebb24c4ccd6df9`. USB ZIP SHA-256 `add0781ec42ccc33441a5384588a9e0b5ef798c69c99c0599a07f18fdddab831`.
- 패키지 제작 시 실차는 미연결이었다. APK에는 기존 차량 진단용 내비 로그가 남아 있다.
- 배포 파일은 C 드라이브 작업 공간의 `deliverables`에 보관했다. `G:/내 드라이브/AI-작업보관` 경로가 보이지 않아 복사와 클라우드 업로드 확인을 진행하지 못했다. C 원본을 유지했다.

## 길안내 값 실제 통로 (2026-11-21 실차 로그)

- 순정 내비(`com.mnsoft.navi`)가 **매초** `com.hkmc.telematics.gis.action.NAVI_GPS_CHANGED` 방송을 쏜다. extras는 `com.hkmc.telematics.gis.extra.` 아래에 있다.
  - `DestDistance`, `DestRemainTime` → **숫자**(문자열 아님). `DestDistanceType=2`, `DestRemainTimeType=1`이 단위 구분 코드다. `LAT`, `LON`, `ALT`, `HEADING`, `TYPE`, `PDOP`, `HDOP`, `TIME`도 함께 온다.
  - 같은 방송을 `ModeManagerService`, `Location2Service`, `DMB_APP`, `USBVideo`가 받고 있다. 시스템 표준 경로다.
- **차량 로그가 "아무 값도 안 뜬다"의 정체를 밝혀냈다.** 기존 코드는 `com.mnsoft.navi.action.extra.DestDistance`만 읽었고 문자열로만 변환했다(`text()`는 CharSequence만 통과). 실제로 오는 값은 숫자이고 이름도 `telematics.gis` 쪽이다.
- 대응: `NavBarService`가 `GPS_CHANGED` 방송을 받고 `value()`(숫자도 통과)로 읽도록 고쳤다. 모든 내비 방송은 `nav extras:` 로그로 값과 타입이 같이 찍힌다.
- 단위(미터인지 초인지)는 `DestDistanceType`/`DestRemainTimeType` 해석이 아직 안 끝났다. 실차에서 한 번 값이 붙은 상태로 돌려보면 확정된다.
- 원본 경로 목록과 근거는 [VEHICLE_API.md](VEHICLE_API.md), 확인 스크립트는 `tools/nav_route_probe.sh`.

## 0.4.13 순정 바로가기 재검증

- 매니저의 K8 Settings 상세 화면에서 Launch 버튼으로 순정 설정을 열었다. 나머지 순정 바로가기도 패키지 실행 경로로 확인했으며, 0.4.12에서 USB 음악만 실차 화면이 전면에 유지되지 않았다. 차량에 USB 저장 장치가 마운트되지 않은 상태였다.
- USB 음악 바로가기는 정상 마운트 시 순정 USB 음악, 마운트가 없으면 순정 미디어 선택 화면을 연다. USB 항목만 실차에 0.4.13으로 갱신하고, 네메시스 Manager의 Launch에서 미디어 선택 화면이 전면에 표시되는 것을 확인했다. USB 연결 분기는 모의 마운트 입력으로만 확인했다.

## 0.4.12 네메시스 셸 바로가기

- 실차의 K8 Ambient Relay 및 BLE Probe와 같은 `kind=sh`를 사용하며, 화면 실행에는 검증된 순정 런처 컴포넌트만 지정한다. 앱마다 패키지 하나, `app.0` 하나로 구성했다. 설치 후 자동 서비스와 APK가 없다.
- 매니저 1.16.9의 `PackageActivity`는 패키지 ID 하나로 Launch를 호출한다. 여러 바로가기를 단일 Launch에 묶었던 0.4.11은 0.4.12 지도로 갱신한다. 나머지 바로가기는 개별 ID로 제공한다.
- USB 묶음은 공식 `type=list`를 사용한다. [설치 안내](NEMESIS_INSTALL.md)를 참고한다. 이 패키지는 순정 화면 바로가기이며 기존 ccNC 홈과 하단바의 렌더링 경로는 별도 확인이 필요하다.
- 실차 매니저에 11개 모두 0.4.12로 등록됐다. 정차 상태에서 지도 패키지를 실행했고 `com.mnsoft.navi/.NaviApp`이 실제로 전면에 뜨는 것을 확인했다. 다른 10개는 실차 패키지 등록까지 확인했다.

## 0.4.10 실차에서 찾은 순정 진입점

- K8 헤드유닛 API 19와 1920×720에서 순정 지도 실행, 하단 간단 메뉴와 통합 검색 터치를 확인했다. 실제 하단줄 상단은 y=627이다. 실차에서 확보한 순정 내비 APK는 이전 REA 조사본과 동일한 SHA-256이다.
- 실차에서 확인한 MediaGate, 블루투스 전화, 라디오, 블루투스 오디오, USB 음악, 자연의 소리, 차량 설정, 사용자 프로필, 폰 프로젝션 등록 패키지를 기존 테마 버튼의 기본 진입점에 연결했다. 앱별 기존 사용자 지정은 우선 적용된다. FM/AM 탭에서 대역을 강제 변경하는 동작은 확인 전이다.
- 순정 통합 검색에서 우리집·사무실이 나란히 보인다. ADB로 순정 메뉴·통합검색 요청을 보내면 완료 메시지는 왔으나 화면이 바뀌지 않았다. 일반 앱에서 같은 요청이 수락되는지는 추가 검증이 필요하다. 하단바 메뉴는 ccNC 전체 기능 화면으로 열고, 뒤로 키로 순정 줄을 다시 볼 수 있게 했다.
- PC에서 Android 4.4 서명 확인은 통과했다. 실차 ADB 설치는 0.4.9와 0.4.10 v1 전용 호환본 모두 `INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES`로 실패했다. USB가 없어 네메시스 Manager 설치는 시험하지 못했다. 테마 경로, 권한, 지도 데이터·경로 취소 및 미디어 상태 응답은 설치 후 검증한다.

## 0.4.9 하단바 정렬과 다음 실차 확인

- 공식 길안내 하단바와 첨부 이미지를 1920×720 기준으로 대조했다. 순정 내비 위 하단바의 왼쪽 버튼은 152px 간격과 32px 아이콘 영역을 이용한다. 거리·시각은 34px, 목적지·미디어 제목은 26px, 단위·AM/PM·보조 라벨은 22px다. 테마 안의 하단바에도 같은 글자 위계와 버튼 도형을 적용했다. 안내 중 숫자가 아직 없으면 목적지 표시를 앞으로 배치한다.
- 실차가 ADB에서 정식 승인된 기기로 확인되면 정차 상태에서 내비·미디어·전화 관련 앱의 현재 버전과 선언, 버튼을 눌렀을 때의 상태 변화를 대조한다. REA에서는 관련 순정 앱의 처리 분기와 호출 조건을 확인한다. 내비 메뉴·통합검색·경로 취소·경로 상세, BT/USB 재생·일시정지, 전화·설정 순으로 확인한다.
- 테마 앱 자체 권한으로 호출에 응답이 오고 순정 화면 전환이 없어야 화면 안에서 실행할 수 있다고 판정한다. 순정 지도 렌더러의 외부 제공 경로와 전용 화면만 있는 기능은 별도 확인이 필요하다. ADB는 실차 진단과 검증에 사용한다.

## 0.4.8 네메시스 직접 실행과 순정 내비 연결

- `tools/build_nemesis.py`가 Manager APK 등록 항목 `io.github.k8home/.HomeActivity`를 포함한 USB 묶음을 만든다. 패키지 ID는 `k8home.install`을 사용하고, APK·네메시스 버전은 0.4.8이다.
- 차량 앱에서 지도 카드를 터치하면 `com.mnsoft.navi/.NaviApp`으로 바로 이동한다. 순정 앱을 연 뒤 내비 설정 및 검색/경로 진입을 요청한다. 우리 집·회사는 순정 등록 지점에서 직접 선택한다.
- 하단바는 **설정 → 일반 → 순정 내비에서 ccNC 하단바 시험**을 켠 뒤 테마에서 지도를 열었을 때 시작한다. 최상위 액티비티만 확인한다. 화면 꺼짐, 다른 앱 전환, 순정 메뉴, 왼쪽 화살표, 목적지 상세에서 순정 하단줄을 복원한다. 경로 취소는 순정 요청과 상태 응답을 연결하며, 종료 응답이 없으면 순정 줄이 돌아온다.
- 순정 내비 ODEX에서 메뉴·경로 취소·목적지 정보 요청의 처리 분기를 확인했다. NAV 키 메뉴와 5W 하단 간단 메뉴의 화면 일치 여부, 일반 앱 요청 수락 및 방송 데이터 단위는 실차 검증 대상이다. 미디어는 Android 표준 키를 이용한다.
- 0.4.8은 [설치 안내](NEMESIS_INSTALL.md)를 따른다. 이전 버전의 루트 설치 스크립트와 미디어 키 도우미는 아래 이력에 해당한다.

## 0.4.7 작업 이력

### 네메시스 패키지 제공

차량 헤드유닛은 일반 APK 설치를 거부한다. 그래서 이 테마는 **네메시스 패키지 하나**로 묶어 넣는다. `tools/build_nemesis.py`가 APK와 키 도우미 dex를 묶어 `private/nemesis/k8home.install-<ver>.zip`과 USB 묶음 `K8-ccnc-home-<ver>-USB.zip`을 만든다.

- 패키지 하나에 들어가는 것: `bin/install.sh`(root, `pm install -r`), `bin/status.sh`(root), `svc/keys.sh`(system uid, media key helper), `bin/k8home-keys.dex`, `apk/k8-home.apk`.
- **미디어 키 도우미**: `helper/src/io/github/k8home/helper/KeyHelper.java`가 추상 소켓 `k8home.keys`에서 `io.github.k8home` uid만 받아 이전(166)·재생/일시정지(91)·다음(167) 키만 `IModeManager.generateKeyEvent`로 다시 보낸다. 일반 앱은 `INJECT_EVENTS`가 없어 모드 매니저가 키를 버린다(2026-11-16 차량 확인). 앱 쪽은 `src/io/github/k8home/MediaKeys.java`가 소켓에 물어보고, 도우미가 없으면 `AudioManager.dispatchMediaKeyEvent`로 물러간다.
- `docs/NEMESIS_INSTALL.md`에 USB 설치 절차를 적었다. USB의 `NEMESIS.data`는 덮어쓰므로 백업이 필요하다.
- 서개모 경로는 쓰지 않는다. 세ogaemo/서개모 port는 `research/k8-ccnc-theme-seogaemo*`에 따로 남아 있다.

## 5W 순정 내비와 ccNC 내비 비교

기아 공식 한국어 매뉴얼 이미지만 근거로 두 플랫폼의 내비 화면을 맞붙였다. 문서는 [5W-vs-ccNC-내비비교.md](5W-vs-ccNC-내비비교.md), 나란히 보는 페이지는 [nav-compare.html](nav-compare.html), 받은 원본 이미지는 `docs/images/nav-compare/`다.

- **하단 표시줄이 가장 큰 차이다.** 5W 아래 줄은 순정 내비 앱의 메뉴(메뉴 · 경로 종료 · 287 km/14:32 · ⓘ 목적지 · 오른쪽 끝 버튼)이고, ccNC 아래 줄은 시스템 바(← 뒤로 · ⌂ 홈 · ≡ 메뉴 · 중앙 알약 · ▶ Media Off)다. **우리 테마가 만든 하단바는 ccNC 쪽 모양**(`assets/index.html`의 `.bottom-bar`, `assets/navbar.html`이 같은 것을 쓴다). 그래서 5W 내비 위에 얹으면 ccNC 스타일 바가 5W 스타일 메뉴줄 위에 놓인다.
- **왼쪽 세로 버튼은 순정 내비 영역이다.** 5W는 홈·SE·Nav·＋·Auto 400 m·− 6개, ccNC는 검색·나침반·Nav·Auto·Scale 5개. 테마가 새로 만들 대상이 아니다.
- **안내 카드와 시계 위치가 다르다.** 5W는 우상단 카드 3장 + 상단 중앙 시계, ccNC는 우측 카드 + 지도 중앙 하단 차선 안내 띠 + 우상단 시계.
- **검색 UI가 완전히 다르다.** 5W는 검은 타일 메뉴에 **우리집·사무실·즐겨찾기 1·2·3** 탭, ccNC는 검색창 + 한글 자판 + 최근 검색 패널 + (최근 목적지·주변 검색·등록 지점) 탭. '우리 집·회사 바로 안내'는 5W의 우리집/사무실 탭을 여는 방식이 정공법에 가깝다.
- **ccNC 등록 지점**은 목적지 설정·위치 저장·선택지 주변 검색 버튼을 준다. 5W 화면에는 이 버튼들이 없다.
- 아직 확인하지 못한 것: 두 플랫폼의 지도 공급자·렌더링 엔진 일치 여부, 5W의 ccNC 기능 지원 범위, 실제 차량 화면에서의 표시 크기.

## 차량용 APK 0.4.7 시동 자동 실행·HOME 등록·순정 내비 위 하단바

0.4.6 조사에서 근거를 찾은 항목 중 **물리 HOME·시동 자동 실행**과 **순정 내비 위 하단바**를 먼저 구현했다. 차량 연결은 없었다.

- **HOME 등록**: `HomeActivity`에 `MAIN`+`HOME`+`DEFAULT` 인텐트 필터를 추가했다. 차량 런처와 테마가 함께 HOME이 되면 안드로이드 4.4는 선택창을 띄우므로, 물리 HOME 버튼이 어디로 전달되는지는 실차 확인이 필요하다. 앱 안의 `차량 홈 화면 설정` 버튼이 시스템 홈 설정으로 간다.
- **시동 자동 실행**: `BootReceiver`가 `BOOT_COMPLETED`·`QUICKBOOT_POWERON`·`MY_PACKAGE_REPLACED`를 받고, 곧바로 열지 않고 알람으로 22초 뒤 미룬다. 알람이 울리면 `sys.boot_completed`, 화면 점등, 카메라·안전 화면 여부를 확인한 뒤에만 테마를 연다. 준비가 안 되면 7초 뒤 다시 확인한다. 일반 설정의 `시동 후 자동 실행`으로 끌 수 있다.
- **순정 내비 위 하단바**: `NavBarService`가 `TYPE_SYSTEM_ALERT` 창으로 하단바를 띄운다. 순정 내비(`com.mnsoft.navi`)가 맨 앞에 있을 때만 나타나고, 바 안은 테마 안의 하단바와 같은 화면이다. `assets/navbar.html`은 **기아 공식 ccNC 길안내 화면(`D-map_guidance.png`)의 하단 표시줄을 픽셀 단위로 옮긴 것**이다. 공식 그림을 1920×720 기준으로 재서 값을 뽑았다. 바 높이 88px, 바 배경 `#686765`, 키 띠 0~521px(키마다 152px 칸, 아이콘 30px), 중앙 알약 521~1423px(배경 `#525150`, 높이 68px), 경로 종료 원 지름 48px(알약 왼쪽에서 30px 지점), `285` 36px·`km` 22px·`3:39` 30px·`PM` 20px, 구분선 x=976, 깃발 32px, 목적지 22px, 재생 띠 1437~1511px(배경 `#1C1C1C`, 삼각형 26px), `Media Off` 27px. 매뉴얼의 강조 표시(흰 테두리·갈색 상자·주황 번호)는 실제 화면에 없는 요소라 넣지 않았다. 앞선 판(색 `#5c6065`/`#36393e`)은 이 수치로 다시 만들었다. 아이콘은 공식 매뉴얼 원본 파일(`contents/images/I-menu_home_gary.png`, `I-menu_new.png`, `I-media_play_new.png`, `I-media_pause_new.png`)을 `assets/icons/`에 그대로 넣어 쓴다. `build.py`가 `assets/icons/*.png`도 APK에 넣는다. 음악이 없으면 검은 재생 띠와 `Media Off`, 음악이 틀어지면 같은 자리에 앨범 표지(테마와 같은 자리 그림)·제목·가수가 나온다. 깃발은 공식처럼 속을 채웠다.
- **5W 하단 줄의 기능을 ccNC 쪽으로 옮겼다.** 5W 아래 줄은 `메뉴 · 경로 종료 · 남은 거리/시간 · ⓘ 목적지 · 오른쪽 끝 버튼`이고, ccNC 아래 줄은 `뒤로 · 홈 · 메뉴 + 중앙 알약 + 미디어 상자`다. 순정 줄을 가리고 그 자리에 ccNC 바를 놓으려면(기본 높이 90 px, 아래 여백 0), 5W의 기능도 중앙 알약으로 옮겨야 한다.(ccNC 공식 길안내 화면 `D-map_guidance.png`의 중앙 알약이 이미 `✕ + 거리 + 시간 + 목적지` 형태다.)
  - `✕` 경로 종료 → `com.mobis.intent.action.CANCEL_ROUTE_SHORTCUT` 를 `com.mnsoft.navi`에 보낸다.
  - `≡` 메뉴 → `com.hkmc.intent.action.ACTION_NAVI_MENU`. 테마 안의 `≡`(전체 앱)와는 뜻이 다르다.
  - `ⓘ` 목적지 → `com.hkmc.intent.action.Request_Info_Destination`.
  - 남은 거리·남은 시간·목적지 이름 → `com.mnsoft.navi.action.Route_Guide_Status` / `Route_Guide_NewStatus` / `OTA_Route_Guide_Status` / `DestinationInfo` broadcasts의 `com.mnsoft.navi.action.extra.DestDistance`, `DestRemainTime`, `Address`, `PoiName` extras로 읽는다. 값이 없으면 중앙 알약은 `길안내 정보 연결 대기`로 남는다.
  - `←` 뒤로는 바를 3분 숨기고, `⌂` 홈은 테마 홈, 미디어 상자는 재생·일시정지다. 바를 길게 누르면 위치(상단/하단), 높이(64/90/112 px), 아래 여백(0/44/90/140 px)을 바꾼다.
  - **동작 이름 근거**: 차량에서 뽑은 순정 내비 ODEX(`research/k8-ambient-sync/private/nowplaying/apps/HKMC_Navi_ExSL.odex`)의 문자열. dexdump으로 만든 이전 REA 증거와 같은 원본이다. 이 액션들이 실제 라우처에서 받아들이는지는 실차 확인 전이다.
- **일반 설정**: `시동 후 자동 실행`, `순정 내비 위 하단바`, `차량 홈 화면 설정` 세 줄을 추가했다. 웹 데모에서도 같은 줄이 보인다.
- **새 파일**: `src/io/github/k8home/BootReceiver.java`, `NavBarService.java`, `ThemePrefs.java`, `assets/navbar.html`. `HomeActivity`는 단일 태스크로 바꾸고 `page` 인텐트 extras로 전체 앱 화면을 열 수 있게 했다. `build.py`가 `assets/navbar.html`도 APK에 넣는다.
- **배포 파일은 `deliverables/K8-5W-홈-0.4.7.apk`다.** API 19, 0.4.6과 동일 서명(SHA-1 `77fde4e9808b507a43f079ea711ab13a6efeb0e7`), APK SHA-256 `e89f3138f9d86a154950b23b1b64c89e4715302ce6ac025a0dcca6c7113d6865`. 0.4.6 위에 덮어써도 데이터가 지워지지 않는다.
- **검증 범위**: 브라우저(1280×720)에서 일반 설정의 새 세 줄, 테마 안 지도 화면의 하단바(✕·거리·시간·목적지·ⓘ), `navbar.html` 단독 렌더링, `docs/nav-bar-preview.html` 목업을 확인했고 콘솔 오류는 없다. `deliverables/K8-5W-하단바-0.4.7-예시.png`은 그 목업(순정 5W 길안내 자리 위에 실제 `navbar.html`을 얹은 모습)이다.
- **실차 시험 준비**: `tools/field_test_0.4.7.sh`(status·install·home·boot·navbar·shot·restore)와 `docs/FIELD_TEST_0.4.7.md` 체크리스트. 연결되면 이 순서로 확인한다.
- **아직 확인하지 않은 것**: 물리 HOME 전달 방식, 완전 부팅과 QuickBoot의 자동 실행, 오버레이 권한 허용 여부, 순정 줄을 덮은 뒤 순정 내비 하단 버튼이 안 보이는지, 카메라 화면에서의 오버레이 동작, CarPlay 진입 시 상태, 내비 broadcast 액션이 실제로 받아들이는지. 네메시스 메뉴 등록은 아직 손대지 않았다. 통화 상태 표시, 우리 집·회사 바로 안내, FM·AM 직접 전환은 이번 버전에 넣지 않았다.

## 0.4.6 연동 조사

GitHub `e8b80a0`의 Android 코드, K8 5W 순정 APK와 기존 네메시스 실차 기록을 대조했다. 이번 조사 시 ADB에 연결된 차량은 없었다.

| 기능 | 현재 테마 | 추가 연동 근거와 확인 범위 |
|---|---|---|
| 미디어 | BT·USB 위젯 방송의 곡명·가수·재생 상태·시간을 표시하고 Android 미디어 키와 음량을 제어한다. | [나우플레잉 오버레이](https://github.com/dlatnqls0815-spec/k8-nowplaying-overlay)에 순정 위젯 수신 구현과 실차 기록이 있다. 기존 프로젝트의 이전·다음·재생 제어에는 별도 네메시스 도우미가 필요했다. 0.4.6의 일반 미디어 키 수신, 소스 직접 전환, 전체 목록과 탐색은 미확인이다. |
| 내비 | `NaviApp` 실행을 지원한다. 우리 집·회사는 순정 앱에서 저장 지점을 선택하도록 안내한다. | 확보본 매니페스트와 ODEX에서 전체검색, 현재 위치, 경로 검색 처리 분기를 확인했다. 경로 검색은 좌표 입력을 읽는다. 순정에 저장된 집·회사 연결, 일반 앱 요청 수락, 필요한 추가 입력은 미확인이다. 순정 지도 View, 실시간 도착 시각·거리·회전·차선 데이터의 테마 공급 경로도 미확인이다. |
| 전화·기기 | Android 등록 기기 이름 표시, 블루투스 설정 및 전화번호를 받는 다이얼러 실행이 구현돼 있다. | 별도 나우플레잉 앱에서 K8 블루투스 핸즈프리의 발신·수신·통화 상태를 읽었다. 테마에 이식 가능한 후보다. 통화 제어와 연락처·통화기록·문자 전체는 테마에서 미검증이며, 기존 순정 전화 알림을 교체한 시험에서 검은 화면 사례가 있었다. |
| 순정 화면 위 하단바 | 앱 내부에 표시한다. | 별도 나우플레잉 앱에서 순정 내비 위 작은 카드를 표시한 실차 근거가 있다. 내비 한정 하단바는 후보이며, 전체 폭 및 다른 순정 화면의 표시·터치와 카메라 화면 등에서의 숨김을 확인해야 한다. |
| 차량 설정 | 차량 설정 앱 진입과 테마용 화면 구성·프로필 설정을 제공한다. | 마이 드라이브 모듈에서 에코 모드의 순정 클러스터 테마 변경 기록이 있고 스포츠 모드에서는 반응이 없었다. 앰비언트 상태는 기존 중계가 읽었다. 5W 차량 옵션별 값을 다른 설정에 일괄 적용할 근거는 없다. |
| 기본 홈·시동 | 일반 앱 실행 진입점이다. | APK에 Android HOME 카테고리, 시동 수신기, 오버레이 권한이 없다. 네메시스 메뉴에서 APK 열기는 구성 후보이며, 물리 HOME의 전달 방식과 완전 부팅·QuickBoot의 화면 자동 표시를 각각 확인해야 한다. |
| ccNC 전용·차량 서비스 | 웹 예시에 일부 화면이 있고 차량용으로 해당 데이터와 권한은 연결되지 않았다. | 디지털 키·모뎀·OTA·빌트인 캠과 Kia Connect 계정, 날씨·스포츠 위젯은 5W 사양과 별도 서비스 공급 경로를 확인해야 한다. 운전자 보조·공조·시트·순정 카메라는 현재 테마 내 직접 조작 경로가 없다. |

순정 내비 APK의 REA 인벤토리 증거는 `ev_44ea39db76d3ceec73f190899b91f06770ef06f72b51c78b191b2d41889f397a`다. `aapt`로 매니페스트를, SDK `dexdump`로 ODEX의 분석용 복사본을 읽었다. DEX 파일 오프셋 `0x06a69a`, `0x079314`, `0x07958a`에서 각 경로 검색·현재 위치·전체검색 액션 비교를 확인했다. 최적화 명령은 완전히 복원하지 못했으며 실행 여부는 실차 확인이 필요하다. 순정 APK·ODEX 원본은 로컬 증거로 보관한다. [기아 표준형 5W 내비 기능](https://ownersmanual.kia.com/ivi/STD_GEN5W/AVNT/KOR/Korean/006_Navigation_basic.html)

## 차량용 APK 0.4.6 공식 매뉴얼 대조

공식 한국어 ccNC 화면의 실제 이미지에서 홈 카드 비율, 키패드 열 배치, 검색 키보드와 결과, 디스플레이의 밝기·블루라이트, 음성 메모, 기기 등록 목록, 대기 화면을 비교했다. K8 5W에서 얻을 수 있는 상태와 테마 내부 기능을 해당 영역에 연결했다. [ccNC 웹 매뉴얼](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/index.html)

| 화면 | 0.4.6 구현 | 5W에서 확인할 부분 |
|---|---|---|
| 홈과 지도 | 홈 카드와 이미지 비율을 조정하고, 지도에서 통합 검색을 연다. 순정 지도와 우리 집·회사 진입 버튼을 제공한다. | 순정 지도 표면, 주소·POI 검색 데이터와 실시간 경로, 저장 목적지 직행이 테마와 연결되는지 확인한다. [홈](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Systemoverview_home.html) · [지도](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Map.html) |
| 통합 검색 | 검색 아이콘과 검색/경로 타일에서 연다. 한글 두벌식·영문 화면 키보드와 기기 키보드 입력, 설정·기능 필터, 최근 검색, 검색 결과 이동을 지원한다. | 순정 네비·전화·미디어 DB의 통합 검색은 연결되지 않았다. [검색](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Systemoverview_search.html) |
| 전화·기기 연결 | 화면 좌측 12키, 중앙 번호, 오른쪽 기기 상태를 배치했다. 번호는 Android 발신 확인으로 전달한다. Android 블루투스에 등록된 기기 이름을 표시한다. 기존에 연결한 앱을 우선 실행하고, 폰 프로젝션은 실행 가능한 DiPO 진입점이 있을 때 사용한다. | 실제 K8 페어링 목록 접근 여부, 핸즈프리·오디오 연결 상태, 주소록·통화·문자, Android Auto·CarPlay 각각의 실행 결과는 확인 전이다. [키패드](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Phone_keypad.html) · [기기 연결](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_connection.html) |
| 디스플레이·일반·대기 | 테마 창 밝기 슬라이더, 기본값 복원, Android 밝기 사용, 앱 블루라이트 색온도·시간 예약, 디지털·아날로그 대기 시계와 액자 선택을 제공한다. 날짜/시간·언어·키보드의 기기 설정을 연다. | 차량 조도 센서, 카메라·계기판과 동영상 색상, 순정 시스템 밝기는 제어하지 않는다. [디스플레이](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_display.html) · [화면구성](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_screenlayout.html) |
| 음성 메모 | 녹음 버튼에서만 마이크를 시작한다. 정지, 화면 이탈이나 오디오 포커스 상실 시 저장하고, 앱 내부 파일을 재생·삭제하거나 Android 저장 위치 선택으로 내보낸다. | 5W 마이크와 오디오 포커스, 저장 위치 선택기에서 USB가 표시되는지는 확인 전이다. 녹음 일시정지와 Kia Connect 동기화는 미구현이다. [음성 메모](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Voicememo.html) |

- 순정 차량 사양 및 권한이 필요한 운전자 보조, 공조·시트, 주차 영상, 빌트인 캠, 차량 진단, 모뎀·OTA·결제·디지털 키 등은 5W 순정 데이터/설정 경로 확인 전이다. 시동 실행, 물리 HOME, 순정 앱 위 공통 하단바에도 실차 근거가 더 필요하다. 순정 전환이나 근거 있는 바로가기가 있는 항목은 해당 앱을 안내하거나 연다.
- 배포 파일은 `deliverables/K8-5W-홈-0.4.6.apk`다. Android 4.4/API 19, 0.4.5와 동일 서명 및 HTML·DEX 패키징을 확인했다. 음성 메모용 `RECORD_AUDIO` 권한이 추가됐다. SHA-256 `914e06cec61a025f561b0487ee16f7fc88e92155e96eb1ada6e1a323fa0537fc`, REA 인벤토리 `ev_efb5a60528175f363424cbe4438caa48cd7fb260429d73c43af5877c10413b7a`.
- 1920×720 마우스/1280×480 터치 브라우저에서 화면과 저장, 검색 키보드의 한글 받침 분리, 검색 결과 전환, 키패드, 밝기·녹음 브리지 요청과 위젯 재배치를 검증했다. `deliverables/K8-5W-0.4.6-화면.png`는 모의 데이터가 포함된 렌더링이다. 실차 연결은 없었다.
- GitHub의 Android 빌드 입력은 `AndroidManifest.xml`, `assets/index.html`, `res/`, `src/`다. Pages의 루트와 `demo/` 체험 링크는 `assets/index.html`의 브라우저 모드를 연다. 앱과 웹이 화면 소스를 공유하며 브라우저에서 지도·미디어·통화 기록은 예시 데이터로 동작한다. 서명된 APK는 로컬 작업 공간의 `deliverables/`에서 관리한다.

## 차량용 APK 0.4.5 화면 배치 수정

- 공식 매뉴얼의 프로필, 날짜/시간, 기기 연결 정지 이미지와 퀵 컨트롤 GIF에서 패널과 항목의 위치를 확인했다. 기존 Gemini 화면 구성의 `sl-nav`, `sl-tab`, `sl-seg-group`을 재사용하고 프로젝트의 아이콘과 색을 적용했다. [프로필](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_userprofile.html) · [일반](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_general.html) · [기기 연결](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_connection.html) · [퀵 컨트롤](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Systemoverview_quickcontrol.html)
- 프로필·일반·기기 연결 상세는 왼쪽 메뉴, 가운데 항목, 오른쪽 이미지/안내로 배치했다. 프로필 이름 편집은 이름 행을 누르면 열린다. 퀵 컨트롤은 편집 상태에서도 타일을 3열로 표시하고 아래에 초기화·취소·완료를 배치했다. 저장 데이터 및 Android 연결 경로는 0.4.4와 호환된다.
- 배포 파일은 `deliverables/K8-5W-홈-0.4.5.apk`다. API 19 빌드를 검증했다. 1920×720 마우스와 1280×480 터치 환경에서 프로필 편집·전환, 퀵 컨트롤 저장·취소·재배치, 위젯 재배치, 시간 표시 저장, 기기 연결 전환과 하단 뒤로 가기를 확인했다. 실차 설치 및 반응은 확인 전이다.

## 차량용 APK 0.4.4 매뉴얼 대조

- 홈의 사용자 표시, 퀵 컨트롤의 프로필 전환, 설정의 사용자 프로필에서 테마 프로필을 관리한다. 운전자 1·2와 게스트는 선택 후 확인할 때 바뀌며, 운전자별 표시 이름과 사진은 앱에 저장한다. Kia Connect와 순정 설정으로 들어가는 버튼을 제공한다. [기아 ccNC 사용자 등록·변경](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Userprofile.html) · [사용자 프로필 설정](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_userprofile.html)
- 퀵 컨트롤에서 항목의 표시 여부와 순서를 편집한다. 완료를 눌러 저장하고, 취소하면 기존 배치를 유지한다. 초기화는 편집 화면의 기본 배치를 복원한다. 타일은 1초간 누른 다음 다른 타일에 놓아 순서를 변경할 수 있다. [기아 ccNC 퀵 컨트롤](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Systemoverview_quickcontrol.html)
- 화면구성의 분할 화면에서 손잡이를 1초간 누르고 끌어 위젯을 정렬한다. 위·아래 버튼과 지도 항상 표시 규칙도 적용한다. [기아 ccNC 화면 구성](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_screenlayout.html)
- 일반의 날짜/시간에서 12·24시간 표시를 선택하면 테마 상단과 대기 화면에 적용한다. 설정, 전화와 퀵 컨트롤에서 기기 연결로 들어가 블루투스 상태와 폰 프로젝션을 각 탭에서 확인한다. 블루투스 등록·관리와 폰 프로젝션 실행은 Android 또는 연결된 앱으로 전달한다. [기아 ccNC 일반 설정](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_general.html) · [기아 ccNC 기기 연결](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_connection.html)
- REA로 설치 파일 `K8-5W-홈-0.4.3.apk`의 패키지와 내장 `assets/index.html`을 대조했다. APK SHA-256은 `6039919e1b43ed3fd81ab0fa34191dc9dc17922d650e1a3559064401e243d39f`이고, REA 내장 파일 인벤토리는 `ev_89e6c913250803394a3328b55875f79bd46ef1193b162bd2c4e78d2115926ef4`다. 매뉴얼은 ccNC 조작 흐름의 근거이며 테마는 자체 그래픽과 Gen5W 연결 경로를 사용한다.
- 배포 파일은 `deliverables/K8-5W-홈-0.4.4.apk`다. SHA-256 `49119facaae61e4ad3e62e61c74d24e6bb5fa16071d80d9fb813f0202fee16ad`, REA 인벤토리 `ev_e4e68c2b94ae4e1e79a86fa650b030a21107c5d25e4f763ae7babe27a45448f8`. API 19 및 0.4.3과 동일 서명을 확인했다. 1920×720 마우스·1280×480 터치 브라우저에서 프로필 전환, 이름·선택값 저장, 퀵 컨트롤과 분할 위젯 정렬, 시계 형식, 기기 연결 화면 및 Android 요청을 검증했다.
- 실차 순정 운전자 전환, Kia Connect 계정 등록, 보안·디지털 키, 경로 데이터, 물리 HOME·시동 자동 실행, 순정 화면 위 테마 하단바의 연결은 확인 전이다.

## 차량용 APK 0.4.3 사운드

- 공식 기아 ccNC 사운드 화면을 직접 확인해 왼쪽 설정 분류, 가운데 항목별 내용, 오른쪽 기능별 그림 구조를 적용했다. 사운드 화면에서는 지도 위젯을 숨기고 나가면 복원한다. [기아 ccNC 사운드 매뉴얼](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_sound.html)
- 고급 음향, 전후/좌우, 고음/중음/저음, 안내음, 라디오 음질, 운전자 보조, 연결 기기 메뉴를 표시한다. 순정 값을 확인하지 못한 항목은 연동 대기로 표시하며 기기 사운드 설정 버튼을 제공한다.
- 미디어 음량은 별도 탭에 유지했다. 퀵 컨트롤에서 사운드를 누르면 미디어 음량이 바로 열린다. 슬라이더와 음소거의 네이티브 요청은 이전 버전과 같다.
- `deliverables/K8-5W-홈-0.4.3.apk`가 차량용 파일이다. 1920×720·1280×480의 메뉴 전환, 음량 요청, 지도 위젯 복귀를 브라우저로 검증했다. 실차 순정 앰프 연동은 확인 전이다.

## 차량용 APK 0.4.2 디자인 점검

- 홈, 전체 앱, 대기, 내비, 미디어, 전화, 설정, 화면 구성, 퀵 컨트롤 화면을 1920×720과 1280×480에서 확인했다. KitKat용 설정 4×2 레이아웃에서 마지막 두 타일이 밀리는 문제와 상세 사운드 화면의 여백 및 헤더를 수정했다.
- 사운드의 큰 슬라이더는 Android 미디어 스트림의 현재 값을 표시하고, 놓을 때 최대 범위 안에서 값을 설정한다. 단계 버튼과 음소거도 제공한다. 순정 음향 효과·안내 음량은 별도 화면과 권한 확인이 필요하다. [기아 Gen5W 사운드 설정](https://ownersmanual.kia.com/ivi/STD_GEN5W/AVNT/KOR/Korean/010_Settings_sound2.html) · [기아 ccNC 사운드 설정](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_sound.html)
- 내비와 분할 위젯에 순정 지도 버튼을 배치했다. 미디어의 초기 목록과 재생 중 상태, 전화 탭 이름과 전용 키패드, 퀵 컨트롤의 실제 BT 상태, 디지털 액자의 선택한 사진 미리보기를 정리했다. 연락처·메시지·최근 통화는 순정 앱을 통해 확인한다.
- 결과물은 `deliverables/K8-5W-홈-0.4.2.apk`다. 화면 조작과 네이티브 요청은 브라우저 모의 및 API 19 빌드로 확인했다. 차량에서의 실제 Android 미디어 음량 반응은 확인 전이다.

## 차량용 APK 0.4.1

- 가로 화면의 홈 카드와 전체 앱 타일을 1초간 누르면 선택 테두리가 생긴다. 같은 화면의 다른 카드로 끌어 놓으면 새 위치를 저장한다. 전체 앱은 길게 눌렀다 떼면 화살표로도 순서를 바꿀 수 있다. 세로 미리보기의 홈 3카드는 고정 배치다.
- 대기 화면, 홈, 전체 앱 1·2 사이를 밀면 옆 화면이 함께 들어온다. 손을 뗄 때 이동 거리와 마지막 속도로 이동하거나 돌아오며 모션 줄이기 설정은 마무리 전환을 생략한다.
- 기기 연결에서 블루투스가 꺼져 있으면 Android의 켜기 확인 화면을 요청한다. 미디어의 블루투스·USB·FM·AM·자연의 소리에는 개별 앱 연결을 추가했다. 저장된 개별 앱이 없으면 미디어 또는 라디오의 기존 연결을 쓰고, 그마저 없으면 앱 선택 화면을 연다. 이 버튼은 순정 오디오의 내부 소스를 직접 바꾸지 않는다.
- **연결 완료 · 실차 반응 확인 전**은 순정 내비 실행, 설치된 미디어·라디오·전화·폰 프로젝션·음성 메모·Kia Connect·알림 앱 열기, Android 플레이어 키와 미디어 음량/음소거, 기기의 블루투스 상태와 활성화 확인, 전화번호 전달, 사진 선택이다. 사진은 테마 내부에서 사용하며 밝기는 테마 창에만 적용한다.
- **차량 연동 경로 미확인**은 홈의 순정 지도 화면, 실시간 목적지·남은 거리·도착 시각·회전/차선 안내, 저장 목적지 직접 안내와 경로 취소, 순정 전체 음악 목록과 탐색, 실제 FM/AM/BT/USB 소스 직접 전환, 최근 통화·연락처·문자, 순정 차량/클러스터/앰프 설정값 쓰기, 음성인식·운전자 프로필 연동, 순정 앱 위 하단바, 물리 HOME과 시동 자동 실행이다. 테마 내부에서 적용 가능한 그래픽·배치·사진·위젯은 앱 설정으로 유지한다.
- 새 파일은 `deliverables/K8-5W-홈-0.4.1.apk`다. 대상 Android 4.4/API 19. 실차는 연결되지 않았다.

## 차량용 APK 0.4.0

차량용 `assets/index.html`은 이 저장소의 Gemini 웹 화면에서 출발한다. 홈, 대기 화면, 전체 앱 두 페이지, 설정 8개, 화면 구성 4개, 미디어, 전화, 내비, 퀵 컨트롤, 분할 화면, 하단바의 구조를 사용하며 API 19용 레이아웃과 제스처를 넣었다. 앱에서는 예시 주소, 거리, 회전·차선 안내, 통화 목록, 날씨, 스포츠 값 대신 연결 상태를 표시한다.

| 화면 | 0.4.0 동작 | 실차에서 더 확인할 부분 |
|---|---|---|
| 화면 구성 · 디지털 액자 | Android 사진 선택에서 선택한 이미지를 앱에 저장한다. 대기 화면과 오른쪽 위젯의 세 번째 페이지에 적용한다. | 차량에 선택 가능한 이미지 앱이 있는지 확인한다. |
| 화면 구성 · 클러스터 · 대기 · 분할 | 게이지 Classic/Simple 미리보기, 테마 그래픽 A/B/C, 대기 사진·시계·표시 없음, 위젯 활성화·순서·열림 상태를 앱에 저장한다. 앱 타일 순서도 저장한다. | 순정 계기판이나 순정 분할 화면 자체를 바꾸는 경로는 확인되지 않았다. |
| 사운드 · 일반 · 퀵 컨트롤 | Android 미디어 스트림 음량, 미디어 음소거, 테마 창 밝기를 앱 안에서 조절한다. 현재 날짜·시간을 표시한다. | OEM 앰프 음량의 반응과 범위를 확인한다. 이퀄라이저·주행 사운드는 순정 화면 경로가 필요하다. |
| 미디어 | 현재 Bluetooth·USB 트랙 방송의 곡명, 아티스트, 재생 상태, 시간 및 진행률을 표시한다. 이전·재생/일시정지·다음은 Android 미디어 키를 보낸다. | 순정 플레이어의 키 수신, 전체 목록 조회, 재생 위치 이동, BT·USB·FM·AM 직접 전환을 확인한다. 소스 아이콘은 연결된 미디어/라디오 앱을 연다. |
| 전화 · 기기 연결 | 블루투스 켜짐 상태를 읽는다. 키패드 번호는 Android 전화 걸기 확인 화면으로 전달한다. 연결 관리와 폰 프로젝션은 해당 앱을 연다. | K8 순정 주소록·통화 목록·메시지 및 블루투스 기기 전환 데이터는 미연동이다. |
| 차량 · 내비 · 음성인식 | 테마 안에 메뉴가 있고, 필요할 때 확인된 순정 앱이나 연결한 앱을 연다. | 클러스터, 운전자 보조, 공조·시트 값 쓰기, 내비 검색·경로·목적지 직행, 음성인식 설정은 구현하지 않았다. 실제 길안내 값·지도 화면의 공급 경로도 확인 전이다. |
| 프로필 · 전체 앱 | 테마에 표시할 프로필을 선택한다. 음성 메모, Kia Connect, 알림은 설치된 앱을 선택해 연결한다. | 순정 운전자 프로필과 연결되는 경로는 확인 전이다. |

- `deliverables/K8-5W-홈-0.4.0.apk`는 API 19용 빌드다. Chromium에서 구형 API와 CSS Grid 감지를 끈 상태로 1920×720 마우스와 1280×480 터치에서 페이지 이동, 화면 구성 저장, 미디어 버튼, 음량 요청, 키패드 요청을 검증했다. 연결된 헤드유닛이 없어 설치와 OEM 버튼 반응은 실차 확인 전이다.

## 차량용 APK 0.3.0 설정

- 홈 하단 또는 전체 앱의 **설정**을 누르면 차량·일반·내비게이션·사운드·기기 연결·사용자 프로필·음성인식·디스플레이가 나온다. 한 줄 보기에서 가로로 스크롤할 수 있다. 오른쪽에 순정 지도, 순정 설정, 바로가기 설정을 배치했다.
- 차량 타일은 K8 실차 기록에서 확인한 `com.hkmc.software.vehiclesetting` 패키지의 공개 실행 화면을 찾는다. 일반은 연결된 순정 설정 앱 또는 Android 설정, 사운드·기기 연결·디스플레이는 Android 공개 설정 인텐트를 사용한다. 기기 연결의 기본 화면은 블루투스 설정이다.
- 내비 설정은 순정 내비 시작 후 안에서 설정을 선택하도록 안내한다. 사용자 프로필·음성인식은 순정 설정의 시작 화면을 열고 사용자가 직접 선택한다. 개별 설정 값을 쓰거나 좌표로 순정 버튼을 누르는 로직은 없다.
- 각 타일은 **바로가기 설정**에서 해당 기기의 실행 가능한 앱으로 재연결할 수 있다. 순정 설정 내부 탭이 공개 인텐트나 Activity를 제공하는지, 런처 앱에서 열 수 있는 권한이 있는지는 실차 확인이 필요하다.
- 결과물은 `deliverables/K8-5W-홈-0.3.0.apk`다. 설치 예시의 파일명을 바꿔 사용한다. GitHub Pages 웹 데모는 별도다.

## 차량용 APK 0.2.1

- 홈 카드에 우리 집, 회사, 재생·일시정지, 미디어 앱의 원형 아이콘 버튼을 적용했다. 재생 상태에 따라 아이콘과 접근성 이름이 바뀐다. 버튼의 차량 연결 경로는 0.2.0과 같다.
- 차량용 파일은 `deliverables/K8-5W-홈-0.2.1.apk`다. 아래 0.1.0 설치 명령은 APK 파일명을 바꿔 사용한다.

## 차량용 APK 0.2.0

- `deliverables/K8-5W-홈-0.2.0.apk`는 기존 `io.github.k8home` 패키지와 같은 서명으로 빌드했다. 차량용 `assets/index.html`은 API 19용 화면으로, GitHub Pages의 `index.html`과 `demo/index.html`은 웹 예시다.
- 순정 내비는 확인된 `com.mnsoft.navi/.NaviApp`에 연결했다. 미디어·전화·라디오·폰 프로젝션은 차량에서 실행 가능한 앱을 처음 한 번 선택해 연결한다. 설정은 저장해 둔 앱을 우선 사용하고, 그다음 Android 설정을 시도한다.
- 재생·일시정지 버튼은 Android `AudioManager.dispatchMediaKeyEvent`에 키를 전달한다. 수신 중인 OEM Bluetooth·USB 오디오 위젯 브로드캐스트에서 곡명·아티스트·재생 상태를 읽는다. 상태를 받기 전에는 재생/정지 전환 키를 사용한다. OEM 오디오가 이 키에 반응하는지, 현재 펌웨어에서 위젯 데이터가 전달되는지는 실차 확인이 필요하다.
- 우리 집·회사 버튼은 순정 내비를 여는 안내를 제공한다. 순정 저장 목적지 직행과 홈 안의 실시간 지도는 확인된 앱 인터페이스가 더 필요하다. 실제 통화 목록, 차량 HOME 키와 시동 자동 실행, 순정 앱 위의 공통 하단 메뉴는 웹 데모의 화면과 별개다.
- API 19 SDK로 컴파일하고 v1 서명·APK 정렬·이전 APK의 인증서 일치를 확인했다. 아래 0.1.0 설치 명령은 파일명을 `K8-5W-홈-0.2.0.apk`로 바꾸면 적용된다. 연결된 차량이 없어 실차 설치는 아직 수행하지 않았다.

## 최초 APK 0.1.0 설치와 실행

- 차량용 파일은 작업공간의 `deliverables/K8-5W-홈-0.1.0.apk`다. K8의 1920×720 가로 화면과 Android 4.4(API 19)를 대상으로 했다. 앱 패키지는 `io.github.k8home`이다.
- 차량에서 일반 외부 APK 설치를 사용할 수 있는 경우 APK를 설치하고 앱 목록의 **K8 홈**을 연다. 사용 중인 ADB 연결이 있다면 아래처럼 설치하고 실행할 수 있다.

```text
adb install -r "deliverables/K8-5W-홈-0.1.0.apk"
adb shell am start -n io.github.k8home/.HomeActivity
```

- 첫 화면은 왼쪽 내비게이션, 가운데 미디어, 오른쪽 전화다. 하단 탭 또는 좌우 밀기로 전체 앱 화면과 홈을 전환한다.
- **순정 지도 열기**는 확보한 K8 5W APK 매니페스트에서 확인한 `com.mnsoft.navi/.NaviApp`을 연다. 차량의 내비 앱이 해당 실행을 받아들이는지 확인이 필요하다.
- 미디어, 전화, 라디오, 폰 프로젝션, 설정은 첫 사용 시 차량 앱 목록에서 연결한다. **바로가기 설정**에서 바꾸거나 해제할 수 있다. 이번 버전은 이 앱들로 이동하는 바로가기를 제공한다.
- **나가기**는 앱을 닫는다. Android 앱 관리에서 삭제하거나, 연결된 ADB에서 `adb uninstall io.github.k8home`으로 제거할 수 있다. 별도 앱으로 설치하며 HOME 인텐트 필터, 부팅 리시버, 차량 제어 권한이 없다.
- PC에서 `assets/index.html`을 열어 화면을 확인할 수 있다. 차량 앱 실행과 실제 지도 표시에는 차량 환경이 필요하다.

## 추후 지도 연결

- 화면 좌측 상단의 `#map-slot`은 처음부터 홈 배치에 포함된다. 그 아래 순정 지도 열기 버튼은 별도로 작동한다.
- 표시할 지도 제공자를 확인한 뒤 UI 스레드에서 `HomeActivity.setMapContent(View)`에 해당 View를 넘긴다. `null`을 넘기면 지도 준비 화면이 돌아온다. 로컬 HTML이 슬롯의 위치와 크기를 네이티브 컨테이너에 알려 주며, 전체 앱 화면으로 넘어갈 때 컨테이너가 숨는다.
- 이 연결 지점은 앱에 붙일 수 있는 View를 받는다. 순정 내비의 별도 Activity를 해당 View로 제공하는 경로, 지도 공급자 권한 및 화면 생명주기는 실차 증거가 필요하다.

## 빌드와 검증

- 기존 K8 앱과 같은 SDK 19 호환 빌드 흐름을 사용한다. JDK와 SDK build-tools 35.0.1이 설치된 환경에서 프로젝트 폴더의 `build.py --sdk <경로> --jdk <경로> --output <APK 경로>`를 실행한다. 개발용 서명 키는 로컬 `.build/`에 유지한다.
- API 19 부트클래스패스로 컴파일하고 DEX를 만들었다. APK v1 서명과 zipalign, 패키지명, 한국어 앱 이름, 게시한 HTML과 앱 내 자산의 일치를 확인했다.
- 1920×720 Chromium에서 홈, 여섯 앱 타일, 터치 밀기, 뒤로 가기·나가기, 바로가기와 순정 내비 동작 전달을 확인했다. 지도 View 자리 채우기·초기화·앱 화면에서 숨김은 브라우저 연결 모의로 확인했다. 브라우저 390px에서 가로 넘침과 PC 모드 안내도 점검했다.
- 연결된 차량이나 Android 기기가 없어 APK 설치, Android 4.4 WebView 렌더링, 순정 앱 실행 및 실제 지도 View 표시를 실차에서 검증해야 한다.

## 하단 메뉴 예시안

- 작업공간의 `deliverables/K8-하단바-예시안/index.html`을 열면 하단 뒤로·홈·전체 앱·내비·미디어와 오른쪽 위젯 전환을 체험할 수 있다. 미디어의 재생 예시 상태는 홈과 상세 화면에서 같은 값으로 표시한다.
- Chromium에서 1920×720, 768×432, 390×844의 배치와 홈 복귀·이전 화면 복귀·상태 표시를 확인했다. APK에는 아직 반영하지 않았다. 순정 화면이나 차량 홈 버튼이 발생시키는 동작에서 새 홈으로 돌아가는 경로는 5W 실차에서 확인이 필요하다.
- [공개 웹 예시안](https://k8-5w-home-demo.plumelf4.chatgpt.site/) 2판에서 홈↔전체 앱의 좌우 마우스 드래그와 터치 스와이프, 지도 카드와 상세 화면의 우리 집·회사 선택, 미디어 카드와 상세 화면의 재생·일시정지를 제공한다. 1920×720 마우스와 390×844·844×390 터치에서 검증했다. 길 안내와 재생 상태는 웹 예시용이다.
