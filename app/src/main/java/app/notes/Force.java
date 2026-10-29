package app.notes;

import android.content.Context;
import android.content.SharedPreferences;

/** The number captured from the swipes, shared between GestureService and the notes screens. */
final class Force {
    /** A captured number is used only if the Celebrities note is opened within this time. */
    static final long MAX_AGE_MS = 30 * 60 * 1000L;
    /**
     * After the Celebrities note has been opened, new numbers are ignored for this long, so a
     * spectator playing with the phone straight after the reveal can't change the list.
     */
    static final long LOCK_MS = 3 * 60 * 1000L;

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

    /** When the current number was captured (0 if never). */
    static long capturedAt(Context c) {
        return prefs(c).getLong("t", 0);
    }

    /** Marks the moment the Celebrities note was shown, which starts the lock. */
    static void revealed(Context c) {
        prefs(c).edit().putLong("r", System.currentTimeMillis()).apply();
    }

    static boolean locked(Context c) {
        long r = prefs(c).getLong("r", 0);
        long now = System.currentTimeMillis();
        return r <= now && now - r < LOCK_MS;
    }
}
