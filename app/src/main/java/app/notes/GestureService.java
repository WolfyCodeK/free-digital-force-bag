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
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads a keypad digit from where a swipe starts on the home screen.
 *
 * <p>Touch exploration is requested only so the system reports where each touch starts. A
 * passthrough region covering the whole screen makes the system hand every touch straight to
 * the apps underneath, unchanged and without waiting for this service. If the system ever drops
 * that region (it does whenever it rebuilds its input filter), the touch is handed over with
 * requestDelegating() instead and the region is set again.
 *
 * <p>A digit counts only if the home screen then shows another page. The service compares a
 * small snapshot of what is visible on the home screen (labels and their rough positions) from
 * when the finger went down with one taken just after it lifted. That works with any launcher.
 * A scroll event or page announcement from the launcher confirms it sooner. A tap that opens an
 * app, a long press (it opens a popup window) or a pull-down of the notification shade doesn't
 * count. Two digits make a number, saved through Force.
 *
 * <p>While one of this app's own screens is in front, touch exploration is switched off so the
 * app behaves exactly like any other app.
 */
public class GestureService extends AccessibilityService {

    /** How long after the finger lifts the launcher may take to report a page change. */
    private static final long CONFIRM_MS = 2000;
    /** When to compare the home screen with how it looked when the finger went down. */
    private static final long CHECK_1_MS = 450, CHECK_2_MS = 1200;
    /** Longer touches are not page swipes. */
    private static final long MAX_SWIPE_MS = 3000;
    private static final Pattern CLOCK = Pattern.compile("\\d{1,2}[:.]\\d{2}");
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
    private final Set<String> homePackages = new HashSet<>();
    private TouchInteractionController controller;
    private boolean capturing;
    private int pendingDigit = -1;
    private long pendingAt;
    private long pendingEndAt;
    private String pendingSnapshot;
    private long lastDownAt;
    private long touchStartAt;

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
    private final Runnable firstCheck = () -> checkPage(false);
    private final Runnable lastCheck = () -> checkPage(true);

    private final Runnable checkDownArrived = () -> {
        // A touch started but its DOWN never reached us: the system forgot that this service
        // detects gestures. Register again so touches keep passing straight through.
        // The DOWN and the "touch started" event arrive separately, in either order.
        if (capturing && Math.abs(touchStartAt - lastDownAt) > LOST_DOWN_MS) {
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
        if (debug()) info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
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
        if (debug() && e.getPackageName() != null && homePackages.contains(e.getPackageName().toString())) {
            log("event " + AccessibilityEvent.eventTypeToString(type) + " changes=" + e.getContentChangeTypes()
                    + " class=" + e.getClassName() + " text=" + e.getText());
        }
        if (type == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START) {
            log("touch exploration started; registering again");
            // Real touch exploration must never run; get back to passthrough at once.
            if (capturing) reassert();
            return;
        }
        if (type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            if (capturing) {
                touchStartAt = SystemClock.uptimeMillis();
                handler.removeCallbacks(checkDownArrived);
                handler.postDelayed(checkDownArrived, LOST_DOWN_MS);
            }
            return;
        }
        if (pendingDigit < 0) return;
        long now = SystemClock.uptimeMillis();
        if (type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END) {
            if (pendingEndAt == 0) {
                pendingEndAt = now;
                log("touch ended after " + (now - pendingAt) + " ms");
                handler.postDelayed(firstCheck, CHECK_1_MS);
                handler.postDelayed(lastCheck, CHECK_2_MS);
            }
            return;
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence from = e.getPackageName();
            boolean fromHome = from != null && homePackages.contains(from.toString());
            // A popup or menu opening during the touch, or any other app's window opening
            // afterwards (a tap that launched an app), means this was not a page swipe.
            if (pendingEndAt == 0 || !fromHome) {
                log("window changed (" + from + "); ignored");
                cancelPending();
            }
            return;
        }
        if (type != AccessibilityEvent.TYPE_VIEW_SCROLLED
                && type != AccessibilityEvent.TYPE_ANNOUNCEMENT) return;
        CharSequence pkg = e.getPackageName();
        if (pkg == null || !homePackages.contains(pkg.toString())) return;
        if (pendingEndAt != 0 && now - pendingEndAt > CONFIRM_MS) return;
        log("page changed (" + AccessibilityEvent.eventTypeToString(type) + ")");
        confirm();
    }

    /** Compares the home screen with how it looked when the finger went down. */
    private void checkPage(boolean last) {
        if (pendingDigit < 0) return;
        if (pendingEndAt - pendingAt > MAX_SWIPE_MS) {
            cancelPending();
            return;
        }
        AccessibilityNodeInfo root = homeRoot();
        if (root == null) {
            if (last) cancelPending();
            return;
        }
        String now = snapshot(root);
        log("snapshot " + pendingSnapshot.length() + " -> " + now.length() + " chars");
        if (now.isEmpty() || pendingSnapshot.isEmpty()) {
            // Nothing visible (an app is opening over the home screen): can't be a new page.
            if (last) cancelPending();
            return;
        }
        if (!now.equals(pendingSnapshot)) {
            log("page changed (home screen looks different)");
            confirm();
        } else if (last) {
            log("home screen unchanged; ignored");
            cancelPending();
        }
    }

    /**
     * The home screen's window if it is what the user sees, otherwise null. Reads the window
     * list rather than the "active window", which Android clears while a finger is down.
     */
    private AccessibilityNodeInfo homeRoot() {
        Point size = screenSize();
        Rect b = new Rect();
        for (AccessibilityWindowInfo w : getWindows()) {
            int type = w.getType();
            if (type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
                    || type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER
                    || type == AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY) {
                continue;
            }
            w.getBoundsInScreen(b);
            // The status bar and navigation bar are small system windows on top of everything.
            if (type == AccessibilityWindowInfo.TYPE_SYSTEM && b.height() < size.y / 3) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null || root.getPackageName() == null) return null;
            return homePackages.contains(root.getPackageName().toString()) ? root : null;
        }
        return null;
    }

    private void confirm() {
        int d = pendingDigit;
        cancelPending();
        if (d >= 0) commit(d);
    }

    private void cancelPending() {
        pendingDigit = -1;
        handler.removeCallbacks(firstCheck);
        handler.removeCallbacks(lastCheck);
    }

    /**
     * What the home screen shows: every visible label with its rough position. Clock-like text
     * is left out so a widget ticking over doesn't look like a new page.
     */
    private String snapshot(AccessibilityNodeInfo root) {
        Point size = screenSize();
        List<String> parts = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        Rect r = new Rect();
        int seen = 0;
        while (!queue.isEmpty() && seen++ < 400) {
            AccessibilityNodeInfo n = queue.poll();
            CharSequence label = n.getText() != null && n.getText().length() > 0
                    ? n.getText() : n.getContentDescription();
            if (label != null && label.length() > 0 && n.isVisibleToUser()
                    && !CLOCK.matcher(label).find()) {
                n.getBoundsInScreen(r);
                parts.add(label + "@" + (r.centerX() * 6 / Math.max(1, size.x)) + ","
                        + (r.centerY() * 12 / Math.max(1, size.y)));
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        Collections.sort(parts);
        return String.join("|", parts);
    }

    @Override
    public void onInterrupt() {
    }

    private void onTouchDown(float x, float y) {
        cancelPending();
        AccessibilityNodeInfo root = homeRoot();
        if (root == null) {
            log("not on the home screen");
            return;
        }
        Point size = screenSize();
        pendingSnapshot = snapshot(root);
        pendingAt = SystemClock.uptimeMillis();
        pendingEndAt = 0;
        pendingDigit = digitFor(x, y, size.x, size.y);
    }

    private void commit(int d) {
        if (Force.addDigit(this, d)) {
            log("digit " + d + (Force.pendingDigit(this) < 0 ? ", number " + Force.last(this) : ""));
        } else {
            log("locked, ignored");
        }
    }

    private static boolean debug() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }

    private static void log(String msg) {
        if (debug()) Log.d(TAG, msg);
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
