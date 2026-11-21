# K8 5W 네메시스 패키지

## ccNC 홈 테마

- `K8-ccNC-Home-NEMESIS-0.4.20-USB.zip`에 독립형 ccNC 홈 APK 0.4.20이 들어 있다. 서개모나 HMG-API 의존성이 없다.
- ZIP을 풀어 `NEMESIS.data`와 `packages`를 USB 최상위에 둔다. 정차 상태에서 **Package Store → USB → K8 ccNC Home Theme**를 설치하고 **My Packages → K8 ccNC Home Theme → Launch**를 누른다. [NEMESIS 패키지 문서](https://github.com/xwtk/hmg_nemesis/blob/main/05-Packages.md#3-applications--appn)
- 0.4.14 패키지로 K8 5W 실차 설치·Launch와 블루투스 곡 정보 표시를 확인했다. 0.4.17의 실차 설치와 USB 설치 경로는 확인 전이다.
- 홈의 지도 카드는 순정 내비를 연다. 내비 위 ccNC 하단바는 **설정 → 일반 → 순정 내비에서 ccNC 하단바 시험**을 켜고 테마에서 지도를 열면 시작한다. 내비 위 하단바 터치 및 순정 미디어 제어는 실차 확인 전이다.
- 패키지를 다시 만들려면 서명한 K8 Home APK를 `python tools/build_nemesis.py --home-apk "<APK 경로>" --out "<출력 폴더>"`에 넘긴다. APK를 재빌드하거나 재서명하지 않는다.

## 순정 바로가기 0.4.13

K8 앰비언트의 `kind=sh` 등록 방식을 사용한다. 네메시스 Manager가 순정 앱을 여는 명령만 실행한다. 각 바로가기는 순정 화면 하나를 연다.

## 차량에서 사용

- 매니저 왼쪽 **My Packages**에서 **K8 Navigation**을 선택하고, 상세 화면 하단의 **Launch**를 누르면 순정 지도가 열린다.
- **K8 Media**, **K8 Bluetooth Phone**, **K8 Radio**, **K8 Bluetooth Audio**, **K8 USB Music**, **K8 Sounds of Nature**, **K8 Phone Projection**, **K8 Settings**, **K8 Vehicle Settings**, **K8 Driver Profile**도 각각 같은 방식으로 연다.
- USB로 설치할 때는 ZIP을 풀고 `NEMESIS.data`와 `packages`를 USB 최상위에 둔다. 기존 `NEMESIS.data`가 있으면 백업한다. **Package Store → USB**에서 필요한 항목을 선택해 설치한다. [NEMESIS 공식 패키지 문서](https://github.com/xwtk/hmg_nemesis/blob/main/05-Packages.md)
- 제거는 해당 항목의 **Uninstall**을 사용한다.
- USB 저장 장치가 연결되지 않으면 **K8 USB Music → Launch**가 순정 미디어 선택 화면을 연다.

## 빌드

```powershell
python tools/build_nemesis.py --out "<출력 폴더>"
```
