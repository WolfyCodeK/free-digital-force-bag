package app.notes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Simple line icons drawn on a 24x24 grid. */
final class IconView extends View {
    static final int MENU = 0, LIST = 1, SORT = 2, PLUS = 3, BACK = 4, DOTS = 5, AUDIO = 6, BOX = 7;

    private final int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    IconView(Context c, int type, int color) {
        super(c);
        this.type = type;
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onDraw(Canvas c) {
        float u = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * u) / 2f, (getHeight() - 24 * u) / 2f);
        c.scale(u, u);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        switch (type) {
            case MENU:
                c.drawLine(3, 6, 21, 6, p);
                c.drawLine(3, 12, 21, 12, p);
                c.drawLine(3, 18, 21, 18, p);
                break;
            case LIST:
                c.drawRoundRect(new RectF(3, 4, 21, 10.5f), 2, 2, p);
                c.drawRoundRect(new RectF(3, 13.5f, 21, 20), 2, 2, p);
                break;
            case SORT:
                c.drawLine(8, 19, 8, 5, p);
                c.drawLine(8, 5, 4.5f, 8.5f, p);
                c.drawLine(8, 5, 11.5f, 8.5f, p);
                c.drawLine(16, 5, 16, 19, p);
                c.drawLine(16, 19, 12.5f, 15.5f, p);
                c.drawLine(16, 19, 19.5f, 15.5f, p);
                break;
            case PLUS:
                p.setStrokeWidth(2.4f);
                c.drawLine(12, 4.5f, 12, 19.5f, p);
                c.drawLine(4.5f, 12, 19.5f, 12, p);
                break;
            case BACK:
                c.drawLine(20, 12, 4.5f, 12, p);
                c.drawLine(4.5f, 12, 11, 5.5f, p);
                c.drawLine(4.5f, 12, 11, 18.5f, p);
                break;
            case DOTS:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 5, 1.9f, p);
                c.drawCircle(12, 12, 1.9f, p);
                c.drawCircle(12, 19, 1.9f, p);
                break;
            case AUDIO:
                p.setStrokeWidth(2.2f);
                c.drawLine(5, 12, 5, 16, p);
                c.drawLine(9, 9, 9, 19, p);
                c.drawLine(13, 11, 13, 17, p);
                p.setStyle(Paint.Style.FILL);
                Path s = new Path();
                float x = 18.5f, y = 6f, r = 4.2f;
                s.moveTo(x, y - r);
                s.quadTo(x, y, x + r, y);
                s.quadTo(x, y, x, y + r);
                s.quadTo(x, y, x - r, y);
                s.quadTo(x, y, x, y - r);
                c.drawPath(s, p);
                break;
            case BOX:
                c.drawRoundRect(new RectF(4, 4, 20, 20), 3, 3, p);
                c.drawLine(12, 8.5f, 12, 15.5f, p);
                c.drawLine(8.5f, 12, 15.5f, 12, p);
                break;
        }
        c.restore();
    }
}
