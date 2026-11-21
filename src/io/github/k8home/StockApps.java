package io.github.k8home;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/** Launch targets verified on the Korean K8 Gen5W head unit. */
final class StockApps {
    static final ComponentName MEDIA = component("com.hkmc.av.app.mediagate/.MediaGateActivity");
    static final ComponentName PHONE = component("com.hkmc.bluetooth/com.android.contacts.DialtactsActivity");
    static final ComponentName RADIO = component("com.hkmc.av.app.radioge/.RadioMainActivity");
    static final ComponentName BLUETOOTH = component("com.daudio.av.app.btaudio.ui/.BtMusicMainActivity");
    static final ComponentName USB = component("com.daudio.av.app.usbmusic/.ui.MusicActivity");
    static final ComponentName NATURE = component("com.hkmc.av.app.naturesound/.ui.NatureSoundActivity");
    static final ComponentName PROJECTION = component("com.daudio.app.phoneprojection/.settings.ui.DeviceConnectionSettings");
    static final ComponentName SETTINGS = component("com.android.settings/.Settings");
    static final ComponentName VEHICLE = component("com.android.vehiclesettings/.VehicleSetting");
    static final ComponentName PROFILE = component("com.hkmc.system.app.userprofile/.UserProfileMainActivity");
    private static final ComponentName RADIO_SERVICE = component("com.hkmc.av.app.radioge/.services.RadioAppService");

    private StockApps() {}

    static boolean open(Context context, ComponentName component) {
        return open(context, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static boolean open(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    static boolean openPlayer(Context context, String source) {
        ComponentName component;
        switch (source) {
            case "bt": component = BLUETOOTH; break;
            case "usb": component = usbMounted() ? USB : MEDIA; break;
            case "radio":
            case "fm":
            case "am": component = RADIO; break;
            case "nature": component = NATURE; break;
            default: component = MEDIA; break;
        }
        return open(context, component) || (!MEDIA.equals(component) && open(context, MEDIA));
    }

    static boolean openRadioBand(Context context, boolean fm) {
        String suffix = fm ? "FM1_SHORTCUT" : "AM_SHORTCUT";
        try {
            context.startService(new Intent("com.hkmc.intent.action." + suffix).setComponent(RADIO_SERVICE));
        } catch (RuntimeException ignored) { }
        return open(context, RADIO);
    }

    private static boolean usbMounted() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(" ", 3);
                if (fields.length >= 2 && fields[1].matches("/(?:storage/usb|mnt/media_rw/usb)[0-2]")) return true;
            }
        } catch (IOException ignored) { }
        return false;
    }

    private static ComponentName component(String flattened) {
        return ComponentName.unflattenFromString(flattened);
    }
}
