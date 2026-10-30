package app.notes;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

/**
 * The trick's own settings and status. Nothing in the app leads here: it opens only when
 * MainActivity.SECRET_CODE is typed into the search box.
 */
public class HiddenSettingsActivity extends AppCompatActivity {

    private static final String[] VIBRATION_NAMES = {"Off", "Light", "Medium", "Strong"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView trick, service, number, pending, line, overlayNote;
    private LinearLayout overlayRow;
    private MaterialSwitch overlaySwitch;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRecentsScreenshotEnabled(false);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.color(this, R.color.keep_bg));
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), 0, dp(16), 0);
        ImageButton back = Ui.iconButton(this, R.drawable.ic_arrow_back, "Navigate up", Ui.textColor(this));
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = Ui.text(this, "Advanced", 22, Ui.textColor(this), R.font.google_sans_regular);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        tl.setMarginStart(dp(12));
        top.addView(title, tl);
        root.addView(top, new LinearLayout.LayoutParams(Ui.MATCH, dp(64)));

        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        root.addView(scroll, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        // Prime: the one thing to tap before performing.
        MaterialButton prime = new MaterialButton(this);
        prime.setText("Prime");
        prime.setAllCaps(false);
        prime.setOnClickListener(v -> prime());
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.MATCH, dp(56));
        pl.setMargins(dp(24), dp(8), dp(24), 0);
        content.addView(prime, pl);
        TextView primeNote = Ui.text(this, "Clears the last number, arms the next two swipes and goes to "
                        + "the first page of your home screen. Once the number has been revealed, the trick "
                        + "switches itself off until you prime it again.", 14, Ui.muted(this),
                R.font.google_sans_text_regular);
        primeNote.setPadding(dp(24), dp(12), dp(24), dp(8));
        content.addView(primeNote);

        header("Status");
        trick = value(row("Trick", null));
        service = value(row("Swipe service", v -> startActivity(
                new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        number = value(row("Number", null));
        pending = value(row("First digit", null));
        line = value(row("Celebrities", null));

        header("Practice");
        overlayRow = new LinearLayout(this);
        overlayRow.setGravity(Gravity.CENTER_VERTICAL);
        overlayRow.setPadding(dp(24), dp(12), dp(16), dp(12));
        overlayRow.setMinimumHeight(dp(56));
        overlayRow.setBackground(Ui.rippleRounded(this, 0));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, "Show the keypad on the home screen", 16, Ui.textColor(this),
                R.font.google_sans_text_regular));
        overlayNote = value(texts);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        tp.setMarginEnd(dp(16));
        overlayRow.addView(texts, tp);
        overlaySwitch = new MaterialSwitch(this);
        overlaySwitch.setClickable(false);
        overlayRow.addView(overlaySwitch);
        overlayRow.setOnClickListener(v -> {
            if (Force.armed(this)) return;
            Force.setOverlay(this, !Force.overlay(this));
            updateStatus();
        });
        content.addView(overlayRow, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        header("Feedback");
        TextView vib = value(row("Vibration", null));
        vib.setText(VIBRATION_NAMES[Force.vibration(this)]);
        ((View) vib.getParent()).setOnClickListener(v -> choose("Vibration", VIBRATION_NAMES,
                Force.vibration(this), which -> {
                    Force.setInt(this, "vibration", which);
                    vib.setText(VIBRATION_NAMES[which]);
                    Force.buzz(this, false);
                }));
        row("Test vibration (one digit, then a whole number)", v -> {
            Force.buzz(this, false);
            handler.postDelayed(() -> Force.buzz(this, Force.doubleBuzz(this)), 700);
        });
        toggle("Double buzz when the number is complete", Force.doubleBuzz(this),
                on -> Force.setBool(this, "doubleBuzz", on));

        header("Timing");
        String[] pairs = {"20 seconds", "45 seconds", "90 seconds"};
        choice("Time allowed between the two swipes", pairs, Force.PAIR_CHOICES, Force.pairMs(this), "pair");

        header("Actions");
        row("Set the number by hand", v -> askNumber());

        TextView foot = Ui.text(this, "Opened by typing " + MainActivity.SECRET_CODE
                        + " in the search box. Leaving the app closes this screen.", 13, Ui.muted(this),
                R.font.google_sans_text_regular);
        foot.setPadding(dp(24), dp(24), dp(24), dp(24));
        content.addView(foot);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            root.setPadding(sys.left, sys.top, sys.right, 0);
            scroll.setPadding(0, 0, 0, sys.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void prime() {
        Force.prime(this);
        updateStatus();
        if (!GestureService.goToFirstPage()) {
            Snackbar.make(content, "Primed. The swipe service is off: enter the number with two swipes "
                    + "on the notes list", Snackbar.LENGTH_LONG).show();
        }
    }

    private void updateStatus() {
        boolean armed = Force.armed(this);
        boolean serviceOn = serviceEnabled();
        trick.setText(armed ? "Primed: the next two swipes enter the number"
                : Force.current(this) >= 1 ? "Off since the number was revealed. Tap Prime before performing"
                : "Not primed. Tap Prime before performing");
        service.setText(serviceOn ? "On" : "Off: two swipes on the notes list instead");
        long now = System.currentTimeMillis();
        int last = Force.current(this);
        if (last < 0) {
            number.setText("None yet");
        } else {
            number.setText(last + ", entered " + ago(now - Force.capturedAt(this)));
        }
        int first = Force.pendingDigit(this);
        if (first < 0) {
            pending.setText("None");
        } else {
            long left = Force.pairMs(this) - (now - getSharedPreferences("f", MODE_PRIVATE).getLong("d1t", now));
            pending.setText(first + ", waiting for the second swipe (" + Math.max(0, left / 1000) + " s left)");
        }
        int l = Celebs.currentLine(this);
        String forced = Celebs.names(this).get(0);
        line.setText(l < 0 ? forced + " goes on a random line" : forced + " is on line " + l);
        overlaySwitch.setChecked(Force.overlay(this));
        overlaySwitch.setEnabled(!armed);
        overlayRow.setEnabled(!armed);
        overlayRow.setAlpha(armed ? 0.5f : 1f);
        overlayNote.setText(armed ? "Off while the trick is primed"
                : !serviceOn ? "Needs the swipe service"
                : "Draws the keypad over your home screen. Each swipe that counts lights up its "
                        + "cell and buzzes, without entering a number");
    }

    private static String ago(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + " s ago";
        if (s < 3600) return (s / 60) + " min ago";
        return (s / 3600) + " h ago";
    }

    private boolean serviceEnabled() {
        AccessibilityManager am = getSystemService(AccessibilityManager.class);
        ComponentName me = new ComponentName(this, GestureService.class);
        for (AccessibilityServiceInfo info :
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null
                    && me.getClassName().equals(info.getResolveInfo().serviceInfo.name)
                    && me.getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)) {
                return true;
            }
        }
        return false;
    }

    private void askNumber() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setHint("1 to 100");
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(e);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Number")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Set", (d, w) -> {
                    try {
                        int n = Integer.parseInt(e.getText().toString().trim());
                        if (n >= 1 && n <= 100) {
                            Force.setManually(this, n);
                            updateStatus();
                            return;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                    Snackbar.make(content, "Enter a number from 1 to 100", Snackbar.LENGTH_SHORT).show();
                })
                .show();
    }

    // Building blocks --------------------------------------------------------------------------

    private interface Choice {
        void on(int which);
    }

    private interface Toggle {
        void on(boolean value);
    }

    private void header(String s) {
        TextView t = Ui.text(this, s, 14, Ui.color(this, R.color.keep_primary), R.font.google_sans_medium);
        t.setPadding(dp(24), dp(24), dp(24), dp(8));
        content.addView(t);
    }

    /** A row with a title and a value line underneath. */
    private LinearLayout row(String title, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(24), dp(12), dp(24), dp(12));
        r.setMinimumHeight(dp(56));
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(Ui.text(this, title, 16, Ui.textColor(this), R.font.google_sans_text_regular));
        if (click != null) {
            r.setBackground(Ui.rippleRounded(this, 0));
            r.setOnClickListener(click);
        }
        content.addView(r, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        return r;
    }

    private TextView value(LinearLayout row) {
        TextView v = Ui.text(this, "", 14, Ui.muted(this), R.font.google_sans_text_regular);
        v.setPadding(0, dp(2), 0, 0);
        row.addView(v);
        return v;
    }

    private void choice(String title, String[] names, long[] values, long current, String key) {
        int index = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == current) index = i;
        TextView v = value(row(title, null));
        v.setText(names[index]);
        final int selected = index;
        ((View) v.getParent()).setBackground(Ui.rippleRounded(this, 0));
        ((View) v.getParent()).setOnClickListener(x -> choose(title, names, selected, which -> {
            Force.setLong(this, key, values[which]);
            v.setText(names[which]);
            recreate();
        }));
    }

    private void choose(String title, String[] names, int selected, Choice cb) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setSingleChoiceItems(names, selected, (d, which) -> {
                    cb.on(which);
                    d.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggle(String title, boolean on, Toggle cb) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(24), dp(8), dp(16), dp(8));
        r.setMinimumHeight(dp(56));
        r.setBackground(Ui.rippleRounded(this, 0));
        TextView t = Ui.text(this, title, 16, Ui.textColor(this), R.font.google_sans_text_regular);
        r.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setChecked(on);
        sw.setClickable(false);
        r.addView(sw);
        r.setOnClickListener(v -> {
            sw.toggle();
            cb.on(sw.isChecked());
        });
        content.addView(r, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
