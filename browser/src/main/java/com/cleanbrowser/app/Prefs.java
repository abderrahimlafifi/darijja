package com.cleanbrowser.app;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

/** All user settings in one place. */
public final class Prefs {

    public static final String FILE = "settings";

    public static final String BLOCK_ADS = "block_ads";
    public static final String BLOCK_POPUPS = "block_popups";
    public static final String HIDE_ELEMENTS = "hide_elements";
    public static final String YOUTUBE_ADS = "youtube_ads";
    public static final String SOURCE_STEVENBLACK = "source_stevenblack";
    public static final String SOURCE_ADGUARD = "source_adguard";
    public static final String SEARCH_ENGINE = "search_engine";
    public static final String TEXT_ZOOM = "text_zoom";
    public static final String DESKTOP_MODE = "desktop_mode";
    public static final String CURSOR_MODE = "cursor_mode";
    public static final String CURSOR_SPEED = "cursor_speed";
    public static final String ALLOWED_SITES = "allowed_sites";
    public static final String LIST_UPDATED = "list_updated";
    public static final String LIST_SIZE = "list_size";
    public static final String TOTAL_BLOCKED = "total_blocked";
    public static final String TV_HINT_SHOWN = "tv_hint_shown";

    public static final String[] SEARCH_NAMES = {"Google", "DuckDuckGo", "Bing"};
    private static final String[] SEARCH_URLS = {
            "https://www.google.com/search?q=",
            "https://duckduckgo.com/?q=",
            "https://www.bing.com/search?q="
    };

    public static final int[] TEXT_ZOOMS = {75, 90, 100, 115, 130, 150, 175, 200};

    private Prefs() {
    }

    public static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean flag(Context c, String key, boolean def) {
        return get(c).getBoolean(key, def);
    }

    public static boolean blockAds(Context c) {
        return flag(c, BLOCK_ADS, true);
    }

    public static boolean blockPopups(Context c) {
        return flag(c, BLOCK_POPUPS, true);
    }

    public static boolean hideElements(Context c) {
        return flag(c, HIDE_ELEMENTS, true);
    }

    public static boolean youtubeAds(Context c) {
        return flag(c, YOUTUBE_ADS, true);
    }

    public static boolean desktopMode(Context c) {
        return flag(c, DESKTOP_MODE, false);
    }

    /** The remote control mouse cursor is on by default only on TVs. */
    public static boolean cursorMode(Context c) {
        return flag(c, CURSOR_MODE, isTv(c));
    }

    public static int searchEngine(Context c) {
        int i = get(c).getInt(SEARCH_ENGINE, 0);
        return (i < 0 || i >= SEARCH_URLS.length) ? 0 : i;
    }

    public static String searchUrl(Context c) {
        return SEARCH_URLS[searchEngine(c)];
    }

    public static int textZoom(Context c) {
        return get(c).getInt(TEXT_ZOOM, 100);
    }

    /** 1 = slow, 2 = normal, 3 = fast. */
    public static int cursorSpeed(Context c) {
        return get(c).getInt(CURSOR_SPEED, 2);
    }

    public static boolean isTv(Context c) {
        UiModeManager ui = (UiModeManager) c.getSystemService(Context.UI_MODE_SERVICE);
        if (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) return true;
        PackageManager pm = c.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
    }
}
