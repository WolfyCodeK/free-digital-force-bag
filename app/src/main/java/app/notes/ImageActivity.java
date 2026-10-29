package app.notes;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

/** One of a note's pictures, full screen on black. Tap to show or hide the bars. */
public class ImageActivity extends AppCompatActivity {

    /** int: index into the note's attachments. The note comes in {@link EditorActivity#EXTRA_ID}. */
    static final String EXTRA_INDEX = "index";

    private Store store;
    private Note note;
    private Note.Attachment attachment;
    private LinearLayout bar;
    private WindowInsetsControllerCompat systemBars;
    private boolean chrome = true;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        store = Store.get(this);
        long id = getIntent().getLongExtra(EditorActivity.EXTRA_ID, -1);
        int index = getIntent().getIntExtra(EXTRA_INDEX, 0);
        note = id >= 0 ? store.get(id) : null;
        if (note == null || index < 0 || index >= note.attachments.size()) {
            finish();
            return;
        }
        attachment = note.attachments.get(index);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(attachment.kind == Note.DRAWING ? "Drawing" : "Image");
        image.setOnClickListener(v -> showChrome(!chrome));
        root.addView(image, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        Media.load(image, store.media(attachment.file), getResources().getDisplayMetrics().widthPixels);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(8), dp(4), dp(16));
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x80000000, 0x00000000}));
        ImageButton back = whiteButton(R.drawable.ic_arrow_back, "Navigate up");
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        ImageButton delete = whiteButton(R.drawable.ic_delete, "Delete");
        delete.setOnClickListener(v -> confirmDelete());
        // Pictures of a note in Deleted can be looked at but not changed.
        if (note.trashed) delete.setVisibility(View.GONE);
        bar.addView(delete);
        root.addView(bar, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.TOP));

        // White status and navigation bar icons on the black background.
        systemBars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        systemBars.setAppearanceLightStatusBars(false);
        systemBars.setAppearanceLightNavigationBars(false);
        systemBars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        // Insets ignoring visibility, so the picture doesn't jump when the bars are hidden.
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars());
            root.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (note == null) return;
        if (store.get(note.id) == null || !note.attachments.contains(attachment)) finish();
    }

    private ImageButton whiteButton(int icon, String desc) {
        ImageButton b = Ui.iconButton(this, icon, desc, Color.WHITE);
        // A light, borderless ripple that shows up on black.
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, null));
        return b;
    }

    /** Shows or hides the top bar together with the system bars. */
    private void showChrome(boolean show) {
        chrome = show;
        bar.animate().cancel();
        if (show) {
            bar.setVisibility(View.VISIBLE);
            bar.animate().alpha(1f).setDuration(150).start();
            systemBars.show(WindowInsetsCompat.Type.systemBars());
        } else {
            bar.animate().alpha(0f).setDuration(150).withEndAction(() -> bar.setVisibility(View.GONE)).start();
            systemBars.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(attachment.kind == Note.DRAWING ? "Delete drawing?" : "Delete image?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    File f = store.media(attachment.file);
                    note.attachments.remove(attachment);
                    f.delete();
                    Media.forget(f);
                    note.edited = Math.max(System.currentTimeMillis(), note.created + 1);
                    store.changed();
                    finish();
                })
                .show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
