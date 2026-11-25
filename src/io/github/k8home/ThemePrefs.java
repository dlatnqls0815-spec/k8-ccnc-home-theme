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
    private static final String NAVI_SKIN = "stock-navi-ccnc-buttons";
    private static final String STOCK_WRITE = "stock-settings-write";
    private static final String SOUND_WRITE = "stock-sound-write";
    private static final String CUSTOM_KEY_HOME = "custom-key-home";

    private ThemePrefs() { }

    static SharedPreferences of(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Start the theme by itself after the head unit finished booting. */
    static boolean autoStart(Context context) {
        return of(context).getBoolean(AUTO_START, true);
    }

    static void setAutoStart(Context context, boolean value) {
        of(context).edit().putBoolean(AUTO_START, value).apply();
    }

    /** Draw the theme bottom bar over the stock navigation app. */
    static boolean navBar(Context context) {
        return of(context).getBoolean(NAV_BAR, true);
    }

    static void setNavBar(Context context, boolean value) {
        of(context).edit().putBoolean(NAV_BAR, value).apply();
    }

    /** Float the ccNC back/home/menu keys and the now-playing box over stock screens. */
    static boolean corners(Context context) {
        return of(context).getBoolean(CORNERS, true);
    }

    static void setCorners(Context context, boolean value) {
        of(context).edit().putBoolean(CORNERS, value).commit();
    }

    /** Cover the stock navigation's left buttons with ccNC-styled, touch-through skins (needs the bar). */
    static boolean naviSkin(Context context) {
        return of(context).getBoolean(NAVI_SKIN, false);
    }

    static void setNaviSkin(Context context, boolean value) {
        of(context).edit().putBoolean(NAVI_SKIN, value).apply();
    }

    /**
     * Write stock (5W) setting values from the theme. Off by default: with it off the theme only
     * shows the values and opens the stock screen instead of writing anything.
     */
    static boolean stockWrite(Context context) {
        return of(context).getBoolean(STOCK_WRITE, false);
    }

    /** 고음/중음/저음·전후/좌우를 테마에서 직접 바꾸는지. 기본 켜짐. */
    static boolean soundWrite(Context context) {
        return of(context).getBoolean(SOUND_WRITE, true);
    }

    static void setSoundWrite(Context context, boolean value) {
        of(context).edit().putBoolean(SOUND_WRITE, value).commit();
    }

    static void setStockWrite(Context context, boolean value) {
        of(context).edit().putBoolean(STOCK_WRITE, value).commit();
    }

    /** 즐겨찾기(☆) 버튼으로 짧게 ccNC 테마 / 길게 순정 5W 홈 전환 사용 여부. 기본 켜짐. */
    static boolean customKeyHome(Context context) {
        return of(context).getBoolean(CUSTOM_KEY_HOME, true);
    }

    static void setCustomKeyHome(Context context, boolean value) {
        of(context).edit().putBoolean(CUSTOM_KEY_HOME, value).commit();
    }
}
