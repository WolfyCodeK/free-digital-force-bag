package app.notes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * The account picture: the photo in res/drawable-nodpi/profile.(jpg|png|webp) cropped to a
 * circle inside Google's blue ring. Without a photo it shows the account's initial, like Google
 * apps do for accounts without a picture.
 */
public class AvatarView extends View {

    private static final int[] INITIAL_COLORS = {0xFF5C6BC0, 0xFF00897B, 0xFFE8710A, 0xFF8E24AA,
            0xFF1E88E5, 0xFF43A047, 0xFFD81B60, 0xFF6D4C41};

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint photo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap bitmap;
    private boolean showRing = true;
    private float ringDp = 2.5f;
    private float gapDp = 2f;

    public AvatarView(Context c) {
        this(c, null);
    }

    public AvatarView(Context c, AttributeSet a) {
        super(c, a);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Ui.color(c, R.color.keep_ring));
        letter.setColor(0xFFFFFFFF);
        letter.setTextAlign(Paint.Align.CENTER);
        letter.setTypeface(Ui.font(c, R.font.google_sans_regular));
        bitmap = loadPhoto(c);
        String name = Account.name(c);
        fill.setColor(INITIAL_COLORS[Math.abs(name.hashCode()) % INITIAL_COLORS.length]);
        setContentDescription(Account.description(c));
        setClickable(true);
        setFocusable(true);
        setBackground(Ui.borderless(c));
    }

    void setRing(boolean show, float ringDp, float gapDp) {
        this.showRing = show;
        this.ringDp = ringDp;
        this.gapDp = gapDp;
        invalidate();
    }

    static Bitmap loadPhoto(Context c) {
        int id = c.getResources().getIdentifier("profile", "drawable", c.getPackageName());
        if (id == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(c.getResources(), id, o);
        int max = Math.max(o.outWidth, o.outHeight);
        int sample = 1;
        while (max / (sample * 2) >= 256) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeResource(c.getResources(), id, o);
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float size = Math.min(getWidth(), getHeight()) - getPaddingLeft() - getPaddingRight();
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float outer = Math.min(size, 42 * d) / 2f;
        float inner = outer;
        if (showRing) {
            float rw = ringDp * d;
            ring.setStrokeWidth(rw);
            c.drawCircle(cx, cy, outer - rw / 2f, ring);
            inner = outer - rw - gapDp * d;
        }
        if (bitmap != null) {
            BitmapShader s = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float side = Math.min(bitmap.getWidth(), bitmap.getHeight());
            float scale = 2 * inner / side;
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            m.postTranslate(cx - bitmap.getWidth() * scale / 2f, cy - bitmap.getHeight() * scale / 2f);
            s.setLocalMatrix(m);
            photo.setShader(s);
            c.drawCircle(cx, cy, inner, photo);
        } else {
            c.drawCircle(cx, cy, inner, fill);
            String name = Account.name(getContext());
            String initial = name.isEmpty() ? "" : name.substring(0, 1).toUpperCase();
            letter.setTextSize(inner * 1.15f);
            Paint.FontMetrics fm = letter.getFontMetrics();
            c.drawText(initial, cx, cy - (fm.ascent + fm.descent) / 2f, letter);
        }
    }

    /** Name and email shown in the account sheet, from assets/account.txt. */
    static final class Account {
        private static String name, email;

        private Account() {}

        private static void load(Context c) {
            if (name != null) return;
            name = "";
            email = "";
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    c.getAssets().open("account.txt"), StandardCharsets.UTF_8))) {
                String line;
                int i = 0;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (i == 0) name = line;
                    else if (i == 1) email = line;
                    i++;
                }
            } catch (Exception ignored) {
            }
        }

        static String name(Context c) {
            load(c);
            return name;
        }

        static String email(Context c) {
            load(c);
            return email;
        }

        static String description(Context c) {
            load(c);
            if (name.isEmpty() && email.isEmpty()) return "Google Account";
            return "Google Account: " + name + (email.isEmpty() ? "" : " (" + email + ")");
        }
    }
}
