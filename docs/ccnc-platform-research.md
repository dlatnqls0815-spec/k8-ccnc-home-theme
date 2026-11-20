# 현대·기아 ccNC AVN 공통 플랫폼 조사

2026-11-20 기준. 한국형 ccNC AVN을 대상으로 현대·기아 차종의 공통점을 조사했다. ccNC Lite, ccIC, Pleos Connect와 기존 K8 표준형 5W는 별도 플랫폼으로 취급한다. 재사용할 때는 차종, 지역, 소프트웨어 버전, 하드웨어 옵션을 함께 기록한다.

## REA로 확인한 공통 범위

현대모비스 공개 목록에서 `K8 HEV (GL3kH)`와 `EV9 (MV1)`에 `AVN CCNC R7`부터 `R10`까지 공통으로 표시된다. 두 모델의 R10 `SOURCE FILE` ID는 `FILE_000000000061668`로 같다. `LICENSE FILE` ID는 각각 `FILE_000000000061926`, `FILE_000000000061942`이고, 실제로 내려받은 ZIP 두 개는 216,906바이트, SHA-256 `5fa2e6fa7d2a65f434015323e1a8a94651a2ad53ba21366e9ee6001b749b4fa4`로 동일하다. R10은 모비스 공개 묶음의 이름이며 기아 차량 업데이트의 `001.002.260813`과 일대일 대응하는지는 확인되지 않았다. [현대모비스 공개 목록](https://www.mobis.com/kr/tech/rnd.do#open)

| REA 대상 | 확인한 내용 | 원본 Evidence ID |
|---|---|---|
| K8 HEV와 EV9 R10 라이선스 ZIP | ZIP 두 개의 SHA-256 일치. 각 ZIP에는 HTML 문서 한 개가 들어 있다. | `ev_8e9bd9991052e7699234a447ccb940bba786f56e00cefff3e1f63c5fde1a9451`, `ev_e8137c4358d983ff9bc6359c5a8c34a636266f641dd2919eba9f7e1dd76d5b55` |
| `ccNC_42dot_review_opened_code.zip` | KenLM 소스와 패치, plog 1.1.5 공개 묶음. | `ev_1a5a4612a7515c7095d7ae4ba9edfd84010e738ac320283c5a8fd021c87a7636` |
| `ccNC_capicxx-core-runtime_src_opened_code.zip` | CommonAPI C++ 코어 런타임 헤더·소스. | `ev_fe1fc21f14ffd881b5620e6dfd6560afe1ac9f374d8b75448af7c79347003e6e` |
| `ccNC_capicxx_someip_runtime_src_opened_code.zip` | CommonAPI SOME/IP 런타임 헤더·소스. | `ev_9a30bf7785d3677cc69f2579583712f0bfed2864f4d84690bbc39b08c10e634b` |
| `ccNC_connman-mobis_src_opened_code.zip` | ConnMan 네트워크 관리 코드. `gdbus`, `gdhcp` 파일 포함. | `ev_e23d754d5f8380557505b328e0940a3e2a127222fb5803a3744f2fd678f59647` |
| `ccNC_gstreamer_src_opened_code.zip` | GStreamer `plugins-ugly` 관련 공개 파일. | `ev_202d2f857cde671bab26a6a6880df8fa887e090458ac04dbd8f57669f478499c` |

공식 R10 소스 ZIP의 처음 32 MiB에서 압축 항목 18개를 끝까지 읽어 CRC-32를 검증했다. 위 소스 ZIP들은 그중 REA로 확인한 하위 묶음이다. 다음 항목인 `ccNC_LG_WebOS_review_opened_code.zip`은 ZIP 로컬 헤더에 압축 크기 1,221,697,254바이트로 표시된다. 현재 확보한 부분에는 이 항목의 전체 데이터와 상위 ZIP의 중앙 디렉터리가 없어, 해당 하위 묶음의 내부와 상위 전체 인벤토리는 확인하지 못했다. 공식 서버의 Range 요청은 `206` 대신 전체 응답 `200`을 반환했다. [공식 R10 오픈소스 다운로드](https://www.mobis.com/cmm/fms/FileDown.do?fileId=FILE_000000000061668&fileSn=0&fileNm=OSA_202611030153571580.zip)

## 공통 고지 구성요소

[R10 구성요소 CSV](ccnc-r10-components.csv)에 고지 항목 780줄을 원문 순서로 기록했다. 항목명 594종, 표기 그대로의 항목명·버전 쌍 685종이다. 같은 제품의 버전이나 고지 항목이 복수로 적히기도 한다. 문서에는 다음이 나온다.

| 영역 | 확인한 항목 | 해석 범위 |
|---|---|---|
| 운영체제 및 실행 환경 | Linux Kernel, BusyBox 1.31.1·1.35.0, systemd 244.5 및 systemd-stable 249.3 | Linux 계열 구성요소를 확인했다. 차종별 커널 빌드 번호와 프로세스 목록은 미확인이다. |
| 화면 및 웹 | Wayland 1.18.0, Weston 8.0.0, wayland-ivi-extension 2.2.0, Chromium 64·92·120 | 그래픽과 웹 구성요소를 확인했다. 순정 홈이 HTML, QML, 네이티브 중 무엇으로 구현됐는지는 미확인이다. |
| 미디어 | GStreamer 1.18.5와 별도 플러그인, PulseAudio 15.0, ALSA, FFmpeg | 공통 미디어 구성요소를 확인했다. 곡 정보·재생 위치를 홈에 전달하는 인터페이스는 미확인이다. |
| 네트워크 및 프로세스 통신 | ConnMan 1.37, D-Bus, CommonAPI C++, vsomeip 3.1.7, DLT | 통신 라이브러리와 일부 소스가 공개됐다. 홈·지도 서비스의 호출 관계는 미확인이다. |
| 오픈소스 배포 내 플랫폼 표시 | `ccNC_LG_WebOS_review_opened_code.zip` 로컬 헤더 | LG webOS 관련 소스 묶음이 포함된다는 단서다. 모든 ccNC 차종의 런타임 구성이나 단일 OS 버전으로 일반화할 근거는 없다. |

## 인포테인먼트 화면과 기능

| 영역 | 공식 자료에서 확인한 사용자 동작 | 5W 프로젝트에서 확인할 연결 |
|---|---|---|
| 홈·전체 기능 | 홈 좌우 스와이프로 페이지 이동, 아이콘 길게 눌러 재배치, 전체 기능 목록 진입. 하단 표시줄에 뒤로·홈·메뉴 동작이 있다. [홈 화면 매뉴얼](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Systemoverview_home.html) | 홈 화면 상태, 제스처, 순정 앱 진입점. |
| 퀵 컨트롤 | 화면 위에서 아래로 밀어 열고, 항목 표시·배치를 편집한다. [퀵 컨트롤 매뉴얼](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Systemoverview_quickcontrol.html) | 홈 내 표현과 실제 시스템 제어 동작의 연결. |
| 분할 화면 | 가장자리에서 밀어 열거나 닫고, 위아래로 넘겨 항목을 바꾼다. 설정에서 순서를 편집하며 지도는 항상 표시된다고 설명한다. [분할 화면 매뉴얼](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Systemoverview_widget.html) · [화면구성 설정](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Settings_screenlayout.html) | 오른쪽 위젯과 홈의 지도 카드는 데이터 공급 경로를 각각 찾아야 한다. |
| 지도·길안내 | 지도 고정 2D, 주행 방향 2D·3D, 사양별 AR, 축척, 교통 정보, 재탐색, 경로 편집, 주변 시설. 적용 차량의 온라인 지도는 오프라인 전환을 지원한다. [지도 매뉴얼](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Map.html) · [온라인 내비 안내](https://update.kia.com/KR/KO/updateNoticeView/GiMjo4) | 지도 미리보기의 프레임·표면·상태 공급자와 카드 누름 시 지도 열기. |
| 음악 | 블루투스 오디오, 재생 목록, 이전·다음, 재생·정지, 재생 위치와 앨범아트. [블루투스 오디오 매뉴얼](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Media_btmusic.html) | 기존 5W 미디어 상태와 재생 조작을 재사용할 수 있는지 확인. |
| 전화 | 블루투스 통화, 연락처·최근 통화 동기화, 전화 화면 전환, 동기화 자료를 숨기는 프라이버시 모드. [최근 통화 매뉴얼](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/Phone_callhistory.html) | 기존 5W 전화 화면을 열고 장치 연결 상태를 표시하는 방법. |
| 그 외 | 폰 프로젝션, 사용자 프로필, 음성인식, 커넥티드 서비스, 공조·시트 화면, 주차 영상, OTA, 음성 메모, 엔터테인먼트, 차량 진단, 빌트인 캠, 인카페이먼트 등이 매뉴얼에 있다. 지원 범위는 모델·지역·사양·가입 상태에 따라 다르다. [기아 ccNC 종합 매뉴얼](https://ownersmanual.kia.com/ivi/ccNC/AVNT/KOR/Korean/) | 모델별 비교가 필요한 페이지다. 공통 홈의 분석 우선순위보다 뒤에 둔다. |

기아 EV9와 더 뉴 K8, 현대 그랜저·코나·아이오닉 계열에서 공통 플랫폼을 비교할 수 있다. 공식 업데이트 목록은 같은 ccNC에서도 모델 코드와 소프트웨어 패키지를 차종별로 구분한다. K8 예시는 `GL3MY26BH.SCBMC.KR`와 `GL3MY26BS.SCBMC.KR`, 기존 5W 비교 사례는 `GL3AH.S5BMC.KR`다. [기아 버전 목록](https://update.kia.com/KR/KO/updateNoticeView/software-version) · [현대 버전 목록](https://update.hyundai.com/KR/KO/updateNoticeView/software-version)

## 남은 바이너리 조사

공식 오픈소스 공개 묶음과 매뉴얼은 확보했다. 차에 탑재되는 ccNC 펌웨어, 순정 홈 실행 파일과 리소스, 지도 엔진 실행 파일은 확보하지 못했다. 작업 공간과 다운로드 폴더에서 확인한 `HKMC_Navi_ExSL.apk`·`.odex`는 기존 K8 5W 자료다. 로컬 C 드라이브 여유 공간은 약 3.4 GB였고 공식 USB 업데이트 안내는 32 GB 이상 저장장치를 요구한다. 전체 차량 업데이트를 확보한 것으로 기록하지 않는다. [기아 업데이트 안내](https://update.kia.com/KR/KO/home)

다음 분석 입력은 차종 무관하게 **정확한 모델·버전이 확인되는 ccNC AVN의 순정 업데이트 파일 또는 읽기 전용 앱·파일시스템 덤프**다. REA로 형식과 파일 인벤토리부터 확인하고, 홈·내비·미디어·전화의 화면 리소스와 인터페이스를 좁힌다. 5W 이식 검증에는 별도로 5W 홈에서 순정 지도 미리보기와 미디어 상태를 전달하는 코드가 필요한 상태다.
