package app.notes;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

/**
 * Google's Help screen as it looks with no connection. It never goes online: "Try again" only
 * pretends to load, and feedback is never sent anywhere.
 */
public class HelpActivity extends AppCompatActivity {

    private static final int MENU_PLAY_STORE = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable settle = this::showOffline;

    private LinearLayout root, offline, bottom;
    private CircularProgressIndicator progress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.color(this, R.color.keep_bg));
        setContentView(root);

        // Top bar ----------------------------------------------------------------------------
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(4), 0);
        ImageButton back = Ui.iconButton(this, R.drawable.ic_arrow_back, "Navigate up", Ui.textColor(this));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView title = Ui.text(this, "Help", 22, Ui.textColor(this), R.font.google_sans_regular);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setAccessibilityHeading(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        tl.setMarginStart(dp(20));
        bar.addView(title, tl);
        ImageButton more = Ui.iconButton(this, R.drawable.ic_more_vert, "More options", Ui.textColor(this));
        more.setOnClickListener(this::showMenu);
        bar.addView(more);
        root.addView(bar, new LinearLayout.LayoutParams(Ui.MATCH, dp(64)));

        // Offline state, centred in the space between the bars --------------------------------
        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        FrameLayout stage = new FrameLayout(this);
        scroll.addView(stage, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        offline = new LinearLayout(this);
        offline.setOrientation(LinearLayout.VERTICAL);
        offline.setGravity(Gravity.CENTER_HORIZONTAL);
        offline.setPadding(dp(32), dp(24), dp(32), dp(24));
        offline.addView(Ui.icon(this, R.drawable.ic_cloud_off, Ui.muted(this), 64));
        TextView heading = Ui.text(this, "You're offline", 22, Ui.textColor(this), R.font.google_sans_regular);
        heading.setGravity(Gravity.CENTER);
        heading.setAccessibilityHeading(true);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        hl.topMargin = dp(24);
        offline.addView(heading, hl);
        TextView message = Ui.text(this, "Check your connection and try again.", 14, Ui.muted(this),
                R.font.google_sans_text_regular);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        ml.topMargin = dp(8);
        offline.addView(message, ml);
        MaterialButton retry = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        retry.setText("Try again");
        retry.setTypeface(Ui.font(this, R.font.google_sans_medium));
        retry.setOnClickListener(v -> retry());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        bl.topMargin = dp(24);
        offline.addView(retry, bl);
        stage.addView(offline, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER));

        progress = new CircularProgressIndicator(this);
        progress.setIndeterminate(true);
        progress.setIndicatorColor(Ui.color(this, R.color.keep_primary));
        progress.setContentDescription("Loading");
        progress.setVisibility(View.GONE);
        stage.addView(progress, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));

        // Send feedback ------------------------------------------------------------------------
        bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        View divider = new View(this);
        divider.setBackgroundColor(Ui.color(this, R.color.keep_stroke));
        bottom.addView(divider, new LinearLayout.LayoutParams(Ui.MATCH, dp(1)));
        LinearLayout feedback = Ui.row(this, R.drawable.ic_edit, "Send feedback", Ui.textColor(this),
                v -> sendFeedback());
        feedback.setMinimumHeight(dp(56));
        bottom.addView(feedback);
        root.addView(bottom, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            root.setPadding(sys.left, sys.top, sys.right, 0);
            bottom.setPadding(0, 0, 0, sys.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(settle);
        super.onDestroy();
    }

    // Offline state ----------------------------------------------------------------------------

    /** Spins for a moment, then lands on the same offline page: there is never a connection. */
    private void retry() {
        if (progress.getVisibility() == View.VISIBLE) return;
        offline.setVisibility(View.INVISIBLE);
        progress.setVisibility(View.VISIBLE);
        handler.removeCallbacks(settle);
        handler.postDelayed(settle, 1500);
    }

    private void showOffline() {
        progress.setVisibility(View.GONE);
        offline.setVisibility(View.VISIBLE);
    }

    // Menu -------------------------------------------------------------------------------------

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor, Gravity.END);
        m.getMenu().add(0, MENU_PLAY_STORE, 0, "View in Google Play Store");
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_PLAY_STORE) openPlayStore();
            return true;
        });
        m.show();
    }

    private void openPlayStore() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.keep"));
        // Sets App.external first and catches ActivityNotFoundException.
        if (!Sheets.launch(this, i)) snack("Google Play Store isn't available on this device");
    }

    // Feedback ---------------------------------------------------------------------------------

    private void sendFeedback() {
        EditText e = new EditText(this);
        e.setHint("Tell us what prompted this feedback…");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setMinLines(3);
        e.setMaxLines(6);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setTypeface(Ui.font(this, R.font.google_sans_text_regular));
        e.setTextColor(Ui.textColor(this));
        e.setHintTextColor(Ui.muted(this));
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(e, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle("Send feedback")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (dlg, w) ->
                        snack("Thanks! Your feedback will be sent when you're back online."))
                .create();
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
        e.requestFocus();

        // Nothing to send until something is written.
        Button send = d.getButton(DialogInterface.BUTTON_POSITIVE);
        if (send == null) return;
        send.setEnabled(false);
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                send.setEnabled(s.toString().trim().length() > 0);
            }
        });
    }

    private void snack(String msg) {
        Snackbar s = Snackbar.make(root, msg, Snackbar.LENGTH_LONG);
        s.setAnchorView(bottom);
        s.show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
