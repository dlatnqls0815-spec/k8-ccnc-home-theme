package com.hkmc.mode;

/** 컴파일용 스텁(APK 미포함). 시그니처 근거: 차량 automotive.dex ModeKeyListener. */
public class ModeKeyListener {
    public ModeKeyListener() { throw new RuntimeException("stub"); }
    public void onAllKeyForService(int action, int flags, int deviceId, int keyCode,
            int scanCode, int metaState, int repeatCount, boolean isViewHandled) { }
    public void onKey(int action, int flags, int deviceId, int keyCode,
            int scanCode, int metaState, int repeatCount) { }
    public void onKeyForService(int action, int flags, int deviceId, int keyCode,
            int scanCode, int metaState, int repeatCount, boolean isViewHandled) { }
    public void onKeyLock(boolean lock) { }
    public void onKeyStuck(String key, boolean stuck) { }
    public void onKeySwap(boolean swap) { }
    public void onRSELock(int code) { }
}
