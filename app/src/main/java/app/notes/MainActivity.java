package app.notes;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Looks like a blank note. Two swipes enter a number 1-100 (see digitFor), then the
 * screen switches to a numbered list of 100 names with the forced name on that line.
 *
 * assets/celebrities.txt: line 1 is the forced name, lines 2-100 are the fillers.
 */
public class MainActivity extends Activity {

    private static final boolean HAPTIC = true;
    private static final int SWIPE_MIN_DP = 40;
    private static final long REVEAL_DELAY_MS = 350;

    private static final int[][] KEYPAD = {
            {1, 2, 3},
            {4, 5, 6},
            {7, 8, 9},
            {0, 0, 0},
    };

    private final List<Integer> digits = new ArrayList<>();
    private String forced;
    private final List<String> fillers = new ArrayList<>();
    private boolean showingList;
    private float downX, downY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadNames();
        showBlank();
    }

    @Override
    protected void onStop() {
        super.onStop();
        showBlank();
    }

    @Override
    public void onBackPressed() {
        if (showingList) {
            showBlank();
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

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.BLACK);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout page(String title) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Color.WHITE);
        col.setPadding(dp(20), dp(48), dp(20), dp(20));
        col.addView(text(title, 28, true));
        return col;
    }

    private void showBlank() {
        showingList = false;
        digits.clear();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        root.addView(page("Notes"), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnTouchListener((v, ev) -> {
            onTouch(v, ev);
            return true;
        });
        setContentView(root);
    }

    private void onTouch(View v, MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
        } else if (ev.getActionMasked() == MotionEvent.ACTION_UP && digits.size() < 2) {
            float dx = ev.getX() - downX, dy = ev.getY() - downY;
            if (Math.hypot(dx, dy) < dp(SWIPE_MIN_DP)) return;
            digits.add(digitFor(downX, downY, v.getWidth(), v.getHeight()));
            buzz();
            if (digits.size() == 2) {
                v.postDelayed(this::showList, REVEAL_DELAY_MS);
            }
        }
    }

    /** Digit for the keypad cell (3 columns x 4 rows, bottom row = 0) containing the touch-down point. */
    private static int digitFor(float x, float y, int w, int h) {
        int col = Math.max(0, Math.min(2, (int) (x / w * 3)));
        int row = Math.max(0, Math.min(3, (int) (y / h * 4)));
        return KEYPAD[row][col];
    }

    private void buzz() {
        if (!HAPTIC) return;
        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vib != null) vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    private void showList() {
        if (digits.size() < 2) return;
        int n = digits.get(0) * 10 + digits.get(1);
        if (n == 0) n = 100;

        StringBuilder sb = new StringBuilder();
        int f = 0;
        for (int i = 1; i <= 100; i++) {
            String name = (i == n) ? forced : fillers.get(f++);
            sb.append(i).append(". ").append(name);
            if (i < 100) sb.append('\n');
        }

        LinearLayout col = page("Celebrities");
        TextView body = text(sb.toString(), 18, false);
        body.setLineSpacing(dp(6), 1f);
        body.setPadding(0, dp(16), 0, 0);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        col.addView(scroll);
        showingList = true;
        setContentView(col);
    }
}
