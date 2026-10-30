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
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    /**
     * A swipe that doesn't turn the page (the last page has nothing after it) still counts if it
     * moves at least this far sideways, mostly sideways, and doesn't start at the very edge of the
     * screen, where gesture navigation's Back and Home swipes start.
     */
    private static final float SIDEWAYS_DP = 56, SIDE_EDGE_DP = 28, BOTTOM_EDGE_DP = 48;
    /**
     * On the page with this app's icon (the last page, where the reveal starts) a swipe can't
     * turn the page, so a keypad touch there counts without a page turn: if it lasted at least
     * this long, opened nothing (an app opening cancels it) and wasn't a long press (its menu
     * cancels it). Only settled at the last check, by which time a tapped app has opened.
     */
    private static final long REVEAL_PAGE_MIN_MS = 60;

    private static final Region EVERYWHERE = new Region(0, 0, 100_000, 100_000);

    /** Silent unless enabled with: adb shell setprop log.tag.KeepGestures DEBUG */
    private static final String TAG = "KeepGestures";

    private static GestureService instance;
    private static boolean appInFront;
    private boolean homeInFront;

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
    /** Where the current touch went down, where it is now, and whether that is known. */
    private float downX, downY, curX, curY;
    private boolean moved, lifted, multiTouch;
    /** The practice keypad drawn over the home screen, or null until it is first needed. */
    private KeypadOverlay overlay;
    /** Prime asked for the first home-screen page: press Home again once home is showing. */
    private boolean wantFirstPage;
    /** Setting the keypad up: the overlay asks for the 1, 9 and 0 apps on the home screen. */
    private boolean calibrating;

    private final TouchInteractionController.Callback callback =
            new TouchInteractionController.Callback() {
                @Override
                public void onMotionEvent(MotionEvent e) {
                    // Touches pass straight through to the launcher, so only their DOWN comes here.
                    if (e.getActionMasked() != MotionEvent.ACTION_DOWN) {
                        followFinger(e, "controller");
                        return;
                    }
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
        if (instance == null) return;
        if (inFront) instance.calibrating = false;
        instance.updateCapturing();
    }

    /**
     * Goes to the home screen and asks for the apps to use as 1, 9 and 0. Returns false if the
     * service isn't running.
     */
    static boolean setUpKeypad() {
        GestureService s = instance;
        if (s == null) return false;
        s.calibrating = true;
        s.performGlobalAction(GLOBAL_ACTION_HOME);
        s.handler.post(s::updateCapturing);
        return true;
    }

    /**
     * The middle of the app (icon and label) under a point on the home screen, so a tap during
     * keypad set-up lines the key up with the launcher's own grid. The point itself if no app is
     * there.
     */
    private float[] snapToApp(float x, float y) {
        AccessibilityNodeInfo root = homeRoot();
        if (root == null) return new float[]{x, y};
        float dp = getResources().getDisplayMetrics().density;
        Rect best = null;
        Rect b = new Rect();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        for (int seen = 0; !queue.isEmpty() && seen < 3000; seen++) {
            AccessibilityNodeInfo n = queue.poll();
            n.getBoundsInScreen(b);
            if (!b.contains((int) x, (int) y)) continue;
            boolean app = n.isClickable() && (n.getText() != null || n.getContentDescription() != null)
                    && b.width() >= 40 * dp && b.width() <= 180 * dp
                    && b.height() >= 40 * dp && b.height() <= 180 * dp;
            if (app && (best == null || b.width() * b.height() < best.width() * best.height())) {
                best = new Rect(b);
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        log("set-up tap " + (int) x + "," + (int) y + (best != null ? " -> app " + best.toShortString() : ", no app there"));
        return best == null ? new float[]{x, y} : new float[]{best.exactCenterX(), best.exactCenterY()};
    }

    /** The overlay finished setting the keypad up (k) or it was cancelled (null). */
    private void keypadSetUp(float[] k) {
        calibrating = false;
        if (k != null) {
            Force.setKeypad(this, k);
            Force.buzz(this, false);
        }
        log(k != null ? "keypad set up" : "keypad set-up cancelled");
        updateCapturing();
    }

    /** Called by Force when the trick is primed or switched off, or the overlay is toggled. */
    static void settingsChanged() {
        GestureService s = instance;
        if (s != null) s.handler.post(s::updateCapturing);
    }

    /**
     * Goes to the home screen's first page: Home, then Home again once the home screen is
     * showing, which launchers answer by going to their main page. Returns false if the
     * service isn't running.
     */
    static boolean goToFirstPage() {
        GestureService s = instance;
        if (s == null) return false;
        s.wantFirstPage = true;
        s.performGlobalAction(GLOBAL_ACTION_HOME);
        s.handler.removeCallbacks(s.giveUpFirstPage);
        s.handler.postDelayed(s.giveUpFirstPage, 5000);
        return true;
    }

    private final Runnable homeAgain = () -> {
        wantFirstPage = false;
        log("home again for the first page");
        performGlobalAction(GLOBAL_ACTION_HOME);
    };

    private final Runnable giveUpFirstPage = () -> wantFirstPage = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        resolveHomePackages();
        controller = getTouchInteractionController(Display.DEFAULT_DISPLAY);
        homeInFront = homeShowing();
        updateCapturing();
    }

    /** True if the home screen is what the user sees (not Recents, the shade or an app). */
    private boolean homeShowing() {
        AccessibilityNodeInfo root = homeRoot();
        if (root == null) return false;
        CharSequence cls = root.getClassName();
        if (cls != null && cls.toString().toLowerCase().contains("recents")) return false;
        for (AccessibilityWindowInfo w : getWindows()) {
            CharSequence title = w.getTitle();
            if (title == null || w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            String t = title.toString().toLowerCase();
            if (t.contains("recent") || t.contains("overview")) return false;
            break;
        }
        return true;
    }

    private final Runnable checkHome = () -> {
        boolean home = homeShowing();
        if (home != homeInFront) {
            homeInFront = home;
            log("home screen " + (home ? "showing" : "gone"));
            updateCapturing();
        }
        if (home && wantFirstPage) {
            wantFirstPage = false;
            handler.removeCallbacks(giveUpFirstPage);
            handler.postDelayed(homeAgain, 600);
        }
    };

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
        if (overlay != null) {
            try {
                getSystemService(WindowManager.class).removeView(overlay);
            } catch (RuntimeException ignored) {
            }
            overlay = null;
        }
    }

    private void updateCapturing() {
        if (controller == null) return;
        // Only while the home screen itself is showing: everywhere else apps would see touch
        // exploration on and behave as if a screen reader were running (Samsung's Recents, for
        // one, draws a green focus box around the current app).
        // And only while the trick is primed or the practice overlay is on: after the reveal
        // the service stops listening altogether until the trick is primed again.
        boolean want = !appInFront && homeInFront && (Force.armed(this) || Force.overlay(this));
        updateOverlay();
        if (want == capturing) return;
        capturing = want;
        log("capturing " + want);
        // A digit only counts while the home screen is still showing: a tap that opened an app
        // (this one included) must never be confirmed later.
        if (!want) cancelPending();
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
     * Shows the practice keypad while it is switched on and the home screen is showing, and the
     * keypad set-up screen while that is running.
     */
    private void updateOverlay() {
        boolean practice = Force.overlay(this);
        boolean show = (practice || calibrating) && homeInFront && !appInFront;
        WindowManager wm = getSystemService(WindowManager.class);
        try {
            if (overlay == null) {
                if (!show) return;
                overlay = new KeypadOverlay(this, this::keypadSetUp, this::snapToApp);
                overlay.setSettingUp(calibrating);
                wm.addView(overlay, KeypadOverlay.layoutParams(calibrating));
            } else if (overlay.isSettingUp() != calibrating) {
                overlay.setSettingUp(calibrating);
                wm.updateViewLayout(overlay, KeypadOverlay.layoutParams(calibrating));
            }
        } catch (RuntimeException e) {
            log("overlay failed: " + e);
            overlay = null;
            return;
        }
        overlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!practice) overlay.clear();
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
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            handler.removeCallbacks(checkHome);
            handler.postDelayed(checkHome, 120);
        }
        if (type == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            // Nothing should look as if a screen reader were running: drop any focus box at once.
            AccessibilityNodeInfo source = e.getSource();
            if (source != null) {
                source.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            }
            return;
        }
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
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
            touchEnded();
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
        if (!mostlySideways()) return;
        log("page changed (" + AccessibilityEvent.eventTypeToString(type) + ")");
        confirm();
    }

    private void followFinger(MotionEvent e, String from) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            multiTouch = true;
        } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            curX = e.getX();
            curY = e.getY();
            if (!moved) log("first move from the " + from);
            moved = true;
            if (action == MotionEvent.ACTION_UP) {
                log("lift-off from the " + from + " at " + (int) curX + "," + (int) curY);
                onTouchUp();
            }
        }
    }

    /** The finger lifted (from the motion events, or the "touch ended" event if those are missing). */
    private void touchEnded() {
        if (pendingDigit < 0 || pendingEndAt != 0) return;
        pendingEndAt = SystemClock.uptimeMillis();
        log("touch ended after " + (pendingEndAt - pendingAt) + " ms"
                + (lifted ? ", moved " + (int) (curX - downX) + "," + (int) (curY - downY) : ""));
        handler.postDelayed(firstCheck, CHECK_1_MS);
        handler.postDelayed(lastCheck, CHECK_2_MS);
    }

    private void onTouchUp() {
        lifted = true;
        touchEnded();
    }

    /**
     * True unless the finger is known to have moved more up or down than sideways (the app
     * drawer, the notification shade). Without motion events this can't be known, so it's true.
     */
    private boolean mostlySideways() {
        if (multiTouch) return false;
        return !moved || Math.abs(curX - downX) >= Math.abs(curY - downY);
    }

    /** A clear sideways swipe that has ended, whether or not the page turned. */
    private boolean sidewaysSwipe() {
        if (multiTouch || !lifted) return false;
        Point size = screenSize();
        float dp = getResources().getDisplayMetrics().density;
        if (downX < SIDE_EDGE_DP * dp || downX > size.x - SIDE_EDGE_DP * dp
                || downY > size.y - BOTTOM_EDGE_DP * dp) {
            return false;
        }
        float dx = Math.abs(curX - downX), dy = Math.abs(curY - downY);
        return dx >= SIDEWAYS_DP * dp && dx >= 1.5f * dy;
    }

    /**
     * True if the finished touch was on the home screen page that shows this app's icon, and
     * long enough to be a swipe rather than a brush of the screen.
     */
    private boolean onRevealPage() {
        if (multiTouch || pendingEndAt - pendingAt < REVEAL_PAGE_MIN_MS) return false;
        AccessibilityNodeInfo root = homeRoot();
        if (root == null) return false;
        String name = getString(R.string.app_name).toLowerCase(Locale.ROOT);
        Point size = screenSize();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        Rect r = new Rect();
        for (int seen = 0; !queue.isEmpty() && seen < 400; seen++) {
            AccessibilityNodeInfo n = queue.poll();
            CharSequence label = n.getText() != null && n.getText().length() > 0
                    ? n.getText() : n.getContentDescription();
            if (label != null && label.toString().toLowerCase(Locale.ROOT).contains(name) && n.isVisibleToUser()) {
                n.getBoundsInScreen(r);
                if (r.left >= 0 && r.right <= size.x) return true;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return false;
    }

    /** Compares the home screen with how it looked when the finger went down. */
    private void checkPage(boolean last) {
        if (pendingDigit < 0) return;
        if (!capturing || !homeShowing()) {
            cancelPending();
            return;
        }
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
        if (!now.equals(pendingSnapshot) && mostlySideways()) {
            log("page changed (home screen looks different)");
            confirm();
        } else if (sidewaysSwipe()) {
            log("sideways swipe; the page didn't turn (no page that way)");
            confirm();
        } else if (last && onRevealPage()) {
            log("swipe on the Keep Notes page; the page can't turn there");
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
        AccessibilityNodeInfo root = homeRoot();
        String now = root == null ? "" : snapshot(root);
        if (pendingDigit >= 0) {
            // The next swipe started before the last one was checked (quick swipes back to back):
            // settle the last one now instead of dropping it.
            boolean changed = !now.isEmpty() && !pendingSnapshot.isEmpty() && !now.equals(pendingSnapshot);
            if (root != null && pendingEndAt != 0 && pendingEndAt - pendingAt <= MAX_SWIPE_MS
                    && ((changed && mostlySideways()) || sidewaysSwipe() || (!changed && onRevealPage()))) {
                log("settled by the next touch");
                confirm();
            } else {
                cancelPending();
            }
        }
        downX = curX = x;
        downY = curY = y;
        moved = lifted = multiTouch = false;
        if (root == null) {
            log("not on the home screen");
            return;
        }
        if (calibrating) return;
        Point size = screenSize();
        int d = digitFor(x, y, size.x, size.y, Force.keypad(this));
        if (d < 0) {
            log("outside the keypad");
            return;
        }
        pendingSnapshot = now;
        pendingAt = SystemClock.uptimeMillis();
        pendingEndAt = 0;
        pendingDigit = d;
    }

    private void commit(int d) {
        if (Force.addDigit(this, d)) {
            log("digit " + d + (Force.pendingDigit(this) < 0 ? ", number " + Force.current(this) : ""));
        } else if (overlay != null && Force.overlay(this)) {
            // Practice: light the cell and buzz, but leave the real number alone.
            boolean complete = overlay.swiped(d);
            Force.buzz(this, complete && Force.doubleBuzz(this));
            log("practice digit " + d);
        } else {
            log("not primed, ignored");
        }
    }

    private static boolean debug() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }

    private static void log(String msg) {
        if (debug()) Log.d(TAG, msg);
    }

    /**
     * The digit of the keypad cell a touch starts in (see Force.keypad), or -1 if it starts
     * anywhere else: those swipes never count, so pages can be turned freely from outside it.
     * Each cell is one app position: the 3x3 between the 1 and 9 keys, and one cell around 0.
     */
    static int digitFor(float x, float y, int w, int h, float[] k) {
        float x1 = k[0] * w, y1 = k[1] * h, cw = (k[2] - k[0]) * w / 2, ch = (k[3] - k[1]) * h / 2;
        if (cw <= 0 || ch <= 0) return -1;
        float fx = (x - x1) / cw + 0.5f, fy = (y - y1) / ch + 0.5f;
        if (fx >= 0 && fx < 3 && fy >= 0 && fy < 3) return (int) fy * 3 + (int) fx + 1;
        if (Math.abs(x - k[4] * w) <= cw / 2 && Math.abs(y - k[5] * h) <= ch / 2) return 0;
        return -1;
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
