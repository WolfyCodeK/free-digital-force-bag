package app.notes;

import android.content.Context;
import android.content.SharedPreferences;

/** The number captured from the home-screen swipes, shared between the service and the activity. */
final class Force {
    private static final long MAX_AGE_MS = 30 * 60 * 1000L;

    private Force() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("f", Context.MODE_PRIVATE);
    }

    static void save(Context c, int n) {
        prefs(c).edit().putInt("n", n).putLong("t", System.currentTimeMillis()).apply();
    }

    /** The last captured number (1-100), or -1 if there is none or it is stale. */
    static int current(Context c) {
        SharedPreferences p = prefs(c);
        if (System.currentTimeMillis() - p.getLong("t", 0) > MAX_AGE_MS) return -1;
        return p.getInt("n", -1);
    }
}
