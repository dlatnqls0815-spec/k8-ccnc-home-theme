# K8 5W 실차 수집 (PC PowerShell에서 실행, 묻지 않고 끝까지 진행).
#   1) 테마 설치 확인(버전, HOME 등록)
#   2) 로그·상태·화면·순정 앱/프레임워크 파일을 private/vehicle/capture-<시각>/ 로 모음
# 차량에는 읽기만 한다. 예외: 화면 캡처를 위해 테마와 순정 내비를 열고(/sdcard 임시 PNG는 지움).
# setprop, 설정 변경, 방송 송신, 모듈 조작은 하지 않는다.
# 주소: -Serial ip:5555 / 환경변수 K8_ADDR / private/k8-addr / 연결된 기기 하나 자동 선택.
param(
  [string]$Serial = '',
  [switch]$SkipPull,
  [int]$LiveSeconds = 60
)
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:USERPROFILE '.local\share\android-sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }

if (-not $Serial) { $Serial = $env:K8_ADDR }
$addrFile = Join-Path $root 'private\k8-addr'
if (-not $Serial -and (Test-Path $addrFile)) { $Serial = (Get-Content $addrFile -Raw).Trim() }
if ($Serial -match ':\d+$') { & $adb connect $Serial | Out-Null }
if (-not $Serial) {
  $devs = & $adb devices | Select-String '\tdevice$' | ForEach-Object { ($_ -split "`t")[0] }
  if (@($devs).Count -ne 1) { Write-Output "기기를 하나로 정할 수 없음: $($devs -join ', ')  (-Serial ip:5555 지정)"; exit 2 }
  $Serial = @($devs)[0]
}
if ((& $adb -s $Serial get-state 2>$null) -ne 'device') { Write-Output "연결 실패: $Serial"; exit 3 }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$out = Join-Path $root "private\vehicle\capture-$stamp"
New-Item -ItemType Directory -Force -Path $out | Out-Null
function Save([string]$name, [string]$cmd) {
  $text = & $adb -s $Serial shell $cmd 2>&1 | Out-String
  [IO.File]::WriteAllText((Join-Path $out $name), ($text -replace "`r", ''), [Text.UTF8Encoding]::new($false))
  return $text
}
function Say([string]$t) { Write-Output $t; Add-Content -Path (Join-Path $out '00-요약.txt') -Value $t -Encoding UTF8 }

Say "수집 시작 $stamp  기기 $Serial"

# 1) 설치 확인
$pkg = Save '10-theme-package.txt' 'dumpsys package io.github.k8home'
$ver = ($pkg -split "`n" | Select-String 'versionName=' | Select-Object -First 1)
if ($ver) { Say ("테마 설치됨: " + $ver.ToString().Trim()) } else { Say '테마 설치 안 됨 (USB로 네메시스 설치 먼저)' }
$homeInfo = Save '11-home-resolve.txt' 'pm query-activities -a android.intent.action.MAIN -c android.intent.category.HOME'
Save '12-nemesis-packages.txt' 'pm list packages -f; ls -l /data/nemesis 2>/dev/null' | Out-Null

# 2) 기기·시스템 상태
Save '20-getprop.txt' 'getprop' | Out-Null
Save '21-system.txt' 'uname -a; uptime; df; cat /proc/meminfo' | Out-Null
Save '22-services.txt' 'service list' | Out-Null
Save '23-app_process.txt' 'ls -l /system/bin/app_process*; md5 /system/bin/app_process 2>/dev/null || md5sum /system/bin/app_process' | Out-Null
Save '24-activity-top.txt' 'dumpsys activity activities' | Out-Null
Save '25-windows.txt' 'dumpsys window windows' | Out-Null
Save '26-media.txt' 'dumpsys media_session; dumpsys audio' | Out-Null
Save '27-theme-mem.txt' 'dumpsys meminfo io.github.k8home' | Out-Null
Save '28-settings.txt' 'settings list system; echo ---; settings list global; echo ---; settings list secure' | Out-Null

# 3) 테마 화면 캡처 (홈, 전체 메뉴, 미디어)
$shots = @(
  @('30-theme-home', 'am start -W -n io.github.k8home/.HomeActivity'),
  @('31-theme-apps', 'am start -W -n io.github.k8home/.HomeActivity --ez io.github.k8home.extra.OPEN_APPS true'),
  @('32-theme-media', 'am start -W -n io.github.k8home/.HomeActivity --ez io.github.k8home.extra.OPEN_MEDIA true'),
  # 순정 내비 지도 화면: 왼쪽 버튼 줄 덮개(naviskin.html) 위치를 실제 픽셀로 맞추는 기준
  @('33-stock-navi', 'am start -W -n com.mnsoft.navi/.NaviApp')
)
foreach ($s in $shots) {
  & $adb -s $Serial shell $s[1] | Out-Null
  Start-Sleep -Seconds 4
  & $adb -s $Serial shell 'screencap -p /sdcard/k8cap.png' | Out-Null
  & $adb -s $Serial pull /sdcard/k8cap.png (Join-Path $out ($s[0] + '.png')) 2>&1 | Out-Null
}
& $adb -s $Serial shell 'rm /sdcard/k8cap.png' | Out-Null
Say '화면 캡처 4장 (테마 3, 순정 내비 1)'

# 4) 로그: 테마를 연 뒤 잠시 모은 다음 전체 로그 덤프
if ($LiveSeconds -gt 0) { Say "로그 $LiveSeconds 초 대기 (이 동안 미디어·내비·전화를 써 보면 값이 더 들어옴)"; Start-Sleep -Seconds $LiveSeconds }
Save '40-logcat-all.txt' 'logcat -d -v threadtime -b main -b system -b events' | Out-Null
$tags = 'K8StockRoute|K8StockWeather|K8StockHybrid|K8StockVehicle|K8HomeMap|K8HomeCorners|K8StockNavigation|K8StockClimate|K8StockPhone|AndroidRuntime|io.github.k8home'
$all = Get-Content (Join-Path $out '40-logcat-all.txt') -Encoding UTF8
$mine = $all | Select-String -Pattern $tags
$mine | ForEach-Object { $_.Line } | Set-Content (Join-Path $out '41-logcat-theme.txt') -Encoding UTF8
Say ("테마 로그 줄 수: " + @($mine).Count)
foreach ($t in 'K8StockVehicle','K8StockHybrid','K8StockWeather','K8StockRoute','K8HomeMap','K8HomeCorners') {
  $n = @($mine | Select-String $t).Count
  $last = $mine | Select-String $t | Select-Object -Last 1
  Say ("  {0,-15} {1,4}줄  {2}" -f $t, $n, $(if ($last) { $last.Line.Substring([Math]::Max(0, $last.Line.Length - 140)) } else { '' }))
}
$crash = @($all | Select-String 'FATAL EXCEPTION' -Context 0,12 | Where-Object { $_.Context.PostContext -match 'k8home' })
Say ("테마 강제 종료 기록: " + $crash.Count)

# 5) 순정 앱·프레임워크 목록과 파일
Save '50-packages.txt' 'pm list packages -f -i' | Out-Null
Save '51-system-files.txt' 'ls -lR /system/app /system/priv-app /system/framework' | Out-Null
if (-not $SkipPull) {
  $fw = Join-Path $out 'framework'
  & $adb -s $Serial pull /system/framework $fw 2>&1 | Select-Object -Last 1 | ForEach-Object { Say ("framework: " + $_) }
  $want = @(
    'com.android.launcher3','com.hkmc.daudio.app.hev2','com.daudio.app.dvrs','com.android.rear_care',
    'com.daudio.app.findmyvehicle','com.daudio.app.musicstreaming','com.android.themesettings',
    'com.android.vehiclesettings','com.android.displaysettings','com.android.generalsettings',
    'com.android.soundsettings','com.android.btsettings','com.android.buttonsettings','com.android.settings',
    'com.android.settingscommon','com.daudio.app.phoneprojection','com.daudio.av.app.usbvideo',
    'com.hkmc.telematics.apps','com.hkmc.telematics.service.apps.climate','com.mobis.welcome',
    'com.android.incallui','com.android.keyguard','com.daudio.av.app.dmb'
  )
  $apps = Join-Path $out 'apps'
  New-Item -ItemType Directory -Force -Path $apps | Out-Null
  foreach ($p in $want) {
    $line = & $adb -s $Serial shell "pm path $p" 2>&1 | Where-Object { $_ -match '^package:' } | Select-Object -First 1
    if (-not $line) { Say "  없음 $p"; continue }
    $remote = ($line -replace '^package:', '').Trim()
    $dir = $remote.Substring(0, $remote.LastIndexOf('/'))
    $base = [IO.Path]::GetFileNameWithoutExtension($remote)
    $dest = Join-Path $apps $p
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    & $adb -s $Serial pull $remote $dest 2>&1 | Out-Null
    & $adb -s $Serial pull "$dir/$base.odex" $dest 2>&1 | Out-Null
    $size = (Get-ChildItem $dest -File | Measure-Object Length -Sum).Sum
    Say ("  받음 {0,-42} {1,12:N0} bytes" -f $p, $size)
  }
}

Say "끝: $out"
