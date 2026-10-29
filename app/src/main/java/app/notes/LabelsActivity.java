package app.notes;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Keep's label screens. Without {@link #EXTRA_NOTES} it is "Edit labels" (create, rename and
 * delete labels); with it, it is the label picker for those notes.
 */
public class LabelsActivity extends AppCompatActivity {

    /** Boolean: open "Edit labels" with the new-label field focused and the keyboard up. */
    static final String EXTRA_CREATE = "create";
    /** long[]: ids of the notes to label. Opens the label picker. */
    static final String EXTRA_NOTES = "notes";

    private Store store;
    private boolean picking;
    private final List<Note> chosen = new ArrayList<>();

    private LinearLayout root, rows;

    // Edit labels
    private EditText createField;
    private ImageButton createLead, createCheck;
    private View createTop, createBottom;
    /** Set while rows are moved or removed, so the focus changes that causes rename nothing. */
    private boolean quiet;

    // Label picker
    private EditText search;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        store = Store.get(this);

        long[] ids = getIntent().getLongArrayExtra(EXTRA_NOTES);
        picking = ids != null;
        if (picking) {
            for (long id : ids) {
                Note n = store.get(id);
                if (n != null && !chosen.contains(n)) chosen.add(n);
            }
            if (chosen.isEmpty()) {
                finish();
                return;
            }
        }
        boolean create = !picking && getIntent().getBooleanExtra(EXTRA_CREATE, false);
        getWindow().setSoftInputMode((create ? WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                : WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        buildUi();
        if (picking) buildPicker();
        else rebuild();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                close();
            }
        });

        if (create) {
            createField.requestFocus();
            showKeyboard(createField);
        }
    }

    // Building ---------------------------------------------------------------------------------

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.color(this, R.color.keep_bg));
        // Takes focus when a field gives it up, so another field doesn't grab it.
        root.setFocusableInTouchMode(true);
        root.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(4), 0);
        ImageButton back = Ui.iconButton(this, R.drawable.ic_arrow_back, "Navigate up", Ui.textColor(this));
        back.setOnClickListener(v -> close());
        bar.addView(back);
        if (picking) {
            search = field("Enter label name");
            search.addTextChangedListener(watcher(this::buildPicker));
            search.setOnEditorActionListener((v, actionId, e) -> {
                if (actionId != EditorInfo.IME_ACTION_DONE) return false;
                String q = search.getText().toString().trim();
                if (!q.isEmpty() && !store.hasLabel(q)) createAndApply(q);
                else hideKeyboard();
                return true;
            });
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, Ui.MATCH, 1f);
            sl.setMarginStart(dp(4));
            bar.addView(search, sl);
        } else {
            TextView title = Ui.text(this, "Edit labels", 22, Ui.textColor(this), R.font.google_sans_regular);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
            tl.setMarginStart(dp(12));
            bar.addView(title, tl);
        }
        root.addView(bar, new LinearLayout.LayoutParams(Ui.MATCH, dp(64)));

        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(16));
        scroll.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        if (!picking) content.addView(createRow(), new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        content.addView(rows, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            root.setPadding(sys.left, sys.top, sys.right, Math.max(sys.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** A borderless single-line field, styled like the editor's. */
    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setTextSize(16);
        e.setTypeface(Ui.font(this, R.font.google_sans_text_regular));
        e.setTextColor(Ui.textColor(this));
        e.setHintTextColor(Ui.muted(this));
        e.setBackground(null);
        e.setHighlightColor(ColorUtils.setAlphaComponent(Ui.color(this, R.color.keep_primary), 0x66));
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setSingleLine(true);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        e.setPadding(dp(12), 0, dp(12), 0);
        return e;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.color(this, R.color.keep_stroke));
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.MATCH, Math.max(1, dp(1))));
        v.setVisibility(View.INVISIBLE);
        return v;
    }

    private static void setIcon(ImageButton b, int icon, String desc) {
        b.setImageResource(icon);
        b.setContentDescription(desc);
        b.setTooltipText(desc);
    }

    // Edit labels: the "Create new label" row --------------------------------------------------

    private View createRow() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        createTop = divider();
        box.addView(createTop);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), 0, dp(8), 0);
        createLead = Ui.iconButton(this, R.drawable.ic_add, "Create new label", Ui.muted(this));
        createField = field("Create new label");
        createCheck = Ui.iconButton(this, R.drawable.ic_check, "Create label", Ui.muted(this));
        row.addView(createLead);
        row.addView(createField, new LinearLayout.LayoutParams(0, Ui.MATCH, 1f));
        row.addView(createCheck);
        box.addView(row, new LinearLayout.LayoutParams(Ui.MATCH, dp(56)));
        createBottom = divider();
        box.addView(createBottom);

        createLead.setOnClickListener(v -> {
            if (createActive()) {
                // Cancel: Keep throws the typed name away.
                createField.setText("");
                createField.clearFocus();
                hideKeyboard();
                updateCreateRow();
            } else {
                createField.requestFocus();
                showKeyboard(createField);
            }
        });
        createCheck.setOnClickListener(v -> createLabel());
        createField.setOnFocusChangeListener((v, has) -> updateCreateRow());
        createField.addTextChangedListener(watcher(this::updateCreateRow));
        createField.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            createLabel();
            return true;
        });
        updateCreateRow();
        return box;
    }

    private boolean createActive() {
        return createField.hasFocus() || createField.length() > 0;
    }

    private void updateCreateRow() {
        boolean active = createActive();
        setIcon(createLead, active ? R.drawable.ic_close : R.drawable.ic_add,
                active ? "Cancel" : "Create new label");
        createCheck.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        int line = createField.hasFocus() ? View.VISIBLE : View.INVISIBLE;
        createTop.setVisibility(line);
        createBottom.setVisibility(line);
    }

    private void createLabel() {
        String name = createField.getText().toString().trim();
        if (name.isEmpty()) return;
        if (store.addLabel(name)) {
            createField.setText("");
            rebuild();
        } else {
            snack("Label already exists");
        }
    }

    // Edit labels: one row per label -----------------------------------------------------------

    /** One label's row in "Edit labels". */
    private final class LabelRow {
        String name;
        LinearLayout box;
        View top, bottom;
        ImageButton lead, trail;
        EditText field;
    }

    /** Recreates the label rows, keeping the focus on the label being edited, if any. */
    private void rebuild() {
        String focusName = null, focusText = null;
        int focusSel = -1;
        View f = getCurrentFocus();
        if (f != null && f.getTag() instanceof LabelRow) {
            LabelRow fr = (LabelRow) f.getTag();
            focusName = fr.name;
            focusText = fr.field.getText().toString();
            focusSel = fr.field.getSelectionEnd();
        }
        quiet = true;
        rows.removeAllViews();
        LabelRow refocus = null;
        for (String l : new ArrayList<>(store.labels())) {
            LabelRow r = labelRow(l);
            rows.addView(r.box, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
            if (l.equals(focusName)) refocus = r;
        }
        if (refocus != null) {
            refocus.field.setText(focusText);
            refocus.field.requestFocus();
            refocus.field.setSelection(Math.max(0, Math.min(focusSel, refocus.field.length())));
        }
        quiet = false;
        if (refocus != null) updateRow(refocus);
    }

    private LabelRow labelRow(String name) {
        LabelRow r = new LabelRow();
        r.name = name;
        r.box = new LinearLayout(this);
        r.box.setOrientation(LinearLayout.VERTICAL);
        r.top = divider();
        r.box.addView(r.top);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), 0, dp(8), 0);
        r.lead = Ui.iconButton(this, R.drawable.ic_label, "Label", Ui.muted(this));
        r.field = field("Enter label name");
        r.field.setText(name);
        r.field.setTag(r);
        r.trail = Ui.iconButton(this, R.drawable.ic_edit, "Rename label", Ui.muted(this));
        row.addView(r.lead);
        row.addView(r.field, new LinearLayout.LayoutParams(0, Ui.MATCH, 1f));
        row.addView(r.trail);
        r.box.addView(row, new LinearLayout.LayoutParams(Ui.MATCH, dp(56)));
        r.bottom = divider();
        r.box.addView(r.bottom);

        r.lead.setOnClickListener(v -> {
            if (r.field.hasFocus()) confirmDelete(r);
            else startEditing(r);
        });
        r.trail.setOnClickListener(v -> {
            if (r.field.hasFocus()) stopEditing(r);
            else startEditing(r);
        });
        r.field.setOnFocusChangeListener((v, has) -> {
            if (!has && !quiet) commit(r);
            updateRow(r);
        });
        r.field.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            stopEditing(r);
            return true;
        });
        updateRow(r);
        return r;
    }

    private void updateRow(LabelRow r) {
        boolean on = r.field.hasFocus();
        setIcon(r.lead, on ? R.drawable.ic_delete : R.drawable.ic_label, on ? "Delete label" : "Label");
        setIcon(r.trail, on ? R.drawable.ic_check : R.drawable.ic_edit, on ? "Rename label" : "Edit label");
        r.top.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        r.bottom.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
    }

    private void startEditing(LabelRow r) {
        r.field.requestFocus();
        r.field.setSelection(r.field.length());
        showKeyboard(r.field);
    }

    /** Ends editing; losing focus renames the label. */
    private void stopEditing(LabelRow r) {
        r.field.clearFocus();
        hideKeyboard();
    }

    /** Renames the label to what was typed, or puts the old name back if that isn't allowed. */
    private void commit(LabelRow r) {
        String typed = r.field.getText().toString().trim();
        if (typed.equals(r.name)) {
            if (!r.field.getText().toString().equals(r.name)) r.field.setText(r.name);
            return;
        }
        boolean taken = !typed.isEmpty() && !typed.equalsIgnoreCase(r.name) && store.hasLabel(typed);
        if (store.renameLabel(r.name, typed)) {
            r.name = typed;
            r.field.setText(typed);
            // Labels are kept sorted; move the row once the focus change has finished.
            rows.post(() -> place(r));
        } else {
            r.field.setText(r.name);
            if (taken) snack("Label already exists");
        }
    }

    /** Moves a renamed label's row to its place in the sorted list. */
    private void place(LabelRow r) {
        int at = rows.indexOfChild(r.box);
        int want = store.labels().indexOf(r.name);
        if (at < 0 || want < 0 || at == want || want >= rows.getChildCount()) return;
        boolean focused = r.field.hasFocus();
        quiet = true;
        rows.removeView(r.box);
        rows.addView(r.box, want);
        if (focused) r.field.requestFocus();
        quiet = false;
    }

    private void confirmDelete(LabelRow r) {
        hideKeyboard();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete label?")
                .setMessage("We’ll delete this label and remove it from all of your Keep notes. "
                        + "Your notes won’t be deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    quiet = true;
                    r.field.clearFocus();
                    quiet = false;
                    store.deleteLabel(r.name);
                    rebuild();
                })
                .show();
    }

    // Label picker -----------------------------------------------------------------------------

    private void buildPicker() {
        rows.removeAllViews();
        String q = search.getText().toString().trim();
        String lower = q.toLowerCase(Locale.ROOT);
        boolean exact = false;
        List<String> shown = new ArrayList<>();
        for (String l : store.labels()) {
            if (l.equalsIgnoreCase(q)) exact = true;
            if (l.toLowerCase(Locale.ROOT).contains(lower)) shown.add(l);
        }
        if (!q.isEmpty() && !exact) rows.addView(createPickRow(q));
        for (String l : shown) rows.addView(pickRow(l));
    }

    private LinearLayout listRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), 0, dp(20), 0);
        row.setMinimumHeight(dp(56));
        row.setBackground(Ui.rippleRounded(this, 0));
        row.setLayoutParams(new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        return row;
    }

    private TextView rowText(String s) {
        TextView t = Ui.text(this, s, 16, Ui.textColor(this), R.font.google_sans_text_regular);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private LinearLayout.LayoutParams rowTextParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        lp.setMarginStart(dp(20));
        lp.setMarginEnd(dp(12));
        return lp;
    }

    private View pickRow(String label) {
        LinearLayout row = listRow();
        row.addView(Ui.icon(this, R.drawable.ic_label, Ui.muted(this), 24));
        row.addView(rowText(label), rowTextParams());
        ImageView box = Ui.icon(this, R.drawable.ic_check_box_outline_blank, Ui.muted(this), 24);
        row.addView(box);
        bindBox(row, box, label);
        row.setOnClickListener(v -> {
            toggle(label);
            bindBox(row, box, label);
        });
        return row;
    }

    private View createPickRow(String name) {
        LinearLayout row = listRow();
        row.addView(Ui.icon(this, R.drawable.ic_add, Ui.textColor(this), 24));
        row.addView(rowText("Create \"" + name + "\""), rowTextParams());
        row.setContentDescription("Create label " + name);
        row.setOnClickListener(v -> createAndApply(name));
        return row;
    }

    private int countWith(String label) {
        int k = 0;
        for (Note n : chosen) if (n.labels.contains(label)) k++;
        return k;
    }

    /** Ticked when every chosen note has the label, a dash when only some do. */
    private void bindBox(View row, ImageView box, String label) {
        int have = countWith(label);
        String state;
        if (have == chosen.size()) {
            box.setImageResource(R.drawable.ic_check_box);
            box.setImageTintList(ColorStateList.valueOf(Ui.textColor(this)));
            state = "checked";
        } else if (have > 0) {
            box.setImageDrawable(partialBox());
            box.setImageTintList(ColorStateList.valueOf(Ui.textColor(this)));
            state = "on some notes";
        } else {
            box.setImageResource(R.drawable.ic_check_box_outline_blank);
            box.setImageTintList(ColorStateList.valueOf(Ui.muted(this)));
            state = "not checked";
        }
        row.setContentDescription(label + ", " + state);
    }

    /** An empty check box with a dash in it, for a label that only some of the notes have. */
    private Drawable partialBox() {
        Drawable outline = ContextCompat.getDrawable(this, R.drawable.ic_check_box_outline_blank).mutate();
        GradientDrawable dash = new GradientDrawable();
        dash.setColor(Color.WHITE);
        LayerDrawable d = new LayerDrawable(new Drawable[]{outline, dash});
        d.setLayerInset(1, dp(7), dp(11), dp(7), dp(11));
        return d;
    }

    /** Adds the label to every chosen note, or removes it from all if they all have it. */
    private void toggle(String label) {
        boolean all = countWith(label) == chosen.size();
        for (Note n : chosen) {
            if (all) {
                if (n.labels.remove(label)) touch(n);
            } else if (!n.labels.contains(label)) {
                n.labels.add(label);
                touch(n);
            }
        }
        store.changed();
    }

    private void createAndApply(String typed) {
        String name = typed.trim();
        if (name.isEmpty()) return;
        if (!store.addLabel(name)) {
            for (String l : store.labels()) if (l.equalsIgnoreCase(name)) name = l;
        }
        for (Note n : chosen) {
            if (!n.labels.contains(name)) {
                n.labels.add(name);
                touch(n);
            }
        }
        store.changed();
        search.setText(""); // Shows every label again, the new one ticked.
    }

    private static void touch(Note n) {
        n.edited = Math.max(System.currentTimeMillis(), n.created + 1);
    }

    // Helpers ----------------------------------------------------------------------------------

    private void close() {
        if (!picking) {
            // A label being renamed keeps its new name; a half-typed new label is dropped.
            View f = getCurrentFocus();
            if (f != null && f.getTag() instanceof LabelRow) commit((LabelRow) f.getTag());
        }
        hideKeyboard();
        finish();
    }

    private static TextWatcher watcher(Runnable after) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                after.run();
            }
        };
    }

    private void showKeyboard(EditText e) {
        e.post(() -> {
            if (e.hasFocus()) {
                getSystemService(InputMethodManager.class).showSoftInput(e, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void hideKeyboard() {
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(root.getWindowToken(), 0);
    }

    private void snack(String msg) {
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
