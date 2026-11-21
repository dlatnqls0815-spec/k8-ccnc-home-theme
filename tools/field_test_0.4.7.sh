#!/usr/bin/env bash
# K8 5W 홈 테마 0.4.7 현장 시험 묶음 (Git Bash, PC에서 실행).
#   status     연결·정차·설치 상태·HOME 등록·부팅 수신기·오버레이 권한 확인
#   install    APK 설치(덮어쓰기). 서명이 0.4.6과 같아 데이터는 유지된다.
#   home       HOME 인텐트가 어떤 앱으로 잡히는지 확인
#   boot       부팅 방송을 흉내내어 시동 자동 실행을 확인(진짜 시동은 차량에서 직접)
#   navbar     순정 내비를 띄우고 하단바 오버레이 유무·스크린샷 확인
#   shot <이름> 화면 캡처를 private/logs/ 아래에 저장
#   restore    자동 실행·하단바 스위치를 끄고 로그를 정리
# 주소: K8_ADDR 환경변수 또는 private/k8-addr (한 줄, ip:5555). 저장소에 주소를 쓰지 않는다.
set -u
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.." || exit 1
ADB=${ADB:-$HOME/.local/share/android-sdk/platform-tools/adb.exe}
APK=${APK:-"$HOME/../../../../../네메시스/deliverables/K8-5W-홈-0.4.7.apk"}
ADDR=${K8_ADDR:-$(tr -d '\r\n ' < private/k8-addr 2>/dev/null)}
PKG=io.github.k8home
A="$ADB -s $ADDR"

sh_() { $A shell "$@" </dev/null | tr -d '\r'; }
log() { mkdir -p private/logs; echo "[$(date +%H:%M:%S)] $*" | tee -a private/logs/field-0.4.7.log; }

connect() {
  [ -n "$ADDR" ] || { echo "주소 없음: K8_ADDR 또는 private/k8-addr"; exit 2; }
  [ "$($A get-state 2>/dev/null)" = device ] && return 0
  timeout 15 $ADB connect "$ADDR" >/dev/null 2>&1
  [ "$($A get-state 2>/dev/null)" = device ] || { echo "연결 실패: $ADDR"; exit 3; }
}

speed() { $A logcat -d </dev/null | grep -a "getSpeed_KMHUnit() =" | tail -1 | sed -n 's/.*getSpeed_KMHUnit() = *\([0-9.]*\).*/\1/p'; }

require_parked() {
  local s; s=$(speed)
  case "$s" in
    0|0.0) echo "정차 확인 (속도 0)";;
    "") echo "속도 로그 없음 → 정차를 확인할 수 없어 중단"; exit 4;;
    *) echo "주행 중 (속도 $s) → 중단"; exit 4;;
  esac
}

version() { sh_ dumpsys package $PKG | grep -a versionName | head -1; }

status() {
  connect
  echo "속도: $(speed)   (0 이어야 화면 조작 시험 가능)"
  echo "설치: $(version || echo '설치 안 됨')"
  echo "--- HOME 인텐트를 받는 앱 ---"
  sh_ pm query-activities -a android.intent.action.MAIN -c android.intent.category.HOME | grep -aE "packageName|name=" | head -12
  echo "--- 부팅 수신기 ---"
  sh_ dumpsys package $PKG | grep -aA3 "BootReceiver" | head -8
  echo "--- 실행 중 프로세스 ---"
  sh_ ps | grep -a "k8home" | head -5
  echo "--- 오버레이 창 ---"
  sh_ dumpsys window windows | grep -a "K8HomeNavBar" | head -3
  echo "--- 부팅 설정 값 ---"
  sh_ "run-as $PKG cat /data/data/$PKG/shared_prefs/theme.xml" 2>/dev/null | head -20
}

install() {
  connect
  require_parked
  [ -f "$APK" ] || { echo "APK 없음: $APK"; exit 2; }
  log "설치 시작: $APK"
  $A install -r "$APK" 2>&1 | tail -3 | tee -a private/logs/field-0.4.7.log
  log "설치 후 버전: $(version)"
}

home() {
  connect
  echo "--- HOME 카테고리 처리 앱 ---"
  sh_ pm query-activities -a android.intent.action.MAIN -c android.intent.category.HOME | grep -aE "packageName|name=" | head -12
  echo "--- 직접 HOME 인텐트 보내기(테마가 열리는지) ---"
  $A shell am start -a android.intent.action.MAIN -c android.intent.category.HOME </dev/null 2>&1 | head -5
  sleep 2
  sh_ dumpsys window windows | grep -aE "mCurrentFocus|mFocusedApp" | head -3
}

boot() {
  connect
  require_parked
  log "부팅 방송 흉내내기 (진짜 시동과 같은 경로)"
  $A shell am broadcast -a android.intent.action.BOOT_COMPLETED -n $PKG/.BootReceiver </dev/null 2>&1 | head -3
  echo "22초 뒤에 테마가 열려야 한다. 그동안 화면을 보지 않는다."
  sleep 26
  sh_ dumpsys window windows | grep -aE "mCurrentFocus|mFocusedApp" | head -3
  log "부팅 확인: $(sh_ dumpsys window windows | grep -a mCurrentFocus | head -1)"
}

navbar() {
  connect
  require_parked
  log "순정 내비 실행 → 하단바 확인"
  $A shell am start -n com.mnsoft.navi/com.mnsoft.navi.NaviApp </dev/null 2>&1 | head -3
  sleep 4
  echo "--- 맨 위 창 ---"
  sh_ dumpsys window windows | grep -aE "mCurrentFocus|mFocusedApp" | head -3
  echo "--- 하단바 오버레이 ---"
  sh_ dumpsys window windows | grep -a "K8HomeNavBar" | head -3
  echo "--- 서비스 로그 ---"
  $A logcat -d -s K8HomeNavBar K8HomeBoot </dev/null | tail -20
  mkdir -p private/logs
  $A exec-out screencap -p > private/logs/navbar-$(date +%Y%m%d-%H%M%S).png 2>/dev/null
  echo "스크린샷: private/logs/navbar-*.png"
}

shot() {
  connect
  local name=${1:-shot}
  mkdir -p private/logs
  $A exec-out screencap -p > "private/logs/${name}-$(date +%Y%m%d-%H%M%S).png" 2>/dev/null
  echo "저장: private/logs/${name}-*.png"
}

restore() {
  connect
  log "스위치 끄기: 자동 실행·하단바"
  sh_ am force-stop $PKG
  echo "앱 안의 설정 > 일반에서 '시동 후 자동 실행'과 '순정 내비 위 하단바'를 끈다."
  echo "완전 제거가 필요하면: adb -s $ADDR uninstall $PKG"
}

case "${1:-status}" in
  status) status;;
  install) install;;
  home) home;;
  boot) boot;;
  navbar) navbar;;
  shot) shift; shot "$@";;
  restore) restore;;
  *) echo "사용법: $0 {status|install|home|boot|navbar|shot <이름>|restore}"; exit 2;;
esac
