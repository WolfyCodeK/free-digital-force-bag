package app.notes;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Lays children out left to right, wrapping onto new lines. Used for chips. */
public class Wrap extends ViewGroup {

    private final int hGap, vGap;

    public Wrap(Context c, int hGapPx, int vGapPx) {
        super(c);
        hGap = hGapPx;
        vGap = vGapPx;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int maxW = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
        boolean unbounded = MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED;
        int x = 0, y = 0, lineH = 0, widest = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            measureChild(ch, MeasureSpec.makeMeasureSpec(Math.max(0, maxW), unbounded
                    ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST), hSpec);
            int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
            if (!unbounded && x > 0 && x + w > maxW) {
                x = 0;
                y += lineH + vGap;
                lineH = 0;
            }
            x += w + hGap;
            widest = Math.max(widest, x - hGap);
            lineH = Math.max(lineH, h);
        }
        int height = y + lineH + getPaddingTop() + getPaddingBottom();
        int width = unbounded ? widest + getPaddingLeft() + getPaddingRight()
                : MeasureSpec.getSize(wSpec);
        setMeasuredDimension(width, resolveSize(height, hSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxW = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
            if (x > 0 && x + w > maxW) {
                x = 0;
                y += lineH + vGap;
                lineH = 0;
            }
            int left = getPaddingLeft() + x, top = getPaddingTop() + y;
            ch.layout(left, top, left + w, top + h);
            x += w + hGap;
            lineH = Math.max(lineH, h);
        }
    }
}
