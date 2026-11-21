# 컴파일용 껍데기

차량 펌웨어의 `automotive` 공용 라이브러리(`/system/framework/automotive.jar`) 클래스 중 테마가 상속하거나 부르는 것만
같은 이름·같은 시그니처로 비워 둔 것이다. `build.py`가 컴파일할 때만 쓰고 APK에는 넣지 않는다.
실행 때는 매니페스트의 `<uses-library android:name="automotive" android:required="false"/>`로 차의 진짜 클래스가 쓰인다.
시그니처 근거: 차에서 뽑은 `automotive.odex`의 `com/hkmc/datc/*`.
