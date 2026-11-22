package io.github.k8home;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    private static final String OPEN_HOME = "io.github.k8home.action.OPEN_HOME_AFTER_BOOT";
    private static final long BOOT_DELAY_MS = 22000;

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        CornerService.sync(context);
        PopupGuardService.start(context);
        if (!ThemePrefs.autoStart(context)) return;
        String action = intent.getAction();
        if (OPEN_HOME.equals(action)) {
            if (!launcherInFront(context)) return;
            try {
                context.startActivity(new Intent(context, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException failure) {
                Log.w("K8HomeBoot", "Could not open theme home", failure);
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarm == null) return;
            Intent launch = new Intent(context, BootReceiver.class).setAction(OPEN_HOME);
            PendingIntent request = PendingIntent.getBroadcast(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT);
            alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + BOOT_DELAY_MS, request);
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean launcherInFront(Context context) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (power == null || !power.isScreenOn() || keyguard == null || keyguard.inKeyguardRestrictedInputMode()) return false;
        ComponentName top = StockNavigation.foreground(context);
        if (top == null || context.getPackageName().equals(top.getPackageName())) return false;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = context.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && resolved.activityInfo != null
            && top.equals(new ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name));
    }
}
