package app.notes;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * The number entered with the swipes, and the trick's own settings (changed on the hidden
 * settings screen). Shared by GestureService, the in-app fallback and the notes screens.
 *
 * <p>Swipes only count while the trick is primed. Priming (on the hidden settings screen)
 * clears the last number; once a number has been entered, opening the Celebrities note or
 * leaving the app switches the trick off again, so the phone can be handed over and nothing
 * buzzes or changes until it is primed again.
 */
final class Force {

    static final int VIBRATION_OFF = 0, VIBRATION_LIGHT = 1, VIBRATION_MEDIUM = 2, VIBRATION_STRONG = 3;

    /** Choices offered on the hidden settings screen. */
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
     * The keypad: a 3x3 block of home-screen app positions, plus a 0 key. Stored as the centres
     * of the 1, 9 and 0 keys, as fractions of the screen width and height. The default is the
     * bottom-right 3x3 of a four-column home screen, just above the dock, with 0 the app to the
     * left of the 7 (the dock below the 8 can't turn the page).
     */
    static final float[] DEFAULT_KEYPAD = {0.375f, 0.42f, 0.875f, 0.72f, 0.125f, 0.72f};
    private static final String[] KEYPAD_KEYS = {"k1x", "k1y", "k9x", "k9y", "k0x", "k0y"};

    static float[] keypad(Context c) {
        SharedPreferences p = prefs(c);
        float[] k = new float[6];
        for (int i = 0; i < 6; i++) k[i] = p.getFloat(KEYPAD_KEYS[i], DEFAULT_KEYPAD[i]);
        return k;
    }

    static boolean customKeypad(Context c) {
        return prefs(c).contains(KEYPAD_KEYS[0]);
    }

    /** Saves a keypad set up on the home screen; null goes back to the default. */
    static void setKeypad(Context c, float[] k) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (int i = 0; i < 6; i++) {
            if (k == null) e.remove(KEYPAD_KEYS[i]);
            else e.putFloat(KEYPAD_KEYS[i], k[i]);
        }
        e.apply();
    }

    /** The second swipe must come within this time of the first. */
    static long pairMs(Context c) {
        return prefs(c).getLong("pair", 45_000L);
    }

    /** The name the trick forces: set on the hidden settings screen, or line 1 of celebrities.txt. */
    static String forcedName(Context c) {
        String s = prefs(c).getString("forced", "");
        return s.isEmpty() ? Celebs.names(c).get(0) : s;
    }

    /** Sets the forced name and rebuilds the Celebrities note; empty goes back to the default. */
    static void setForcedName(Context c, String name) {
        name = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (name.equals(Celebs.names(c).get(0))) name = "";
        prefs(c).edit().putString("forced", name).apply();
        Celebs.restore(c);
        MainActivity.numberChanged();
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

    // Primed or not -----------------------------------------------------------------------------

    /** True while swipes enter a number: from Prime until the number has been revealed. */
    static boolean armed(Context c) {
        return prefs(c).getBoolean("armed", false);
    }

    /**
     * Forgets the number, a digit in progress and the Celebrities line, and arms the trick for
     * the next two swipes. The keypad overlay goes off and stays off while the trick is primed.
     */
    static void prime(Context c) {
        reset(c);
        prefs(c).edit().putBoolean("armed", true).putBoolean("overlay", false).apply();
        GestureService.settingsChanged();
    }

    /**
     * Called when the number may have been seen: the Celebrities note was opened or the app was
     * left. Once a number has been entered, this switches the trick off until the next Prime.
     */
    static void performed(Context c) {
        if (!armed(c) || current(c) < 1) return;
        prefs(c).edit().putBoolean("armed", false).remove("d1").remove("d1t").apply();
        GestureService.settingsChanged();
    }

    /** Whether the keypad is drawn over the home screen to practise. Never while primed. */
    static boolean overlay(Context c) {
        return prefs(c).getBoolean("overlay", false) && !armed(c);
    }

    static void setOverlay(Context c, boolean on) {
        if (on && armed(c)) return;
        prefs(c).edit().putBoolean("overlay", on).apply();
        GestureService.settingsChanged();
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

    /** The first digit of a number being entered, or -1. */
    static int pendingDigit(Context c) {
        SharedPreferences p = prefs(c);
        if (System.currentTimeMillis() - p.getLong("d1t", 0) > pairMs(c)) return -1;
        return p.getInt("d1", -1);
    }

    /**
     * Records one swiped digit. The second digit within the pair time completes the number,
     * which rebuilds the Celebrities note straight away, while the app is still closed.
     * Returns false if the digit was ignored (the trick isn't primed).
     */
    static synchronized boolean addDigit(Context c, int d) {
        if (!armed(c)) return false;
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
        prefs(c).edit().remove("d1").remove("d1t").apply();
        save(c, n);
        Celebs.refresh(c);
        MainActivity.numberChanged();
    }

    /**
     * Forgets the number, a digit in progress and the Celebrities line, which goes to a new
     * random line.
     */
    private static void reset(Context c) {
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
