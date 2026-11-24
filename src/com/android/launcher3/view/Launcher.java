package com.android.launcher3.view;

/**
 * The theme's screen under the stock home's class name. The stock radio and nature sound apps skip their right-side
 * popup when the front activity's class is com.android.launcher3.view.Launcher (the home widget already shows the
 * station; radio "sendOSDInfo ... Launcher screen", 2026-11-24 car analysis). AndroidManifest keeps .HomeActivity as an
 * alias of this activity so every existing launch path (NEMESIS, services, adb) still works.
 */
public final class Launcher extends io.github.k8home.HomeActivity {
}
