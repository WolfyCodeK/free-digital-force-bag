package app.notes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.core.content.res.ResourcesCompat;

/**
 * Practice aid, switched on in the hidden settings: draws the invisible keypad over the home
 * screen and lights up the cell of each swipe that counts. It never takes touches, and it is
 * only shown while the trick isn't primed.
 */
final class KeypadOverlay extends View {

    private static final long FLASH_MS = 900;
    private static final String IDLE = "Practice: swipes don't enter a number";

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private final int[] loc = new int[2];
    private final RectF r = new RectF();

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

    KeypadOverlay(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        Typeface font = ResourcesCompat.getFont(c, R.font.google_sans_medium);
        line.setColor(0xD0FFFFFF);
        line.setStrokeWidth(1.5f * dp);
        line.setShadowLayer(2 * dp, 0, 0, 0xB0000000);
        digit.setColor(0xE6FFFFFF);
        digit.setTextSize(56 * dp);
        digit.setTextAlign(Paint.Align.CENTER);
        digit.setTypeface(font);
        digit.setShadowLayer(4 * dp, 0, dp, 0xC0000000);
        fill.setColor(0x66FBBC04);
        pill.setColor(0xD9202124);
        pillText.setColor(0xFFFFFFFF);
        pillText.setTextSize(15 * dp);
        pillText.setTextAlign(Paint.Align.CENTER);
        pillText.setTypeface(font);
    }

    /** A full-screen window that sits above the home screen and lets every touch through. */
    static WindowManager.LayoutParams layoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setFitInsetsTypes(0);
        return lp;
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

    @Override
    protected void onDraw(Canvas c) {
        // The same grid GestureService.digitFor uses: the whole screen, system bars included.
        Rect b = getContext().getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        float w = b.width(), h = b.height(), cw = w / 3f, ch = h / 4f;
        getLocationOnScreen(loc);
        c.save();
        c.translate(-loc[0], -loc[1]);

        if (flash >= 0 && SystemClock.uptimeMillis() < flashUntil) {
            if (flash == 0) {
                r.set(0, 3 * ch, w, h);
            } else {
                int row = (flash - 1) / 3, col = (flash - 1) % 3;
                r.set(col * cw, row * ch, (col + 1) * cw, (row + 1) * ch);
            }
            c.drawRect(r, fill);
        }

        for (int i = 1; i <= 2; i++) c.drawLine(i * cw, 0, i * cw, 3 * ch, line);
        for (int i = 1; i <= 3; i++) c.drawLine(0, i * ch, w, i * ch, line);

        float lift = (digit.descent() + digit.ascent()) / 2;
        for (int d = 1; d <= 9; d++) {
            int row = (d - 1) / 3, col = (d - 1) % 3;
            c.drawText(String.valueOf(d), (col + 0.5f) * cw, (row + 0.5f) * ch - lift, digit);
        }
        c.drawText("0", w / 2, 3.5f * ch - lift, digit);

        float tw = pillText.measureText(status);
        float cy = ch - 36 * dp;
        r.set(w / 2 - tw / 2 - 16 * dp, cy - 18 * dp, w / 2 + tw / 2 + 16 * dp, cy + 18 * dp);
        c.drawRoundRect(r, 18 * dp, 18 * dp, pill);
        c.drawText(status, w / 2, cy - (pillText.descent() + pillText.ascent()) / 2, pillText);
        c.restore();
    }
}
