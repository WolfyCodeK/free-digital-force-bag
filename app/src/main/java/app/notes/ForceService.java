package app.notes;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.TouchInteractionController;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the keypad digit from where a swipe starts on the home screen. Every touch is
 * handed straight back to the system, so the home screen behaves normally. A digit
 * counts only if the launcher scrolls (a page change) shortly after the touch began.
 * Two digits make a number, which is saved for MainActivity.
 */
public class ForceService extends AccessibilityService {

    private static final boolean HAPTIC = true;
    private static final long CONFIRM_MS = 2000;
    private static final long PAIR_TIMEOUT_MS = 45_000;

    private static final int[][] KEYPAD = {
            {1, 2, 3},
            {4, 5, 6},
            {7, 8, 9},
            {0, 0, 0},
    };

    private final List<Integer> digits = new ArrayList<>();
    private long lastDigitAt;
    private int pendingDigit = -1;
    private long pendingAt;
    private String homePackage;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        homePackage = resolveHomePackage();
        TouchInteractionController c = getTouchInteractionController(Display.DEFAULT_DISPLAY);
        c.registerCallback(getMainExecutor(), new TouchInteractionController.Callback() {
            @Override
            public void onMotionEvent(MotionEvent e) {
                if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return;
                DisplayMetrics dm = getResources().getDisplayMetrics();
                pendingDigit = digitFor(e.getX(), e.getY(), dm.widthPixels, dm.heightPixels);
                pendingAt = System.currentTimeMillis();
                try {
                    c.requestDelegating();
                } catch (IllegalStateException ignored) {
                }
            }

            @Override
            public void onStateChanged(int state) {
            }
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (pendingDigit < 0) return;
        int type = e.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_SCROLLED
                && type != AccessibilityEvent.TYPE_ANNOUNCEMENT) return;
        if (homePackage != null && !homePackage.contentEquals(e.getPackageName())) return;
        if (System.currentTimeMillis() - pendingAt > CONFIRM_MS) {
            pendingDigit = -1;
            return;
        }
        int d = pendingDigit;
        pendingDigit = -1;
        commit(d);
    }

    @Override
    public void onInterrupt() {
    }

    private void commit(int d) {
        long now = System.currentTimeMillis();
        if (now - lastDigitAt > PAIR_TIMEOUT_MS) digits.clear();
        lastDigitAt = now;
        digits.add(d);
        buzz();
        if (digits.size() == 2) {
            int n = digits.get(0) * 10 + digits.get(1);
            Force.save(this, n == 0 ? 100 : n);
            digits.clear();
        }
    }

    private static int digitFor(float x, float y, int w, int h) {
        int col = Math.max(0, Math.min(2, (int) (x / w * 3)));
        int row = Math.max(0, Math.min(3, (int) (y / h * 4)));
        return KEYPAD[row][col];
    }

    private void buzz() {
        if (!HAPTIC) return;
        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vib != null) vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    private String resolveHomePackage() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri == null ? null : ri.activityInfo.packageName;
    }
}
