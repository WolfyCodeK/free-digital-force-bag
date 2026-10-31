package app.notes;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

/**
 * Shown for a moment after an unlock that follows an automatic Prime: a picture of the home
 * screen's first page, taken earlier, while the launcher jumps back to that page underneath
 * (see GestureService), so the jump itself is never seen. Above the status bar line nothing is
 * drawn, so the live status bar shows through. It takes every touch while it is up.
 */
final class FirstPageCover extends View {

    private final Bitmap picture;
    private final int top;
    private final int[] loc = new int[2];
    private final Rect src = new Rect();
    private final Rect dst = new Rect();

    FirstPageCover(Context c, Bitmap picture) {
        super(c);
        this.picture = picture;
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        top = id > 0 ? c.getResources().getDimensionPixelSize(id) : 0;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // Nothing underneath (the page that is about to slide away) may be tapped meanwhile.
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        // The picture is the whole screen; draw it where it was taken.
        getLocationOnScreen(loc);
        src.set(0, top, picture.getWidth(), picture.getHeight());
        dst.set(src);
        dst.offset(-loc[0], -loc[1]);
        c.drawBitmap(picture, src, dst, null);
    }
}
