package app.notes;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.core.content.res.ResourcesCompat;

import java.util.function.Consumer;

/**
 * Practice aid, switched on in the hidden settings: draws the keypad (the 3x3 block of app
 * positions and the 0 key) over the home screen and lights up the cell of each swipe that
 * counts. It never takes touches then, and it is only shown while the trick isn't primed.
 *
 * <p>It also sets the keypad up: in that mode it takes the touches itself (so nothing opens) and
 * asks for the app to use as 1, then 9, then 0.
 */
final class KeypadOverlay extends View {

    private static final long FLASH_MS = 900;
    private static final String IDLE = "Practice: swipes don't enter a number";
    private static final String[] ASK = {
            "Tap the app for 1 (top-left)",
            "Now the app for 9 (bottom-right)",
            "Now the app for 0",
    };

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint();
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private final int[] loc = new int[2];
    private final RectF r = new RectF();
    private final RectF cancel = new RectF();
    private final Consumer<float[]> onSetUp;

    private int flash = -1;
    private long flashUntil;
    private int first = -1;
    private long firstAt;
    private String status = IDLE;
    private final Runnable forgetFirst = () -> {
        first = -1;
        status = IDLE;
        invalidate();
    };

    private boolean settingUp;
    private int step;
    private final float[] taps = new float[6];
    private String problem;

    /**
     * {@code onSetUp} gets the new keypad (see Force.keypad) when it has been set up, or null if
     * setting it up was cancelled.
     */
    KeypadOverlay(Context c, Consumer<float[]> onSetUp) {
        super(c);
        this.onSetUp = onSetUp;
        dp = c.getResources().getDisplayMetrics().density;
        Typeface font = ResourcesCompat.getFont(c, R.font.google_sans_medium);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0xE6FFFFFF);
        line.setStrokeWidth(1.5f * dp);
        line.setShadowLayer(2 * dp, 0, 0, 0xB0000000);
        cell.setColor(0x26FFFFFF);
        digit.setColor(0xD9FFFFFF);
        digit.setTextAlign(Paint.Align.CENTER);
        digit.setTypeface(font);
        digit.setShadowLayer(4 * dp, 0, dp, 0xC0000000);
        fill.setColor(0x80FBBC04);
        pill.setColor(0xE6202124);
        pillText.setColor(0xFFFFFFFF);
        pillText.setTextSize(15 * dp);
        pillText.setTextAlign(Paint.Align.CENTER);
        pillText.setTypeface(font);
        dim.setColor(0x73000000);
        mark.setColor(0xFFFBBC04);
    }

    /** A full-screen window above the home screen; it lets touches through unless setting up. */
    static WindowManager.LayoutParams layoutParams(boolean touchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setFitInsetsTypes(0);
        return lp;
    }

    boolean isSettingUp() {
        return settingUp;
    }

    /** Starts (or stops) asking for the 1, 9 and 0 apps. */
    void setSettingUp(boolean on) {
        settingUp = on;
        step = 0;
        problem = null;
        invalidate();
    }

    /**
     * A practice swipe counted: lights its cell and pairs it with the one before, like the real
     * thing. Returns true when it completed a number.
     */
    boolean swiped(int d) {
        long now = SystemClock.uptimeMillis();
        boolean second = first >= 0 && now - firstAt <= Force.pairMs(getContext());
        removeCallbacks(forgetFirst);
        if (second) {
            int n = first * 10 + d;
            if (n == 0) n = 100;
            status = first + " " + d + "  =  " + n;
            first = -1;
        } else {
            first = d;
            firstAt = now;
            status = d + "  _";
            postDelayed(forgetFirst, Force.pairMs(getContext()));
        }
        flash = d;
        flashUntil = now + FLASH_MS;
        invalidate();
        postInvalidateDelayed(FLASH_MS + 50);
        return second;
    }

    /** Forgets a half-entered practice number. */
    void clear() {
        removeCallbacks(forgetFirst);
        forgetFirst.run();
        flash = -1;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!settingUp) return false;
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
        if (cancel.contains(e.getRawX(), e.getRawY())) {
            settingUp = false;
            onSetUp.accept(null);
            return true;
        }
        Rect b = screen();
        taps[step * 2] = e.getRawX() / b.width();
        taps[step * 2 + 1] = e.getRawY() / b.height();
        problem = null;
        if (step == 1 && (taps[2] - taps[0]) * b.width() < 24 * dp
                || step == 1 && (taps[3] - taps[1]) * b.height() < 24 * dp) {
            problem = "9 goes below and right of 1. Tap 1 again";
            step = 0;
        } else if (step == 2 && GestureService.digitFor(e.getRawX(), e.getRawY(), b.width(), b.height(),
                new float[]{taps[0], taps[1], taps[2], taps[3], -1, -1}) > 0) {
            problem = "0 goes outside the 3x3. Tap 0 again";
        } else if (step == 2) {
            settingUp = false;
            onSetUp.accept(taps.clone());
        } else {
            step++;
        }
        invalidate();
        return true;
    }

    private Rect screen() {
        return getContext().getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
    }

    @Override
    protected void onDraw(Canvas c) {
        // Drawn in screen coordinates, the ones GestureService.digitFor uses.
        Rect b = screen();
        float w = b.width(), h = b.height();
        getLocationOnScreen(loc);
        c.save();
        c.translate(-loc[0], -loc[1]);
        if (settingUp) {
            drawSetUp(c, w, h);
        } else {
            drawKeypad(c, w, h);
        }
        c.restore();
    }

    private void drawKeypad(Canvas c, float w, float h) {
        float[] k = Force.keypad(getContext());
        float x1 = k[0] * w, y1 = k[1] * h, cw = (k[2] - k[0]) * w / 2, ch = (k[3] - k[1]) * h / 2;
        digit.setTextSize(Math.min(cw, ch) * 0.5f);
        boolean lit = flash >= 0 && SystemClock.uptimeMillis() < flashUntil;
        for (int d = 0; d <= 9; d++) {
            float cx = d == 0 ? k[4] * w : x1 + (d - 1) % 3 * cw;
            float cy = d == 0 ? k[5] * h : y1 + (d - 1) / 3 * ch;
            r.set(cx - cw / 2 + 2 * dp, cy - ch / 2 + 2 * dp, cx + cw / 2 - 2 * dp, cy + ch / 2 - 2 * dp);
            c.drawRoundRect(r, 14 * dp, 14 * dp, lit && flash == d ? fill : cell);
            c.drawRoundRect(r, 14 * dp, 14 * dp, line);
            c.drawText(String.valueOf(d), cx, cy - (digit.descent() + digit.ascent()) / 2, digit);
        }
        float top = Math.min(y1, k[5] * h) - ch / 2;
        drawPill(c, status, w / 2, top - 28 * dp > 72 * dp ? top - 28 * dp : y1 + 2.5f * ch + 28 * dp);
    }

    private void drawSetUp(Canvas c, float w, float h) {
        c.drawRect(0, 0, w, h, dim);
        for (int i = 0; i < step; i++) {
            c.drawCircle(taps[i * 2] * w, taps[i * 2 + 1] * h, 10 * dp, mark);
            c.drawText(i == 0 ? "1" : "9", taps[i * 2] * w, taps[i * 2 + 1] * h - 18 * dp, pillText);
        }
        drawPill(c, ASK[step], w / 2, h * 0.18f);
        if (problem != null) drawPill(c, problem, w / 2, h * 0.18f + 48 * dp);
        drawPill(c, "Cancel", w / 2, h * 0.18f + (problem != null ? 104 : 56) * dp);
        cancel.set(r);
        cancel.inset(-8 * dp, -8 * dp);
    }

    private void drawPill(Canvas c, String text, float cx, float cy) {
        float tw = pillText.measureText(text);
        r.set(cx - tw / 2 - 16 * dp, cy - 18 * dp, cx + tw / 2 + 16 * dp, cy + 18 * dp);
        c.drawRoundRect(r, 18 * dp, 18 * dp, pill);
        c.drawText(text, cx, cy - (pillText.descent() + pillText.ascent()) / 2, pillText);
    }
}
