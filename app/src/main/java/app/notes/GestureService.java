package app.notes;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.TouchInteractionController;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.util.Log;
import android.os.Vibrator;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a keypad digit from where a swipe starts on the home screen.
 *
 * <p>Touch exploration is requested only so the system reports where each touch starts. A
 * passthrough region covering the whole screen makes the system hand every touch straight to
 * the apps underneath, unchanged and without waiting for this service. If the system ever drops
 * that region (it does whenever it rebuilds its input filter), the touch is handed over with
 * requestDelegating() instead and the region is set again.
 *
 * <p>A digit counts only if the launcher then changes page (it sends TYPE_VIEW_SCROLLED or a page
 * announcement) shortly after the touch began. Two digits make a number, saved through Force.
 *
 * <p>While one of this app's own screens is in front, touch exploration is switched off so the
 * app behaves exactly like any other app.
 */
public class GestureService extends AccessibilityService {

    /** One short buzz for every accepted digit. */
    private static final boolean HAPTIC = true;
    /** How long after the touch starts the launcher may take to change page. */
    private static final long CONFIRM_MS = 2000;
    /** A lone first digit is forgotten after this long. */
    private static final long PAIR_TIMEOUT_MS = 45_000;
    /** If a touch starts but no DOWN reaches us within this time, re-register. */
    private static final long LOST_DOWN_MS = 300;

    private static final int[][] KEYPAD = {
            {1, 2, 3},
            {4, 5, 6},
            {7, 8, 9},
            {0, 0, 0},
    };

    private static final Region EVERYWHERE = new Region(0, 0, 100_000, 100_000);

    /** Silent unless enabled with: adb shell setprop log.tag.KeepGestures DEBUG */
    private static final String TAG = "KeepGestures";

    private static GestureService instance;
    private static boolean appInFront;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> digits = new ArrayList<>();
    private final Set<String> homePackages = new HashSet<>();
    private TouchInteractionController controller;
    private boolean capturing;
    private long lastDigitAt;
    private int pendingDigit = -1;
    private long pendingAt;
    private long lastDownAt;

    private final TouchInteractionController.Callback callback =
            new TouchInteractionController.Callback() {
                @Override
                public void onMotionEvent(MotionEvent e) {
                    if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return;
                    lastDownAt = SystemClock.uptimeMillis();
                    log("down " + (int) e.getX() + "," + (int) e.getY() + " state "
                            + TouchInteractionController.stateToString(controller.getState()));
                    if (controller.getState() != TouchInteractionController.STATE_DELEGATING) {
                        // The passthrough region was dropped: hand this touch over and set it again.
                        try {
                            controller.requestDelegating();
                        } catch (IllegalStateException ignored) {
                        }
                        applyPassthrough();
                    }
                    onTouchDown(e.getX(), e.getY());
                }

                @Override
                public void onStateChanged(int state) {
                }
            };

    private final Runnable passthroughTask = this::applyPassthrough;

    private final Runnable checkDownArrived = () -> {
        // A touch started but its DOWN never reached us: the system forgot that this service
        // detects gestures. Register again so touches keep passing straight through.
        if (capturing && SystemClock.uptimeMillis() - lastDownAt > LOST_DOWN_MS) {
            log("touch started without a DOWN; registering again");
            reassert();
        }
    };

    /** Called by App when this app's own screens come to the front or leave it. */
    static void setAppInFront(boolean inFront) {
        appInFront = inFront;
        if (instance != null) instance.updateCapturing();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        resolveHomePackages();
        controller = getTouchInteractionController(Display.DEFAULT_DISPLAY);
        updateCapturing();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stop();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    private void stop() {
        if (instance == this) instance = null;
        handler.removeCallbacksAndMessages(null);
        capturing = false;
    }

    private void updateCapturing() {
        if (controller == null) return;
        boolean want = !appInFront;
        if (want == capturing) return;
        capturing = want;
        log("capturing " + want);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        if (want) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        } else {
            info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        }
        setServiceInfo(info);
        if (want) reassert();
    }

    /**
     * Tells the system again that this service detects gestures and that every touch passes
     * through. Both are forgotten whenever the system rebuilds its input filter, so this is
     * repeated a few times in case the filter is still being installed.
     */
    private void reassert() {
        if (controller == null || !capturing) return;
        controller.unregisterAllCallbacks();
        controller.registerCallback(getMainExecutor(), callback);
        applyPassthrough();
        handler.removeCallbacks(passthroughTask);
        handler.postDelayed(passthroughTask, 150);
        handler.postDelayed(passthroughTask, 700);
        handler.postDelayed(passthroughTask, 2500);
    }

    private void applyPassthrough() {
        if (!capturing) return;
        try {
            setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY, EVERYWHERE);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        int type = e.getEventType();
        if (type == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START) {
            log("touch exploration started; registering again");
            // Real touch exploration must never run; get back to passthrough at once.
            if (capturing) reassert();
            return;
        }
        if (type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            if (capturing) {
                handler.removeCallbacks(checkDownArrived);
                handler.postDelayed(checkDownArrived, LOST_DOWN_MS);
            }
            return;
        }
        if (pendingDigit < 0) return;
        if (type != AccessibilityEvent.TYPE_VIEW_SCROLLED
                && type != AccessibilityEvent.TYPE_ANNOUNCEMENT) return;
        CharSequence pkg = e.getPackageName();
        if (pkg == null || !homePackages.contains(pkg.toString())) return;
        if (SystemClock.uptimeMillis() - pendingAt > CONFIRM_MS) {
            pendingDigit = -1;
            return;
        }
        int d = pendingDigit;
        pendingDigit = -1;
        log("page changed (" + AccessibilityEvent.eventTypeToString(type) + ")");
        commit(d);
    }

    @Override
    public void onInterrupt() {
    }

    private void onTouchDown(float x, float y) {
        Point size = screenSize();
        pendingDigit = digitFor(x, y, size.x, size.y);
        pendingAt = SystemClock.uptimeMillis();
    }

    private void commit(int d) {
        if (Force.locked(this)) {
            log("locked, ignored");
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastDigitAt > PAIR_TIMEOUT_MS) digits.clear();
        lastDigitAt = now;
        digits.add(d);
        buzz();
        log("digit " + d);
        if (digits.size() == 2) {
            int n = digits.get(0) * 10 + digits.get(1);
            log("number " + (n == 0 ? 100 : n));
            Force.save(this, n == 0 ? 100 : n);
            digits.clear();
        }
    }

    private static void log(String msg) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, msg);
    }

    static int digitFor(float x, float y, int w, int h) {
        int col = Math.max(0, Math.min(2, (int) (x / w * 3)));
        int row = Math.max(0, Math.min(3, (int) (y / h * 4)));
        return KEYPAD[row][col];
    }

    /** The full size of the screen in its current rotation, system bars included. */
    private Point screenSize() {
        try {
            Rect b = getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            if (b.width() > 0 && b.height() > 0) return new Point(b.width(), b.height());
        } catch (RuntimeException ignored) {
        }
        Point p = new Point();
        Display d = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        d.getRealSize(p);
        return p;
    }

    private void buzz() {
        if (!HAPTIC) return;
        Vibrator vib = getSystemService(Vibrator.class);
        if (vib == null || !vib.hasVibrator()) return;
        vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ACCESSIBILITY));
    }

    /** Every installed launcher, except the system's own fallback screens. */
    private void resolveHomePackages() {
        homePackages.clear();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        PackageManager pm = getPackageManager();
        for (ResolveInfo ri : pm.queryIntentActivities(home, PackageManager.MATCH_ALL)) {
            String p = ri.activityInfo.packageName;
            if (!"android".equals(p) && !"com.android.settings".equals(p)) homePackages.add(p);
        }
        ResolveInfo def = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && !"android".equals(def.activityInfo.packageName)) {
            homePackages.add(def.activityInfo.packageName);
        }
    }
}
