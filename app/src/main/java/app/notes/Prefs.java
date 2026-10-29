package app.notes;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Keep's settings and the notes screen's view options. */
final class Prefs {

    static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2;

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean grid(Context c) {
        return p(c).getBoolean("grid", true);
    }

    static void setGrid(Context c, boolean grid) {
        p(c).edit().putBoolean("grid", grid).apply();
    }

    static int sort(Context c) {
        return p(c).getInt("sort", MainActivity.SORT_CUSTOM);
    }

    static boolean sortReversed(Context c) {
        return p(c).getBoolean("sortReversed", false);
    }

    static void setSort(Context c, int sort, boolean reversed) {
        p(c).edit().putInt("sort", sort).putBoolean("sortReversed", reversed).apply();
    }

    static boolean addToBottom(Context c) {
        return p(c).getBoolean("addToBottom", true);
    }

    static boolean moveTickedToBottom(Context c) {
        return p(c).getBoolean("tickedToBottom", true);
    }

    static boolean richLinks(Context c) {
        return p(c).getBoolean("richLinks", true);
    }

    static boolean sharing(Context c) {
        return p(c).getBoolean("sharing", true);
    }

    static void setBool(Context c, String key, boolean value) {
        p(c).edit().putBoolean(key, value).apply();
    }

    static int theme(Context c) {
        return p(c).getInt("theme", THEME_SYSTEM);
    }

    static void setTheme(Context c, int theme) {
        p(c).edit().putInt("theme", theme).apply();
        applyTheme(c);
    }

    static void applyTheme(Context c) {
        int t = theme(c);
        AppCompatDelegate.setDefaultNightMode(t == THEME_LIGHT ? AppCompatDelegate.MODE_NIGHT_NO
                : t == THEME_DARK ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /** Reminder default times, in minutes after midnight. */
    static int morning(Context c) {
        return p(c).getInt("morning", 8 * 60);
    }

    static int afternoon(Context c) {
        return p(c).getInt("afternoon", 13 * 60);
    }

    static int evening(Context c) {
        return p(c).getInt("evening", 18 * 60);
    }

    static void setTime(Context c, String key, int minutes) {
        p(c).edit().putInt(key, minutes).apply();
    }
}
