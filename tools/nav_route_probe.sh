#!/usr/bin/env bash
# Reads the raw navigation broadcast the theme logged on the car and prints it in a readable way.
# Run this after opening the map from the theme with a route set.
#   ADB=... bash tools/nav_route_probe.sh
# Address: K8_ADDR environment variable or private/k8-addr (one line, ip:5555).
set -u
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.." || exit 1
ADB=${ADB:-$HOME/.local/share/android-sdk/platform-tools/adb.exe}
ADDR=${K8_ADDR:-$(tr -d '\r\n ' < private/k8-addr 2>/dev/null)}
A="$ADB -s $ADDR"

if [ -z "$ADDR" ]; then echo "주소 없음: K8_ADDR 또는 private/k8-addr"; exit 2; fi
[ "$($A get-state 2>/dev/null)" = device ] || { $ADB connect "$ADDR" >/dev/null 2>&1; }

log=/data/nemesis/logs/k8home.nav
out=private/logs/nav-route-probe-$(date +%Y%m%d-%H%M%S).txt
mkdir -p private/logs

echo "=== 1) 앱 로그에서 길안내 방송 수집 (30초) ==="
timeout 30 $A logcat -d -v time -s K8HomeNavBar 2>/dev/null | grep -a "nav extras" | tee -a "$out"
echo

echo "=== 2) 시스템 로그에서 내비 방송 직접 확인 ==="
timeout 20 $A logcat -d -v time 2>/dev/null \
  | grep -aE "Route_Guide_Status|DestinationInfo|DestDistance|DestRemainTime|ROUTE_GUIDE_STATE" \
  | tail -40 | tee -a "$out"
echo

echo "=== 3) 내비 프로세스가 살아 있는가 ==="
$A shell "ps | grep mnsoft" 2>/dev/null | tee -a "$out"
echo

echo "저장: $out"
echo "해석: DestDistance/DestRemainTime 뒤의 괄호가 값의 타입이다."
echo "  (Integer) = 미터/초 같은 숫자, (String) = '285 km' 처럼 이미 서식이 있는 값."
