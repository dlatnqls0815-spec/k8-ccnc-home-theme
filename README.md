# K8 ccNC 홈 테마 (k8-ccnc-home-theme)

기아 K8 (Gen5W, 1920×720, Android 4.4 / API 19) 환경에서 **ccNC 순정 홈 테마의 디자인과 기능을 최대한 동일하게 구현**하는 것을 목표로 하는 프로젝트입니다.

## 현재까지 개발된 내용

### 1. K8 홈 Android 앱 (`0.1.0`)
- **패키지**: `io.github.k8home` (`AndroidManifest.xml`, `src/io/github/k8home/HomeActivity.java`)
- **UI**: `assets/index.html` — 1920×720 가로 화면 기준 3분할 홈 카드(내비게이션 · 미디어 · 전화) 및 좌우 스와이프 전체 앱 화면 전환
- **네이티브 브릿지**:
  - `HomeActivity.setMapContent(View)` 기반 지도 슬롯(`#map-slot`) 연동 인터페이스
  - 순정 내비게이션(`com.mnsoft.navi/.NaviApp`) 실행 및 미디어/전화/라디오/폰 프로젝션/설정 바로가기 연결
- **빌드 스크립트**: `build.py` (Gradle 없이 로컬 Android SDK build-tools `35.0.1` + JDK로 API 19 호환 APK 빌드 및 v1 서명)

### 2. ccNC 스타일 웹 인터랙티브 데모 (`demo/`)
- `demo/index.html` — 하단바(뒤로 · 홈 · 전체 앱 · 내비 · 미디어) 및 오른쪽 분할 위젯 전환, 홈↔전체 앱 드래그/스와이프, 지도(우리 집/회사) 및 미디어 재생 상태 연동 데모
- `demo/home.png`, `demo/navigation.png` — 화면 캡처 참고 이미지

### 3. 리서치 및 인수인계 문서 (`docs/`)
- `docs/AI_HANDOFF.md` — K8 5W 홈 `0.1.0` 및 하단 메뉴 예시안 개발 현황·검증 내역
- `docs/ccnc-platform-research.md` — 현대·기아 ccNC AVN 공통 플랫폼 조사 및 기능 매핑
- `docs/ccnc-r10-components.csv` — ccNC R10 오픈소스 고지 구성요소 목록

## 빌드 방법 (Android APK)

```bash
python build.py --sdk "<Android_SDK_경로>" --jdk "<JDK_경로>" --output "<출력_APK_경로>"
```

> 참고: 빌드 산출물(`.apk`)과 서명 키(`.build/`)는 `.gitignore`에 의해 저장소에 포함되지 않습니다.

## PC 웹 데모 확인

`demo/index.html` 또는 `assets/index.html`을 브라우저에서 직접 열어 1920×720 및 반응형 화면 동작을 확인할 수 있습니다.

## 향후 목표

- ccNC 순정 홈/하단바/퀵 컨트롤/분할 위젯의 시각 디자인(색상, 타이포그래피, 아이콘, 여백, 애니메이션) 정밀 일치화
- 실시간 미디어 메타데이터·재생 제어, 전화 상태, 지도 뷰 연동 등 핵심 기능 구현
- 실차(K8 Gen5W) 환경 APK 검증 및 최적화
