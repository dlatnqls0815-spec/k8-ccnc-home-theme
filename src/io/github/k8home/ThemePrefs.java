package io.github.k8home;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Switches shared by the theme activity, the overlay bar and the boot receiver.
 * The file is separate from the shortcut table because these values are not app links.
 */
final class ThemePrefs {
    static final String FILE = "theme";
    static final String AUTO_START = "auto-start";
    private static final String NAV_BAR = "stock-nav-bar-preview";
    private static final String CORNERS = "stock-corner-buttons";

    private ThemePrefs() { }

    static SharedPreferences of(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Start the theme by itself after the head unit finished booting. */
    static boolean autoStart(Context context) {
        return of(context).getBoolean(AUTO_START, false);
    }

    static void setAutoStart(Context context, boolean value) {
        of(context).edit().putBoolean(AUTO_START, value).apply();
    }

    /** Draw the theme bottom bar over the stock navigation app. */
    static boolean navBar(Context context) {
        return of(context).getBoolean(NAV_BAR, false);
    }

    static void setNavBar(Context context, boolean value) {
        of(context).edit().putBoolean(NAV_BAR, value).apply();
    }

    /** Float the ccNC back/home/menu keys and the now-playing box over stock screens. */
    static boolean corners(Context context) {
        return of(context).getBoolean(CORNERS, false);
    }

    static void setCorners(Context context, boolean value) {
        of(context).edit().putBoolean(CORNERS, value).commit();
    }

}
