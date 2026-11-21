# K8 5W 차량 인포테인먼트 API 지도

2026-11-21 차량(무선 ADB)에서 직접 확인한 결과다. 근거는 차량 앱 APK 매니페스트 스캔(`docs/vehicle-api.json`)과 순정 내비 dex 문자열표(`docs/vehicle-nav-strings.txt`), JADX 1.5.3 디컴파일(`.build/jadx/navi`)이다.

- 차량: `wp_daudioplus_kia_kr` / 안드로이드 4.4.2 (API 19) / 빌드 `KVT49L` / 차종 코드 `GL3AH.S5BM`
- REA MCP의 안드로이드 공급자는 `REA_JADX_MCP_JAR`·`REA_ADB_PATH` 환경변수가 서버 프로세스에 없어 쓸 수 없었다. REA 번들(JADX 1.5.3, apktool)을 같은 방식으로 직접 실행해 동일 분석을 했다.
- 내비 dex의 일부 메서드는 보호되어 JADX가 디코하지 못한다(`Unknown instruction`). 그래서 **문자열표와 매니페스트가 1차 근거**이고, 소스 수준 해석은 가능한 범위만 했다.

## 1. 순정 내비 `com.mnsoft.navi` (HKMC_Navi_ExSL.apk, 338 KB / dex는 odex)

### 차량 로그로 확인한 실제 데이터 통로 (2026-11-21 06:00 실차 로그)

내비 프로세스(`com.mnsoft.navi`, pid 1337)가 **매초** 다음 방송을 쏜다.

- action: **`com.hkmc.telematics.gis.action.NAVI_GPS_CHANGED`**
- extras (실제 로그 인용):
  `com.hkmc.telematics.gis.extra.DestDistance=0`, `DestRemainTime=0`, `DestDistanceType=2`, `DestRemainTimeType=1`, `LAT`, `LON`, `ALT`, `HEADING`, `TYPE`, `PDOP`, `HDOP`, `TIME=20261121060018`
- **값이 숫자다.** 문자열이 아니라 정수(거리·시간)와 타입 코드(단위 구분)가 붙어 온다. 이게 "마이너스 오프"의 정체였다.
- 같은 방송을 `ModeManagerService`, `Location2Service`, `DMB_APP`, `USBVideo`가 받고 있다. 즉 시스템 표준 경로다.

> 주의: `com.mnsoft.navi.action.extra.DestDistance` 같은 이름도 dex 문자열표에는 있으나, **차량 로그에서 실제로 매초 오는 값은 `com.hkmc.telematics.gis.extra.*` 쪽**이다. 두 경로를 모두 읽어야 한다.

### 읽기 (방송)

| action | extras | 근거 |
|---|---|---|
| `com.mnsoft.navi.action.Route_Guide_Status` | `ROUTE_GUIDE_STATE` (0 없음 / 1 있음 / 3 선택), `IS_BOOT` | **sticky**로 쏜다. `com.mnsoft.navi.message.c` |
| `com.mnsoft.navi.action.Route_Guide_NewStatus` | `ROUTE_GUIDE_NewSTATE` | 문자열표 |
| `com.mnsoft.navi.action.DestinationInfo` | `PoiName`, `Address`, `LAT`, `LON`, `TYPE`, `DestRemainTime`, `registpoint` | `com.mnsoft.navi.message.c`의 `a(Context, String, String, double, double, int)` / `a(Context, String, String, double, double, int, int, int)` |
| `com.mnsoft.navi.action.Route_Guide_Set` | 길안내 설정 | 문자열표 |
| `com.mnsoft.navi.action.Route_Guide_ACTION` | 길안내 동작 | 문자열표 |
| `com.mnsoft.navi.action.RouteSearchState` | 경로 검색 상태 | 문자열표 |
| `com.mnsoft.navi.action.Navi_View_Status`, `Navi_Screen_Changed` | 화면 상태 | 문자열표 |
| `com.mnsoft.navi.action.OTA_Route_Guide_Status` | 내비→시스템 길안내 | 문자열표 |

**중요한 확인**: `com.daudio.app.dipo`(순정 CarPlay) 매니페스트가 `com.mnsoft.navi.action.Route_Guide_Status`를 **intent-filter로 수신**한다. 즉 이 방송은 OEM 앱 밖에서도 받는 경로가 보장돼 있다.

`registpoint` extras는 목적지 종류(우리 집·사무실 등)를 알려 준다. `DestRemainTime`은 남은 시간, `DestDistance`는 남은 거리다(단위 타입 extras가 `DestDistanceType`·`DestRemainTimeType`으로 따로 있다).

### 쓰기 (내비에 명령)

| action | 하는 일 | 근거 |
|---|---|---|
| `com.hkmc.intent.action.ACTION_NAVI_MENU` | 내비 메뉴 열기 | 내비 매니페스트 receiver |
| `com.hkmc.intent.action.ACTION_NAVI_CURRENTPOS` | 현재 위치 | 내비 매니페스트 |
| `com.hkmc.intent.action.ACTION_NAVI_TOTALSEARCH` | 전체 검색 | 내비 매니페스트 |
| `com.hkmc.intent.action.ACTION_ADD_POINT` | 지점 추가 | 내비 매니페스트 |
| `com.hkmc.intent.action.ACTION_ADD_SEARCH` / `ACTION_ICP_SEARCH` | 검색 추가 / 주변 검색(`_RESPONSE`로 응답) | 내비 매니페스트 |
| `com.mobis.intent.action.CANCEL_ROUTE_SHORTCUT` | 경로 종료 | 문자열표 |
| `com.mobis.intent.action.REROUTE_SHORTCUT` | 재탐색 | 문자열표 |
| `com.hkmc.intent.action.Request_Info_Destination` / `Response_Info_Destination` | 목적지 정보 요청/응답(`remain_dist`, `arrival_time`) | 문자열표 |
| `com.hkmc.intent.action.ACTION_ROUTE_SEARCH` | 경로 검색 실행 | 문자열표 |
| `com.hkmc.intent.action.ACTION_SHOW_MAP` | 지도 표시 | 문자열표 |
| `com.mobis.intent.action.SSEARCH_RESULT` / `SSEARCH_SCREEN` | 통합 검색 결과를 내비 검색 화면으로 | 문자열표 + UserProfile 매니페스트 |

### 등록 지점·즐겨찾기 저장소

`content://com.mnsoft.navi/Destination_Table`, `Search_Table`, `NonSearch_Table`, `State_Table` (문자열표). 등록 지점 상태는 `com.mnsoft.navi.action.extra.registpoint`, `com.mnsoft.navi.REGISTPOINT_STATE_LS3`.

## 2. 미디어·라디오

| 항목 | 값 |
|---|---|
| 위젯 갱신 방송 | `com.hkmc.audio.btaudio.widgetupdate`(BT), `com.hkmc.audio.usb.widgetupdate`(USB) |
| 위젯 요청 | `com.hkmc.request.widgetupdate` |
| 위젯 extras | `filename`, `artist_name`, `playstate`, `audio_current_time`, `audio_total_time` |
| 원격 서비스 | `com.hkmc.intent.action.REMOTE_SS_BTAUDIO_SERVICE`, `android.intent.action.REMOTE_MUSIC_SERVICE`, `REMOTE_RADIO_SERVICE`, `REMOTE_NATURE_SOUND_SERVICE`, `REMOTE_CARPLAY_SERVICE` |
| AV 알림 프로토콜 | `android.intent.action.VIT_NOTI_AV_PLAY_CONTROL`, `VIT_NOTI_AV_PLAY_LIST_VIEW`, `VIT_NOTI_AV_PLAY_TRACK_NUMBER`, `VIT_NOTI_AV_RANDOM_CONTROL`, `VIT_NOTI_AV_REPEAT_CONTROL`, `VIT_NOTI_AV_MODE_CHANGE`, `VIT_NOTI_AV_RADIO_SET_FREQUENCY`, `VIT_NOTI_AV_SET_RADIO_SEARCH_MUSIC`, `VIT_NOTI_GOTO_RADIO_LIST` |
| 시스템 바로가기 키 | `com.hkmc.intent.action.FM1_SHORTCUT`, `AM_SHORTCUT`, `MEDIA_BT_SHORTCUT`, `MEDIA_USB_AUDIO_SHORTCUT`, `MEDIA_BLUETOOTH_SHORTCUT`, `MEDIA_NATURESOUND_SHORTCUT`, `MEDIAGATE_SHORTCUT`, `DIPO_SHORTCUT` |
| **버튼 매핑 공식 방법** | 시스템 속성 `persist.sys.key.custom` → `com.mobis.intent.action.CONNECTIVITY_SHORTCUT`, `persist.sys.key.swrccustom` → `com.hkmc.intent.action.MAP_SHORTCUT` |

`persist.sys.key.*` 두 속성이 차량 버튼을 어떤 broadcast로 보낼지 지정한다. 이 값을 바꾸면 차량 버튼을 우리 앱으로 돌릴 수 있다(서개모에서 못 했던 "NAV 버튼 바꾸기"의 공식 경로).

## 3. 통화

| 항목 | 값 |
|---|---|
| 통화 상태 방송 | `com.broadcom.bt.hfdevice.profile.action.CALL_STATE_CHANGED` (MediaGate·VoiceMemo가 수신) |
| 대기 | `com.broadcom.bt.hfdevice.profile.action.MOBIS_BT_HOLD_CALL` |
| BT 아이콘 정보 | `com.broadcom.bt.hfdevice.hfbtcm.action.BTCM_HFP_ICON_INFO`, `BTCM_A2DP_ICON_INFO` |
| 전화 UI | `android.bluetooth.intent.action.INCOMING_CALL`, `RECENT_CALLS`, `com.android.phone.action.TOUCH_DIALER`, `android.intent.action.ACTION_MDN_STATE_CHANGED` |
| Broadcom BT 상태 | `com.broadcom.bt.hfdevice.hfbtcm.action.BTCM_CONNECTION_STATE_CHANGED` (BTAudio) |

## 4. 우측 분할 화면·테마 자리 `com.mobis.splitscreen.right` (RightScreenHome.apk, 101 MB)

- 원격 서비스: `android.intent.action.REMOTE.SPLIT.SERVICE`
- 권한: `SYSTEM_ALERT_WINDOW`, `INTERNAL_SYSTEM_WINDOW`, `STATUS_BAR`, `RECEIVE_BOOT_COMPLETED`, `CALL_PHONE`
- 테마가 붙는 자리가 이 앱이다. 우측 위젯을 테마 안에서 그리려면 이 앱 안에 직접 들어가야 한다.

## 5. 차량 상태 (시스템 속성)

| 속성 | 의미 |
|---|---|
| `sys.hkmc.navistatus` | 내비 상태(측정값 3 = 내비 실행) |
| `persist.sys.setting.cluster` | 클러스터 테마 번호(측정값 4) |
| `persist.sys.climate.*` | 공조 옵션(구역·좌/우·터널·언더패스·외기) |
| `persist.sys.seatPositioning`, `showSeatMemoryt`, `seatWheelHeat` | 시트 위치·시트 메모리·열선 |
| `persist.sys.usmAmbientLight` | 앰비언트 라이트 |
| `ro.product.opt.drivemode` | 주행 모드 옵션 |
| `ro.product.vehicleinfo` | 차종 코드(`GL3AH.S5BM`) |
| `ro.mobis.onebindir`, `ro.mobis.targetdir` | 차량 바이너리 경로(`gl3hev_kr`) |
| `tcc.mobis.AMS.state` | 텔레매틱스 상태 |

## 6. 카메라 `com.hkmc.system.app.rearcamera`

`com.hkmc.intent.action.CAMERA_DISPLAY_TUNING_ON/OFF`, `CAMERA_GUIDELINE_TUNING_ON/OFF`, `PGS_BINDER_DIED`, `com.hkmc.telematics.safety.action.EMERGENCY`.

## 7. 사용자 프로필·홈 설정

- `com.mobis.user_profile.intent.action.RESET_PROFILE`, `WRITE_JSON`, `READ_JSON`, `SHOW_FRS`, `CHECK_RUN_FRS`, `MOVE_GUEST_TO_DR1`, `GUEST_LOG_OFF`
- `com.hkmc.intent.action.AGREE_USER_LICENSE`, `mobis.intent.action.CLIMATE_VARIANT`
- `com.android.settings.ACTION_SYSTEM_DEFAULT`, `..._ALL_USER`, `..._CUR_USER` (기본 앱 지정)

## 8. 음성·안전

- 음성 엔진: `mediazen.intent.action.SERVICE_START`, `SERVICE_STOP`, `SERVICE_TTS_START`, `VH_SERVICE`, `SERVICE_ASR_ENGINEERING_MODE` (VoiceHandler), `com.hkmc.system.app.voiceinfomenu`(VoiceCommand), `com.hkmc.intent.action.PTT_SHORTCUT`
- 안전: `com.hkmc.telematics.safety.action.EMERGENCY` (DiPO·후방카메라가 수신), `com.hkmc.telematics.service.action.INIT_ALL_DATA_FOR_TEST_MODE`

## 9. 아직 못 읽은 것

- 내비 dex는 보호되어 일부 메서드 본문을 못 읽었다. 방송·extras 이름은 문자열표로 전부 확보했지만, 각 extras의 단위(미터/초인지)는 값만 보면 알 수 없다. 실차에서 한 번 로그를 남기면 확정된다.
- `IInterfaceService` 바인드 인터페이스의 메서드 목록은 AIDL 스텁이 다른 dex에 있어 이번엔 못 뽑았다. 거리·시간을 방송이 아니라 이 서비스로 읽는 경로가 남아 있다.
- 클러스터(`com.hkmc.cluster.*`) AIDL은 이번에 안 가져왔다. 클러스터 테마(목적지·차선·횡단보도)는 따로 파야 한다.
- 공조·시트·운전자보조의 실제 데이터는 AIDL이나 차량 서비스 바인드로만 나온다. 속성은 설정값(저장된 값)일 뿐 실시간 상태가 아니다.
