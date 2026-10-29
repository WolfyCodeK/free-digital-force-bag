package app.notes;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A Google Keep look-alike. The "Celebrities" note holds a numbered list of 100 names,
 * with the forced name on the line given by the number ForceService captured.
 * Follows the system light/dark setting.
 *
 * assets/celebrities.txt: line 1 is the forced name, lines 2-100 are the fillers.
 */
public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private int header, pill, bg, card, border, text, muted, fabSmall, fabSmallIcon, fabBig, fabBigIcon;
    private static final int RING = 0xFF3185FF;

    private String forced;
    private final List<String> fillers = new ArrayList<>();
    private final int fallback = 1 + new Random().nextInt(100);
    private boolean inEditor;
    private boolean night;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            header = 0xFF333439; pill = 0xFF38393E; bg = 0xFF1E1F24; card = 0xFF121318;
            border = 0xFF45454D; text = 0xFFE3E2E6; muted = 0xFFC4C6D0;
            fabSmall = 0xFFB0C6FF; fabSmallIcon = 0xFF002C71;
            fabBig = 0xFF2F4577; fabBigIcon = 0xFFD9E2FF;
        } else {
            header = 0xFFF0F0F7; pill = 0xFFE4E5EC; bg = 0xFFF9F9FF; card = 0xFFFFFFFF;
            border = 0xFFC4C6D0; text = 0xFF1B1B1F; muted = 0xFF44474F;
            fabSmall = 0xFFB0C6FF; fabSmallIcon = 0xFF002C71;
            fabBig = 0xFFD9E2FF; fabBigIcon = 0xFF00164E;
        }
        getWindow().setStatusBarColor(header);
        getWindow().setNavigationBarColor(night ? card : bg);
        int flags = 0;
        if (!night) {
            flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        loadNames();
        showGrid();
    }

    @Override
    protected void onStop() {
        super.onStop();
        showGrid();
    }

    @Override
    public void onBackPressed() {
        if (inEditor) {
            showGrid();
        } else {
            super.onBackPressed();
        }
    }

    private void loadNames() {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(getAssets().open("celebrities.txt")))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (forced == null) forced = line;
                else fillers.add(line);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String celebrityList() {
        int n = Force.current(this);
        if (n < 1 || n > 100) n = fallback;
        StringBuilder sb = new StringBuilder();
        int f = 0;
        for (int i = 1; i <= 100; i++) {
            sb.append(i).append(". ").append(i == n ? forced : fillers.get(f++));
            if (i < 100) sb.append('\n');
        }
        return sb.toString();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private GradientDrawable rounded(int fill, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, WRAP, 1f);
    }

    private LinearLayout.LayoutParams box(int wDp, int hDp, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(wDp), dp(hDp));
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private View card(String title, String body, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(rounded(card, 14, border, 1.5f));
        if (!title.isEmpty()) c.addView(tv(title, 18, text, true));
        TextView b = tv(body, 16, text, false);
        b.setMaxLines(10);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(0, title.isEmpty() ? 0 : dp(12), 0, 0);
        c.addView(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(dp(5), dp(5), dp(5), dp(5));
        c.setLayoutParams(lp);
        if (click != null) c.setOnClickListener(click);
        return c;
    }

    private View fab(int sizeDp, float radiusDp, int fill, int iconType, int iconColor, int iconDp) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(rounded(fill, radiusDp, 0, 0));
        f.setElevation(dp(4));
        f.addView(new IconView(this, iconType, iconColor),
                new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER));
        f.setLayoutParams(new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return f;
    }

    private void showGrid() {
        inEditor = false;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setBackgroundColor(header);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(new IconView(this, IconView.MENU, text), box(24, 24, 20, 0, 0, 0));

        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(26), 0, dp(20), 0);
        search.setBackground(rounded(pill, 31, 0, 0));
        TextView hint = tv("Search Keep", 18, muted, false);
        search.addView(hint, weighted());
        search.addView(new IconView(this, IconView.LIST, text), box(24, 24, 0, 0, 0, 0));
        search.addView(new IconView(this, IconView.SORT, text), box(24, 24, 18, 0, 0, 0));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(62), 1f);
        sp.setMargins(dp(14), dp(8), dp(14), dp(8));
        top.addView(search, sp);

        TextView avatar = tv("A", 18, Color.WHITE, false);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable ring = rounded(0xFF5F6368, 21, RING, 3);
        ring.setShape(GradientDrawable.OVAL);
        avatar.setBackground(ring);
        top.addView(avatar, box(42, 42, 0, 0, 16, 0));
        content.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        left.addView(card("Celebrities", celebrityList(), v -> showEditor()));
        left.addView(card("Gift ideas", "Mum - scarf\nDad - book\nSam - headphones", null));
        right.addView(card("", "Milk\nEggs\nBread\nCoffee\nApples", null));
        right.addView(card("To do", "Call dentist\nRenew parking permit\nBook flights", null));
        LinearLayout cols = new LinearLayout(this);
        cols.setPadding(dp(5), dp(5), dp(5), dp(120));
        LinearLayout.LayoutParams lw = new LinearLayout.LayoutParams(0, WRAP, 1f);
        cols.addView(left, lw);
        cols.addView(right, new LinearLayout.LayoutParams(0, WRAP, 1f));
        ScrollView sv = new ScrollView(this);
        sv.addView(cols);
        content.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        root.addView(content, new FrameLayout.LayoutParams(MATCH, MATCH));

        View small = fab(64, 20, fabSmall, IconView.AUDIO, fabSmallIcon, 28);
        FrameLayout.LayoutParams sfp = new FrameLayout.LayoutParams(dp(64), dp(64),
                Gravity.BOTTOM | Gravity.END);
        sfp.setMargins(0, 0, dp(20), dp(52 + 91 + 16));
        root.addView(small, sfp);
        View big = fab(91, 26, fabBig, IconView.PLUS, fabBigIcon, 40);
        FrameLayout.LayoutParams bfp = new FrameLayout.LayoutParams(dp(91), dp(91),
                Gravity.BOTTOM | Gravity.END);
        bfp.setMargins(0, 0, dp(20), dp(52));
        root.addView(big, bfp);

        setContentView(root);
    }

    private void showEditor() {
        inEditor = true;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(card);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), 0, dp(16), 0);
        View back = new IconView(this, IconView.BACK, text);
        back.setOnClickListener(v -> showGrid());
        top.addView(back, box(24, 24, 4, 0, 0, 0));
        top.addView(new View(this), weighted());
        top.addView(new IconView(this, IconView.DOTS, text), box(24, 24, 0, 0, 4, 0));
        root.addView(top, new LinearLayout.LayoutParams(MATCH, dp(64)));

        EditText title = new EditText(this);
        title.setText("Celebrities");
        title.setHint("Title");
        title.setHintTextColor(muted);
        title.setTextSize(22);
        title.setTextColor(text);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setSingleLine(true);
        title.setBackground(null);
        title.setPadding(dp(20), dp(4), dp(20), dp(4));
        root.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));

        EditText body = new EditText(this);
        body.setText(celebrityList());
        body.setTextSize(16);
        body.setTextColor(text);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setGravity(Gravity.TOP);
        body.setBackground(null);
        body.setLineSpacing(dp(4), 1f);
        body.setPadding(dp(20), dp(8), dp(20), dp(24));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(body, new FrameLayout.LayoutParams(MATCH, WRAP));
        root.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(20), 0, dp(20), 0);
        bottom.addView(new IconView(this, IconView.BOX, muted), box(24, 24, 0, 0, 0, 0));
        TextView edited = tv("Edited " + new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date()), 13, muted, false);
        edited.setGravity(Gravity.CENTER);
        bottom.addView(edited, weighted());
        bottom.addView(new IconView(this, IconView.DOTS, muted), box(24, 24, 0, 0, 0, 0));
        root.addView(bottom, new LinearLayout.LayoutParams(MATCH, dp(52)));

        setContentView(root);
    }
}
