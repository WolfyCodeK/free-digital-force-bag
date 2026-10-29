package app.notes;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

/** Small helpers shared by every screen. */
final class Ui {

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    /** Keep's note colours, in the order of its colour picker. */
    static final String[] COLOR_NAMES = {"Default", "Coral", "Peach", "Sand", "Mint", "Sage", "Fog",
            "Storm", "Dusk", "Blossom", "Clay", "Chalk"};
    private static final int[] LIGHT = {0, 0xFFFAAFA8, 0xFFF39F76, 0xFFFFF8B8, 0xFFE2F6D3,
            0xFFB4DDD3, 0xFFD4E4ED, 0xFFAECCDC, 0xFFD3BFDB, 0xFFF6E2DD, 0xFFE9E3D4, 0xFFEFEFF1};
    private static final int[] DARK = {0, 0xFF77172E, 0xFF692B17, 0xFF7C4A03, 0xFF264D3B,
            0xFF0C625D, 0xFF256377, 0xFF284255, 0xFF472E5B, 0xFF6C394F, 0xFF4B443A, 0xFF232427};

    private Ui() {}

    static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static int color(Context c, int res) {
        return ContextCompat.getColor(c, res);
    }

    /** The fill of a note in the given colour (0 is the default note colour). */
    static int noteColor(Context c, int index) {
        if (index <= 0 || index >= LIGHT.length) return color(c, R.color.keep_card);
        return night(c) ? DARK[index] : LIGHT[index];
    }

    /** The background behind a note while editing it. */
    static int editorColor(Context c, int index) {
        return noteColor(c, index);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Typeface font(Context c, int res) {
        Typeface t = ResourcesCompat.getFont(c, res);
        return t != null ? t : Typeface.DEFAULT;
    }

    static GradientDrawable rounded(int fill, float radiusPx, int stroke, float strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (stroke != 0 && strokePx > 0) g.setStroke(Math.round(strokePx), stroke);
        return g;
    }

    static GradientDrawable circle(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        return g;
    }

    static int rippleColor(Context c) {
        return night(c) ? 0x33FFFFFF : 0x1F000000;
    }

    /** A ripple over the given background, clipped to it. */
    static Drawable ripple(Context c, Drawable content) {
        GradientDrawable mask = new GradientDrawable();
        if (content instanceof GradientDrawable) {
            mask = (GradientDrawable) content.getConstantState().newDrawable().mutate();
            mask.setColor(Color.WHITE);
        } else {
            mask.setColor(Color.WHITE);
        }
        return new RippleDrawable(ColorStateList.valueOf(rippleColor(c)), content, mask);
    }

    /** A rounded ripple with no background of its own. */
    static Drawable rippleRounded(Context c, float radiusPx) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor(c)), null,
                rounded(Color.WHITE, radiusPx, 0, 0));
    }

    static Drawable borderless(Context c) {
        TypedArray a = c.obtainStyledAttributes(
                new int[]{android.R.attr.selectableItemBackgroundBorderless});
        Drawable d = a.getDrawable(0);
        a.recycle();
        return d;
    }

    static ImageButton iconButton(Context c, int icon, String desc, int tint) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(tint));
        b.setBackground(borderless(c));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setContentDescription(desc);
        b.setTooltipText(desc);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)));
        return b;
    }

    static ImageView icon(Context c, int icon, int tint, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(icon);
        v.setImageTintList(ColorStateList.valueOf(tint));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    static TextView text(Context c, CharSequence s, float sp, int color, int font) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(font(c, font));
        t.setIncludeFontPadding(true);
        return t;
    }

    /** A full-width row: icon, then label. Used by bottom sheets and menus. */
    static LinearLayout row(Context c, int icon, String label, int tint, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 24), 0, dp(c, 24), 0);
        r.setMinimumHeight(dp(c, 52));
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor(c)), null,
                new android.graphics.drawable.ColorDrawable(Color.WHITE)));
        if (icon != 0) {
            ImageView iv = icon(c, icon, tint, 24);
            r.addView(iv);
        }
        TextView t = text(c, label, 16, tint, R.font.google_sans_text_regular);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        lp.setMarginStart(icon != 0 ? dp(c, 20) : 0);
        r.addView(t, lp);
        r.setOnClickListener(click);
        r.setLayoutParams(new LinearLayout.LayoutParams(MATCH, WRAP));
        return r;
    }

    static int muted(Context c) {
        return color(c, R.color.keep_muted);
    }

    static int textColor(Context c) {
        return color(c, R.color.keep_text);
    }
}
