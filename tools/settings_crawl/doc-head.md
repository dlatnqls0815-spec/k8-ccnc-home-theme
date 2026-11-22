# 5W 순정 설정 실차 수집 (2026-11-22)

사용자 차(K8 하이브리드, 5W)에 adb로 붙어 순정 설정 화면을 돌며 모은 결과다. 원본(캡처, 화면 구조 XML, 설정 표, 순정 APK와 odex)은 `private/vehicle/settings-20261122-153149/`에 있다(커밋 안 함). 이 문서에는 이름·주소 같은 개인 정보를 넣지 않았다.

## 수집 방법과 안전
- 수집 프로그램: `tools/settings_crawl/crawl.py`(탭이 있는 설정), `crawl_vehicle.py`(차량 설정).
- 왼쪽 탭과 분류는 모두 눌렀다. 오른쪽은 **› 아이콘이 있는 줄(하위 화면·선택 목록이 열리는 줄)만** 열고 뒤로 나왔다. 체크박스·버튼·슬라이더는 누르지 않았다.
- 다녀온 뒤 목록 상태가 달라지면 멈추게 했다. 수집 전후 설정 표(system/global/secure)와 속성을 비교해 **값이 모두 같음**을 확인했다.
- 첫 시험 실행에서 사운드 "고음/중음/저음 초기값"과 "오디오 음량 자동 줄이기"가 눌려 바뀌었다. 바뀌기 전 화면으로 원래 값(고음 +4, 중음 0, 저음 +10, 자동 줄이기 켬)을 확인해 **즉시 되돌렸다.**
- 이 차의 adb는 `exec-out`을 지원하지 않는다(캡처는 `screencap` 후 `pull`). Git Bash에서는 `MSYS_NO_PATHCONV=1`이 필요하다.
- 설치: 0.5.0-test8(차에는 0.4.48이 있었다). 홈 화면이 정상으로 뜨는 것을 캡처로 확인했다.

## 핵심 발견
- **운전자별 값**: 대부분의 키가 `키`, `키_dr1`, `키_dr2`(그리고 `키_g`)로 따로 있다. 현재 운전자는 `user_profile_driver`(지금 1). 화면 값은 `키_dr{운전자}`와 맞고, 접미사 없는 키와는 다를 수 있다. 예: 내비 안내 일시 정지는 화면에서 켜짐이고 `sound_mute_nav_guide_dr1=1`, `sound_mute_nav_guide=0`.
- **고음/중음/저음**: `hkmc_sndset_treble_USER_1`, `_middle_`, `_bass_`에 10을 0으로 저장한다(고음 +4 = 14, 저음 +10 = 20). 앰프 상태(`MicomAudioStatus`)와 별개로 일반 앱이 읽을 수 있다.
- **사용자 버튼(☆·★)과 MODE**: 설정 표가 아니라 **시스템 속성**이다.
  - ☆(내비게이션 쪽) `persist.sys.key.custom` = `com.mobis.intent.action.CONNECTIVITY_SHORTCUT` → 화면 선택 "폰 프로젝션".
  - ★(핸들) `persist.sys.key.swrccustom` = `com.hkmc.intent.action.MAP_SHORTCUT` → 화면 선택 "MAP".
  - MODE `persist.sys.key.mode` = 129 → 화면 선택 "자연의 소리".
  - `btn_n_left/right`는 이 차의 사용자 버튼이 아니다(N 라인용, 값 0).
- **버튼 선택지(이 차 ☆ 목록)**: 폰 프로젝션, 프라이버시 모드, 하이브리드, 핸즈프리 기기 연결 변경, 홈, 화면 켜기/끄기, 후석 취침 모드, DMB, Kia Connect, MAP, Radio/Media On/Off, 없음. ccNC 설명서 목록(경로 취소·날씨 등)과 다르다. 자원 번호표(`integer/btn_unified_custom_*`, 23401~23437)도 있지만 실제 저장은 의도 문자열이라, 테마는 의도 문자열 → 이름 표로 보여 주면 된다(나머지 의도 문자열은 한 번씩 골라 보며 확정).
- **[∧]/[∨]**: `button_settings_seek`(0 = 프리셋 채널 변경). **VOL/TUNE**: `tune_knob_function`(1), `vol_tuneknob_swap`(0). **후석 잠금** 탭이 있다.
- **순정 설정 활동 이름(확인)**: `com.android.soundsettings/.SoundSetting`, `com.android.displaysettings/.DisplaySetting`, `com.android.generalsettings/.GeneralSettings`, `com.android.buttonsettings/.ButtonSettings`, `com.android.vehiclesettings/.VehicleSetting`, `com.android.btsettings/com.android.settings2.bluetooth.BluetoothSettings`, `com.hkmc.system.app.userprofile/.UserProfileMainActivity`, `com.android.themesettings/.MainActivity`, 설정 첫 화면 `com.android.settings/.Settings`. 하위 화면: 사운드 `sound.MeridianHorizonCustom`, `sound.CommonRadioButtonList`, `sound.VolumeSubActivity`.
- **차량 설정 분류(이 차)**: 운전자 보조, 드라이브 모드, 환경차, HUD, 클러스터, 공조, 시트, 라이트, 도어, 디지털 키, 편의. 값은 차량 쪽(CAN)이라 일반 앱이 읽을 설정 키는 대부분 없다. `com.android.vehiclesettings.provider.VehicleSettingsProvider`가 있다(권한·내용 확인 필요).
- **설정 첫 화면(이 차)**: 차량, 내비게이션, 사운드, 기기 연결, 사용자 프로필, 음성인식, 화면구성, 디스플레이, 버튼, Kia Connect, 일반(11칸, 6+5).
- **순정 사운드 탭(이 차)**: 고급 음향(Meridian Horizon 커스텀, Meridian Intelli-Q, 오디오 음량 자동 줄이기), 전후/좌우, 고음/중음/저음, 안내음, 라디오 음질, 운전자 보조, 연결 기기.

## 테마가 읽을 수 있는 순정 설정 키(현재 값, 운전자 1)
| 화면 항목 | 키 | 지금 값 |
|---|---|---|
