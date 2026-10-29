package app.notes;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;

/** Keep's Settings screen: display options, reminder defaults and sharing. */
public class SettingsActivity extends AppCompatActivity {

    /** The theme dialog's choices, in Keep's order. */
    private static final String[] THEME_NAMES = {"Light", "Dark", "System default"};
    private static final int[] THEMES = {Prefs.THEME_LIGHT, Prefs.THEME_DARK, Prefs.THEME_SYSTEM};

    private LinearLayout root, content;
    private TextView themeSummary;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.color(this, R.color.keep_bg));
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(4), 0);
        ImageButton back = Ui.iconButton(this, R.drawable.ic_arrow_back, "Navigate up", Ui.textColor(this));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView title = Ui.text(this, "Settings", 22, Ui.textColor(this), R.font.google_sans_regular);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setAccessibilityHeading(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        tl.setMarginStart(dp(20));
        bar.addView(title, tl);
        root.addView(bar, new LinearLayout.LayoutParams(Ui.MATCH, dp(64)));

        NestedScrollView scroll = new NestedScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        header("Display options");
        switchRow("Add new items to bottom", "addToBottom", Prefs.addToBottom(this));
        switchRow(getString(R.string.move_checked_bottom), "tickedToBottom", Prefs.moveTickedToBottom(this));
        switchRow("Display rich link previews", "richLinks", Prefs.richLinks(this));
        LinearLayout theme = row();
        themeSummary = labels(theme, "Theme", themeName(Prefs.theme(this)));
        theme.setOnClickListener(v -> chooseTheme());

        header("Reminder defaults");
        timeRow("Morning", "morning");
        timeRow("Afternoon", "afternoon");
        timeRow("Evening", "evening");

        header("Sharing");
        switchRow("Enable sharing", "sharing", Prefs.sharing(this));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            root.setPadding(sys.left, sys.top, sys.right, 0);
            content.setPadding(0, 0, 0, sys.bottom + dp(16));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    // Rows -------------------------------------------------------------------------------------

    private void header(String text) {
        TextView t = Ui.text(this, text, 14, Ui.color(this, R.color.keep_primary), R.font.google_sans_medium);
        t.setPadding(dp(24), dp(24), dp(24), dp(8));
        t.setAccessibilityHeading(true);
        content.addView(t, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
    }

    /** An empty full-width row, already added to the list. */
    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(24), dp(10), dp(24), dp(10));
        r.setMinimumHeight(dp(56));
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.rippleColor(this)), null,
                new ColorDrawable(Color.WHITE)));
        content.addView(r, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        return r;
    }

    /** Adds the title and optional summary to a row; returns the summary, if any. */
    private TextView labels(LinearLayout r, String title, String summary) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, title, 16, Ui.textColor(this), R.font.google_sans_text_regular));
        TextView s = null;
        if (summary != null) {
            s = Ui.text(this, summary, 14, Ui.muted(this), R.font.google_sans_text_regular);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            sl.topMargin = dp(2);
            col.addView(s, sl);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        return s;
    }

    /** A row with a switch at its end. The whole row toggles it, like Android's own settings. */
    private void switchRow(String title, String key, boolean on) {
        LinearLayout r = row();
        labels(r, title, null);
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setChecked(on);
        sw.setClickable(false);
        sw.setFocusable(false);
        sw.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.setMarginStart(dp(16));
        r.addView(sw, lp);
        r.setOnClickListener(v -> {
            sw.toggle();
            Prefs.setBool(this, key, sw.isChecked());
        });
        r.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Switch");
                info.setCheckable(true);
                info.setChecked(sw.isChecked());
            }
        });
    }

    /** A reminder default: its name, and its time on the right. */
    private void timeRow(String title, String key) {
        LinearLayout r = row();
        labels(r, title, null);
        TextView value = Ui.text(this, formatTime(minutes(key)), 14, Ui.muted(this),
                R.font.google_sans_text_regular);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.setMarginStart(dp(16));
        r.addView(value, lp);
        r.setOnClickListener(v -> pickTime(key, value));
        // A picker that was open when the screen was recreated has lost its listener.
        Object open = getSupportFragmentManager().findFragmentByTag(pickerTag(key));
        if (open instanceof MaterialTimePicker) listen((MaterialTimePicker) open, key, value);
    }

    // Theme ------------------------------------------------------------------------------------

    private static String themeName(int theme) {
        return theme == Prefs.THEME_LIGHT ? "Light" : theme == Prefs.THEME_DARK ? "Dark" : "System default";
    }

    private void chooseTheme() {
        int current = Prefs.theme(this);
        int checked = THEMES.length - 1;
        for (int i = 0; i < THEMES.length; i++) {
            if (THEMES[i] == current) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose theme")
                .setSingleChoiceItems(THEME_NAMES, checked, (d, which) -> {
                    d.dismiss();
                    int t = THEMES[which];
                    if (t == Prefs.theme(this)) return;
                    // Not every change recreates this screen (e.g. Light while the system is light).
                    themeSummary.setText(themeName(t));
                    Prefs.setTheme(this, t);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Reminder times ---------------------------------------------------------------------------

    private int minutes(String key) {
        switch (key) {
            case "morning":
                return Prefs.morning(this);
            case "afternoon":
                return Prefs.afternoon(this);
            default:
                return Prefs.evening(this);
        }
    }

    private String formatTime(int minutes) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minutes / 60);
        c.set(Calendar.MINUTE, minutes % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return DateFormat.getTimeFormat(this).format(c.getTime());
    }

    private static String pickerTag(String key) {
        return "time_" + key;
    }

    private void pickTime(String key, TextView value) {
        String tag = pickerTag(key);
        if (getSupportFragmentManager().isStateSaved()
                || getSupportFragmentManager().findFragmentByTag(tag) != null) return;
        int m = minutes(key);
        MaterialTimePicker p = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(m / 60)
                .setMinute(m % 60)
                .build();
        listen(p, key, value);
        p.show(getSupportFragmentManager(), tag);
    }

    private void listen(MaterialTimePicker p, String key, TextView value) {
        p.addOnPositiveButtonClickListener(x -> {
            int picked = p.getHour() * 60 + p.getMinute();
            Prefs.setTime(this, key, picked);
            value.setText(formatTime(picked));
        });
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
