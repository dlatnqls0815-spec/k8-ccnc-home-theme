# K8 ccNC 홈 테마 — 차 방문 한 번에 돌리기 (PC PowerShell, 묻지 않고 끝까지 진행).
#
#   1) 연결·지금 설치된 테마 버전
#   2) 겹침 확인: Now Playing 오버레이 앱, ccNC 오버레이(서개모) 모듈 — 경고만 하고 끄지 않는다
#   3) 최신 테마 NEMESIS 묶음 설치(adb push → nemesis install --update, 사용자 승인 2026-11-23)
#      이미 같은 버전이고 도우미 소켓이 살아 있으면 건너뛴다(-Force 로 다시 설치)
#   4) 도우미 소켓(k8home.keys) 확인
#   5) 읽기만: 순정 글꼴(이름·위치), 고음/중음/저음 순정 키, 테마 화면 사진 1장
#   6) 손으로 볼 항목 목록을 출력
#   -Watch: 위 다음에 테마 로그(곡 넘기기·팝업·사운드)를 화면과 파일에 계속 남긴다(Ctrl+C 로 끝).
#
# 하지 않는 것: setprop(특히 persist.service.adb.tcp.enable), 설정 값 쓰기, 사운드 쓰기, 모듈 끄기/지우기.
# 결과: private/vehicle/visit-<시각>/ (커밋하지 않는 폴더). 차 주소는 여기에 적지 않는다.
# 주소: -Serial ip:5555 / 환경변수 K8_ADDR / private/k8-addr / 연결된 기기 하나 자동 선택.
param(
  [string]$Serial = '',
  [string]$Version = '',
  [switch]$Force,
  [switch]$Watch
)
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:USERPROFILE '.local\share\android-sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }
$started = Get-Date

if (-not $Serial) { $Serial = $env:K8_ADDR }
$addrFile = Join-Path $root 'private\k8-addr'
if (-not $Serial -and (Test-Path $addrFile)) { $Serial = (Get-Content $addrFile -Raw).Trim() }
if ($Serial -match ':\d+$') { & $adb connect $Serial | Out-Null }
if (-not $Serial) {
  $devs = & $adb devices | Select-String '\tdevice$' | ForEach-Object { ($_ -split "`t")[0] }
  if (@($devs).Count -ne 1) { Write-Output "기기를 하나로 정할 수 없음: $($devs -join ', ')  (-Serial ip:5555 지정)"; exit 2 }
  $Serial = @($devs)[0]
}
if ((& $adb -s $Serial get-state 2>$null) -ne 'device') { Write-Output "연결 실패: $Serial (차 시동·무선 adb 확인)"; exit 3 }

# 설치할 버전: -Version 또는 tools/build_nemesis.py 의 HOME_PACKAGE_VERSION
if (-not $Version) {
  $m = Select-String -Path (Join-Path $PSScriptRoot 'build_nemesis.py') -Pattern 'HOME_PACKAGE_VERSION = "([^"]+)"' | Select-Object -First 1
  if ($m) { $Version = $m.Matches[0].Groups[1].Value }
}
$deliver = Join-Path (Split-Path -Parent (Split-Path -Parent $root)) 'deliverables\검수전'
$usbZip = Join-Path $deliver "K8-ccNC-Home-NEMESIS-$Version-USB.zip"

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$out = Join-Path $root "private\vehicle\visit-$stamp"
New-Item -ItemType Directory -Force -Path $out | Out-Null
function Sh([string]$cmd) { return (& $adb -s $Serial shell $cmd 2>&1 | Out-String) -replace "`r", '' }
function Save([string]$name, [string]$cmd) {
  $text = Sh $cmd
  [IO.File]::WriteAllText((Join-Path $out $name), $text, [Text.UTF8Encoding]::new($false))
  return $text
}
function Say([string]$t) { Write-Output $t; Add-Content -Path (Join-Path $out '00-요약.txt') -Value $t -Encoding UTF8 }
function Installed() {
  $v = (Sh 'dumpsys package io.github.k8home') -split "`n" | Select-String 'versionName=' | Select-Object -First 1
  if ($v) { return ($v.ToString() -replace '.*versionName=', '').Trim() } return ''
}
function SocketUp() { return [bool]((Sh 'cat /proc/net/unix') -match 'k8home\.keys') }

Say "차 방문 시작 $stamp  설치할 버전 $Version"

# 1) 지금 상태
$before = Installed
Say ("1) 지금 테마: " + ($(if ($before) { $before } else { '설치 안 됨' })) + " · 도우미 소켓: " + ($(if (SocketUp) { '있음' } else { '없음' })))

# 2) 겹침 확인(경고만)
$pk = Save '20-packages.txt' 'pm list packages'
$np = ($pk -split "`n") | Where-Object { $_ -match 'nowplaying|k8np' }
$mods = Save '21-nemesis-apps.txt' 'ls /data/nemesis/apps 2>&1; ls /data/nemesis 2>&1'
Say ("2) Now Playing 앱: " + ($(if ($np) { ($np -join ', ').Trim() + '  ← 켜져 있으면 하단바가 겹칠 수 있음' } else { '없음' })))
$seo = ($mods -split "`n") | Where-Object { $_ -match 'ccnc|seogaemo|overlay|k8np' }
Say ("   NEMESIS 모듈(오버레이 후보): " + ($(if ($seo) { ($seo -join ', ').Trim() + '  ← 같은 기능이면 겹침 확인' } else { '안 보임(읽기 권한이 없을 수도 있음)' })))

# 3) 설치
if (-not $Force -and $before -eq $Version -and (SocketUp)) {
  Say "3) 이미 $Version 이고 도우미도 살아 있어 설치를 건너뜀(-Force 로 다시)"
} elseif (-not (Test-Path $usbZip)) {
  Say "3) 설치 묶음이 없음: $usbZip  → 설치 건너뜀"
} else {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $inner = "k8home.install-$Version.zip"
  $tmp = Join-Path $env:TEMP $inner
  $zip = [IO.Compression.ZipFile]::OpenRead($usbZip)
  try {
    $entry = $zip.Entries | Where-Object { $_.FullName -eq "packages/$inner" } | Select-Object -First 1
    if ($entry) { [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $tmp, $true) }
  } finally { $zip.Dispose() }
  if (-not (Test-Path $tmp)) { Say "3) 묶음 안에 packages/$inner 가 없음 → 설치 건너뜀" }
  else {
    & $adb -s $Serial push $tmp "/data/local/tmp/$inner" 2>&1 | Out-Null
    $res = Save '30-install.txt' "nemesis install --update /data/local/tmp/$inner; echo exit=`$?; rm /data/local/tmp/$inner"
    Remove-Item $tmp -Force
    Start-Sleep -Seconds 3
    $after = Installed
    Say ("3) 설치: " + ($(if ($after -eq $Version) { "성공 → $after" } else { "확인 필요(지금 $after) · 30-install.txt 참고" })))
  }
}

# 4) 도우미 소켓
$up = $false
for ($i = 0; $i -lt 10 -and -not $up; $i++) { if (SocketUp) { $up = $true } else { Start-Sleep -Seconds 2 } }
Say ("4) 도우미 소켓 k8home.keys: " + ($(if ($up) { '있음(곡 넘기기 준비됨)' } else { '없음 → 재시동 후 다시 확인, 30-install.txt·logcat 확인' })))

# 5) 읽기만
Save '50-fonts-dir.txt' 'ls -l /system/fonts' | Out-Null
Save '51-fonts-xml.txt' 'cat /system/etc/system_fonts.xml; echo ===; cat /system/etc/fallback_fonts.xml; echo ===; cat /system/etc/fonts.xml 2>/dev/null' | Out-Null
$ff = Save '52-fonts-elsewhere.txt' 'ls -lR /system/app /system/priv-app /system/vendor 2>/dev/null | grep -iE "\.(ttf|otf|ttc)$"'
$fd = Sh 'ls /system/fonts'
$kor = ($fd -split "`n") | Where-Object { $_ -match '(?i)kr|ko|hyundai|kia|hmg|nanum|sandoll|yoon|cjk|harmony|signature' }
Say ("5) 글꼴: /system/fonts 에 한글 후보 " + ($(if ($kor) { ($kor -join ', ').Trim() } else { '없음' })) + " · 앱 안 글꼴 파일 " + (@(($ff -split "`n") | Where-Object { $_.Trim() }).Count) + "개(52-fonts-elsewhere.txt)")
$tone = Save '53-sound-keys.txt' 'for k in treble middle bass; do for u in _USER_1 _USER_2 _GUEST; do for ns in system global secure; do v=$(settings get $ns hkmc_sndset_$k$u 2>/dev/null); [ "$v" != "null" ] && [ -n "$v" ] && echo "$ns hkmc_sndset_$k$u=$v"; done; done; done'
Say ("   고음/중음/저음 순정 키: " + (($tone -split "`n" | Where-Object { $_.Trim() }) -join ' · '))
& $adb -s $Serial shell 'am start -W -n io.github.k8home/.HomeActivity' | Out-Null
Start-Sleep -Seconds 3
& $adb -s $Serial shell 'screencap -p /sdcard/k8visit.png' | Out-Null
& $adb -s $Serial pull /sdcard/k8visit.png (Join-Path $out '55-theme-home.png') 2>&1 | Out-Null
& $adb -s $Serial shell 'rm /sdcard/k8visit.png' | Out-Null

$sec = [int]((Get-Date) - $started).TotalSeconds
Say "자동 단계 끝($sec 초). 결과: $out"
Say ''
Say '손으로 볼 것(docs/설정-차시험.md 2-8):'
@(
  ' A. 하단바 미디어 칸 스와이프 → 블루투스 곡 넘어감',
  ' B. 테마 닫고 순정 내비만 띄운 채 핸들로 곡 넘김 → 순정 곡 팝업 바로 닫힘',
  ' C. 길안내 목록 버튼 위치·동작 / 하단바 ‹ (순정 내비 하위 화면 뒤로) / 진행선',
  ' D. 길안내 중 시간 칸 5초 교대·"1시간 25분" 겹침',
  ' E. 긴 곡 제목 굵기 / 라디오 타일·FM/AM → 순정 라디오',
  ' F. 설정 전체: 5W 자리·스위치 모양·글자 꺾임, 하단바 가운데 경계 없음·세로선 맞춤·가운데 칸 아래까지',
  ' G. 사운드: 고음/중음/저음 막대 = 순정 값, 아래 줄 앰프 값·단위 적기',
  ' H. (그때 다시 승인받고) 사운드 직접 변경 쓰기 시험'
) | ForEach-Object { Say $_ }

if ($Watch) {
  $log = Join-Path $out '60-logcat.txt'
  Say ''
  Say "로그 보는 중(Ctrl+C 로 끝): $log"
  & $adb -s $Serial logcat -v time -s K8HomeKeys:* K8HomePopup:* K8Sound:* K8Settings:* K8HomeNavBar:* K8HomeBoot:* AndroidRuntime:E 2>&1 |
    ForEach-Object { $_; Add-Content -Path $log -Value $_ -Encoding UTF8 }
}
