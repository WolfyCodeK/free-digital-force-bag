package app.notes;

import android.app.Activity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A Google Keep look-alike. The "Celebrities" note holds a numbered list of 100 names,
 * with the forced name on the line given by the number ForceService captured.
 *
 * assets/celebrities.txt: line 1 is the forced name, lines 2-100 are the fillers.
 */
public class MainActivity extends Activity {

    private static final int INK = 0xFF202124;
    private static final int MUTED = 0xFF5F6368;
    private static final int LINE = 0xFFDADCE0;
    private static final int BAR = 0xFFF1F3F4;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private String forced;
    private final List<String> fillers = new ArrayList<>();
    private final int fallback = 1 + new Random().nextInt(100);
    private boolean inEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, WRAP, 1f);
    }

    private View card(String title, String body, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(rounded(Color.WHITE, 8, LINE));
        c.addView(tv(title, 16, INK, true));
        TextView b = tv(body, 14, INK, false);
        b.setMaxLines(12);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(0, dp(6), 0, 0);
        c.addView(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        c.setLayoutParams(lp);
        if (click != null) c.setOnClickListener(click);
        return c;
    }

    private void showGrid() {
        inEditor = false;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(16), 0, dp(12), 0);
        search.setBackground(rounded(BAR, 28, 0));
        search.addView(tv("☰", 20, MUTED, false));
        TextView hint = tv("Search your notes", 16, MUTED, false);
        hint.setPadding(dp(20), 0, 0, 0);
        search.addView(hint, weighted());
        search.addView(tv("▦", 20, MUTED, false));
        TextView avatar = tv("A", 15, Color.WHITE, false);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(0xFF1A73E8, 14, 0));
        LinearLayout.LayoutParams av = new LinearLayout.LayoutParams(dp(28), dp(28));
        av.setMargins(dp(16), 0, 0, 0);
        search.addView(avatar, av);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, dp(52));
        sp.setMargins(dp(12), dp(8), dp(12), dp(8));
        content.addView(search, sp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        left.addView(card("Celebrities", celebrityList(), v -> showEditor()));
        left.addView(card("Gift ideas", "Mum - scarf\nDad - book\nSam - headphones", null));
        right.addView(card("Groceries", "Milk\nEggs\nBread\nCoffee\nApples", null));
        right.addView(card("To do", "Call dentist\nRenew parking permit\nBook flights", null));
        LinearLayout cols = new LinearLayout(this);
        cols.setPadding(dp(6), 0, dp(6), 0);
        cols.addView(left, weighted());
        cols.addView(right, weighted());
        ScrollView sv = new ScrollView(this);
        sv.addView(cols);
        content.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(BAR);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), 0, dp(96), 0);
        String[] icons = {"☑", "✎", "◉", "▣"};
        for (String s : icons) {
            TextView t = tv(s, 22, MUTED, false);
            t.setGravity(Gravity.CENTER);
            bar.addView(t, weighted());
        }
        content.addView(bar, new LinearLayout.LayoutParams(MATCH, dp(56)));
        root.addView(content, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView fab = tv("+", 34, 0xFF1A73E8, false);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(rounded(Color.WHITE, 16, LINE));
        fab.setElevation(dp(6));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(56), dp(56),
                Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, dp(16), dp(28));
        root.addView(fab, fp);

        setContentView(root);
    }

    private void showEditor() {
        inEditor = true;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), 0, dp(16), 0);
        TextView back = tv("←", 24, MUTED, false);
        back.setOnClickListener(v -> showGrid());
        top.addView(back);
        top.addView(new View(this), weighted());
        top.addView(tv("⋮", 24, MUTED, false));
        root.addView(top, new LinearLayout.LayoutParams(MATCH, dp(56)));

        EditText title = new EditText(this);
        title.setText("Celebrities");
        title.setHint("Title");
        title.setTextSize(22);
        title.setTextColor(INK);
        title.setSingleLine(true);
        title.setBackground(null);
        title.setPadding(dp(20), dp(4), dp(20), dp(4));
        root.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));

        EditText body = new EditText(this);
        body.setText(celebrityList());
        body.setTextSize(16);
        body.setTextColor(INK);
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
        bottom.addView(tv("⊕", 24, MUTED, false));
        TextView edited = tv("Edited just now", 12, MUTED, false);
        edited.setGravity(Gravity.CENTER);
        bottom.addView(edited, weighted());
        bottom.addView(tv("⋮", 24, MUTED, false));
        root.addView(bottom, new LinearLayout.LayoutParams(MATCH, dp(48)));

        setContentView(root);
    }
}
