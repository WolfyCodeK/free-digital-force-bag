package app.notes;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * The number entered with the swipes, and the trick's own settings (changed on the hidden
 * settings screen). Shared by GestureService, the in-app fallback and the notes screens.
 */
final class Force {

    static final int VIBRATION_OFF = 0, VIBRATION_LIGHT = 1, VIBRATION_MEDIUM = 2, VIBRATION_STRONG = 3;

    /** Choices offered on the hidden settings screen. */
    static final long[] LOCK_CHOICES = {0, 60_000L, 3 * 60_000L, 10 * 60_000L};
    static final long[] PAIR_CHOICES = {20_000L, 45_000L, 90_000L};

    private Force() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("f", Context.MODE_PRIVATE);
    }

    // Settings ---------------------------------------------------------------------------------

    static int vibration(Context c) {
        return prefs(c).getInt("vibration", VIBRATION_MEDIUM);
    }

    /** Buzz twice when the second digit completes the number. */
    static boolean doubleBuzz(Context c) {
        return prefs(c).getBoolean("doubleBuzz", true);
    }

    /**
     * After the Celebrities note has been opened, new numbers are ignored for this long, so a
     * spectator playing with the phone straight after the reveal can't change the list.
     */
    static long lockMs(Context c) {
        return prefs(c).getLong("lock", 3 * 60_000L);
    }

    /** The second swipe must come within this time of the first. */
    static long pairMs(Context c) {
        return prefs(c).getLong("pair", 45_000L);
    }

    static void setInt(Context c, String key, int value) {
        prefs(c).edit().putInt(key, value).apply();
    }

    static void setLong(Context c, String key, long value) {
        prefs(c).edit().putLong(key, value).apply();
    }

    static void setBool(Context c, String key, boolean value) {
        prefs(c).edit().putBoolean(key, value).apply();
    }

    // The number -------------------------------------------------------------------------------

    static void save(Context c, int n) {
        prefs(c).edit().putInt("n", n).putLong("t", System.currentTimeMillis()).apply();
    }

    /**
     * The last captured number (1-100), or -1 if there is none. It stays until a new number is
     * entered or everything is reset; the note was already rebuilt for it when it was captured.
     */
    static int current(Context c) {
        return prefs(c).getInt("n", -1);
    }

    /** When the current number was captured (0 if never). */
    static long capturedAt(Context c) {
        return prefs(c).getLong("t", 0);
    }

    /** Marks the moment the Celebrities note was shown, which starts the lock. */
    static void revealed(Context c) {
        prefs(c).edit().putLong("r", System.currentTimeMillis()).apply();
    }

    /** How much longer new numbers are ignored, in ms (0 if they aren't). */
    static long lockLeft(Context c) {
        long r = prefs(c).getLong("r", 0);
        long now = System.currentTimeMillis();
        if (r > now) return 0;
        return Math.max(0, r + lockMs(c) - now);
    }

    static boolean locked(Context c) {
        return lockLeft(c) > 0;
    }

    /** The first digit of a number being entered, or -1. */
    static int pendingDigit(Context c) {
        SharedPreferences p = prefs(c);
        if (System.currentTimeMillis() - p.getLong("d1t", 0) > pairMs(c)) return -1;
        return p.getInt("d1", -1);
    }

    /**
     * Records one swiped digit. The second digit within the pair time completes the number,
     * which rebuilds the Celebrities note straight away, while the app is still closed.
     * Returns false if the digit was ignored (straight after a reveal).
     */
    static synchronized boolean addDigit(Context c, int d) {
        if (locked(c)) return false;
        int first = pendingDigit(c);
        if (first < 0) {
            prefs(c).edit().putInt("d1", d).putLong("d1t", System.currentTimeMillis()).apply();
            buzz(c, false);
            return true;
        }
        int n = first * 10 + d;
        if (n == 0) n = 100;
        prefs(c).edit().remove("d1").remove("d1t").apply();
        save(c, n);
        buzz(c, doubleBuzz(c));
        Celebs.refresh(c);
        MainActivity.numberChanged();
        return true;
    }

    /** Sets the number directly (hidden settings), as if it had just been swiped. */
    static void setManually(Context c, int n) {
        prefs(c).edit().remove("d1").remove("d1t").remove("r").apply();
        save(c, n);
        Celebs.refresh(c);
        MainActivity.numberChanged();
    }

    /**
     * Forgets everything: the number, a digit in progress, the lock and the Celebrities line,
     * which goes to a new random line the next time the note is shown.
     */
    static void reset(Context c) {
        prefs(c).edit().remove("n").remove("t").remove("r").remove("d1").remove("d1t").apply();
        Celebs.forgetLine(c);
        Celebs.restore(c);
        MainActivity.numberChanged();
    }

    // Feedback ---------------------------------------------------------------------------------

    /** One short buzz for a digit, or two when the number is complete. */
    static void buzz(Context c, boolean twice) {
        int level = vibration(c);
        if (level == VIBRATION_OFF) return;
        Vibrator vib = c.getSystemService(Vibrator.class);
        if (vib == null || !vib.hasVibrator()) return;
        long ms = level == VIBRATION_LIGHT ? 18 : level == VIBRATION_MEDIUM ? 30 : 45;
        int amp = !vib.hasAmplitudeControl() ? VibrationEffect.DEFAULT_AMPLITUDE
                : level == VIBRATION_LIGHT ? 70 : level == VIBRATION_MEDIUM ? 160 : 255;
        VibrationEffect fx = twice
                ? VibrationEffect.createWaveform(new long[]{0, ms, 110, ms}, new int[]{0, amp, 0, amp}, -1)
                : VibrationEffect.createOneShot(ms, amp);
        vib.vibrate(fx, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ACCESSIBILITY));
    }
}
