package app.notes;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;

/** Keep's note editor, for text notes and lists. */
public class EditorActivity extends AppCompatActivity {

    static final String EXTRA_ID = "id", EXTRA_NEW = "new", EXTRA_LIST = "list", EXTRA_LABEL = "label";

    private Store store;
    private Note note;
    private boolean isNew;
    private boolean readOnly;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout root, topBar, bottomBar, formatBar, content, imagesBox, listBox, audioBox;
    private LinearLayout topGroup, leftGroup, undoGroup;
    private FrameLayout backBox, moreBox;
    private NestedScrollView scroll;
    private EditText title, body;
    private TextView edited;
    private Wrap chips;
    private ImageButton pin, remind, archive, undoBtn, redoBtn;
    private int navInset;

    private boolean showChecked = true;
    private long focusItem = -1;
    private int focusPos = -1;
    private boolean applying;

    private final List<String> history = new ArrayList<>();
    private int historyAt = -1;
    private final Runnable recordHistory = this::pushHistory;

    private MediaPlayer player;
    private String playing;
    private final Runnable progressTick = this::updateProgress;
    private final List<Object[]> audioViews = new ArrayList<>();

    private ActivityResultLauncher<PickVisualMediaRequest> pickImage;
    private ActivityResultLauncher<Uri> takePicture;
    private Uri cameraUri;
    private Recorder recorder;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRecentsScreenshotEnabled(false);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        store = Store.get(this);
        pickImage = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::addImage);
        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (ok && cameraUri != null) addImage(cameraUri);
        });
        recorder = new Recorder(this);

        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        if (state != null) id = state.getLong(EXTRA_ID, id);
        if (id >= 0) note = store.get(id);
        if (note == null && getIntent().getBooleanExtra(EXTRA_NEW, false) && state == null) {
            note = store.create();
            isNew = true;
            note.list = getIntent().getBooleanExtra(EXTRA_LIST, false);
            String label = getIntent().getStringExtra(EXTRA_LABEL);
            if (label != null) note.labels.add(label);
            if (note.list) note.items.add(new Note.Item(1, "", false));
        }
        if (note == null) {
            finish();
            return;
        }
        if (note.isCelebrities()) Force.revealed(this);
        readOnly = note.trashed;

        buildUi();
        bindAll();
        lastSaved = state();
        pushHistory();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (formatBar.getVisibility() == View.VISIBLE) showFormatBar(false);
                else close();
            }
        });
        if (isNew) {
            if (note.list) {
                focusItem = note.items.get(0).id;
                focusPos = 0;
                buildList();
            } else {
                body.requestFocus();
            }
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (note != null) out.putLong(EXTRA_ID, note.id);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (note == null) return;
        if (store.get(note.id) == null) {
            finish();
            return;
        }
        bindImages();
        bindAudio();
        bindChips();
        updateTop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (note != null) save();
        stopPlayback();
    }

    // Building ---------------------------------------------------------------------------------

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), 0, dp(8), 0);
        root.addView(topBar, new LinearLayout.LayoutParams(Ui.MATCH, dp(64)));

        backBox = new FrameLayout(this);
        ImageButton back = Ui.iconButton(this, R.drawable.ic_arrow_back, "Navigate up", Ui.textColor(this));
        back.setOnClickListener(v -> close());
        backBox.addView(back, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        topBar.addView(backBox, new LinearLayout.LayoutParams(dp(48), dp(48)));
        topBar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        topGroup = new LinearLayout(this);
        topGroup.setOrientation(LinearLayout.HORIZONTAL);
        topGroup.setPadding(dp(4), 0, dp(4), 0);
        pin = Ui.iconButton(this, R.drawable.ic_push_pin, "Pin", Ui.textColor(this));
        remind = Ui.iconButton(this, R.drawable.ic_add_alert, "Reminder", Ui.textColor(this));
        archive = Ui.iconButton(this, R.drawable.ic_archive, "Archive", Ui.textColor(this));
        pin.setOnClickListener(v -> {
            note.pinned = !note.pinned;
            if (note.pinned && note.archived) {
                note.archived = false;
                snack("Note unarchived and pinned");
            }
            touched();
            updateTop();
        });
        remind.setOnClickListener(v -> editReminder());
        archive.setOnClickListener(v -> archiveNote());
        topGroup.addView(pin);
        topGroup.addView(remind);
        topGroup.addView(archive);
        topBar.addView(topGroup, new LinearLayout.LayoutParams(Ui.WRAP, dp(48)));

        scroll = new NestedScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        imagesBox = new LinearLayout(this);
        imagesBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(imagesBox, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        title = new EditText(this);
        title.setHint("Title");
        styleField(title, 22, R.font.google_sans_regular);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        title.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        title.setPadding(dp(20), dp(10), dp(20), dp(8));
        title.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                if (note.list && !note.items.isEmpty()) focusFirstItem();
                else body.requestFocus();
                return true;
            }
            return false;
        });
        title.addTextChangedListener(watcher(() -> {
            String s = title.getText().toString();
            if (s.contains("\n")) {
                int at = s.indexOf('\n');
                applying = true;
                title.setText(s.replace("\n", ""));
                title.setSelection(Math.min(at, title.length()));
                applying = false;
                if (note.list) focusFirstItem();
                else body.requestFocus();
            }
        }));
        content.addView(title, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        body = new EditText(this);
        body.setHint("Note");
        styleField(body, 16, R.font.google_sans_text_regular);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setLineSpacing(dp(3), 1f);
        body.setPadding(dp(20), dp(6), dp(20), dp(20));
        body.addTextChangedListener(watcher(null));
        content.addView(body, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0, 0, 0, dp(8));
        content.addView(listBox, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        audioBox = new LinearLayout(this);
        audioBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(audioBox, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        chips = new Wrap(this, dp(8), dp(8));
        chips.setPadding(dp(20), dp(4), dp(20), dp(16));
        content.addView(chips, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        View filler = new View(this);
        filler.setOnClickListener(v -> focusEnd());
        content.addView(filler, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        filler.setMinimumHeight(dp(120));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(8), dp(4), dp(8), dp(4));
        leftGroup = new LinearLayout(this);
        leftGroup.setOrientation(LinearLayout.HORIZONTAL);
        leftGroup.setPadding(dp(4), 0, dp(4), 0);
        ImageButton add = Ui.iconButton(this, R.drawable.ic_add_box, "Add", Ui.textColor(this));
        ImageButton palette = Ui.iconButton(this, R.drawable.ic_palette, getString(R.string.change_colour), Ui.textColor(this));
        ImageButton format = Ui.iconButton(this, R.drawable.ic_text_format, "Formatting options", Ui.textColor(this));
        add.setOnClickListener(v -> showAddSheet());
        palette.setOnClickListener(v -> Sheets.colors(this, note.color, noteColor(), c -> {
            note.color = c;
            touched();
            applyColors();
        }));
        format.setOnClickListener(v -> showFormatBar(true));
        leftGroup.addView(add);
        leftGroup.addView(palette);
        leftGroup.addView(format);
        bottomBar.addView(leftGroup, new LinearLayout.LayoutParams(Ui.WRAP, dp(48)));
        undoGroup = new LinearLayout(this);
        undoGroup.setOrientation(LinearLayout.HORIZONTAL);
        undoBtn = Ui.iconButton(this, R.drawable.ic_undo, "Undo", Ui.textColor(this));
        redoBtn = Ui.iconButton(this, R.drawable.ic_redo, "Redo", Ui.textColor(this));
        undoBtn.setOnClickListener(v -> undo());
        redoBtn.setOnClickListener(v -> redo());
        undoGroup.addView(undoBtn);
        undoGroup.addView(redoBtn);
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(Ui.WRAP, dp(48));
        ul.setMarginStart(dp(4));
        bottomBar.addView(undoGroup, ul);
        edited = Ui.text(this, "", 13, Ui.textColor(this), R.font.google_sans_text_regular);
        edited.setGravity(Gravity.CENTER);
        edited.setSingleLine(true);
        bottomBar.addView(edited, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        moreBox = new FrameLayout(this);
        ImageButton more = Ui.iconButton(this, R.drawable.ic_more_vert, "More options", Ui.textColor(this));
        more.setOnClickListener(v -> showMoreSheet());
        moreBox.addView(more, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        bottomBar.addView(moreBox, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(bottomBar, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        formatBar = new LinearLayout(this);
        formatBar.setOrientation(LinearLayout.HORIZONTAL);
        formatBar.setGravity(Gravity.CENTER_VERTICAL);
        formatBar.setPadding(dp(8), dp(4), dp(8), dp(4));
        formatBar.setVisibility(View.GONE);
        ImageButton closeFormat = Ui.iconButton(this, R.drawable.ic_close, "Close formatting", Ui.textColor(this));
        closeFormat.setOnClickListener(v -> showFormatBar(false));
        formatBar.addView(closeFormat);
        formatBar.addView(formatText("H1", "Heading 1", Note.H1));
        formatBar.addView(formatText("H2", "Heading 2", Note.H2));
        formatBar.addView(formatText("Aa", "Normal text", 0));
        View sep = new View(this);
        sep.setBackgroundColor(Ui.color(this, R.color.keep_stroke));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(1), dp(24));
        sl.setMargins(dp(6), 0, dp(6), 0);
        formatBar.addView(sep, sl);
        formatBar.addView(formatIcon(R.drawable.ic_format_bold, "Bold", Note.BOLD));
        formatBar.addView(formatIcon(R.drawable.ic_format_italic, "Italic", Note.ITALIC));
        formatBar.addView(formatIcon(R.drawable.ic_format_underlined, "Underline", Note.UNDERLINE));
        formatBar.addView(formatIcon(R.drawable.ic_format_clear, "Clear formatting", -1));
        root.addView(formatBar, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            navInset = Math.max(sys.bottom, ime.bottom);
            root.setPadding(sys.left, sys.top, sys.right, 0);
            bottomBar.setPadding(dp(8), dp(4), dp(8), dp(4) + navInset);
            formatBar.setPadding(dp(8), dp(4), dp(8), dp(4) + navInset);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void styleField(EditText e, float sp, int font) {
        e.setTextSize(sp);
        e.setTypeface(Ui.font(this, font));
        e.setTextColor(Ui.textColor(this));
        e.setHintTextColor(Ui.muted(this));
        e.setBackground(null);
        e.setHighlightColor(ColorUtils.setAlphaComponent(Ui.color(this, R.color.keep_primary), 0x66));
    }

    private TextWatcher watcher(Runnable after) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (applying) return;
                if (after != null) after.run();
                changed();
            }
        };
    }

    // Binding ----------------------------------------------------------------------------------

    private void bindAll() {
        applying = true;
        title.setText(note.title);
        if (!note.list) {
            body.setText(Fmt.styled(note), TextView.BufferType.SPANNABLE);
        }
        applying = false;
        body.setVisibility(note.list ? View.GONE : View.VISIBLE);
        listBox.setVisibility(note.list ? View.VISIBLE : View.GONE);
        if (note.list) buildList();
        bindImages();
        bindAudio();
        bindChips();
        updateTop();
        updateEdited();
        applyColors();
        if (readOnly) {
            for (EditText e : new EditText[]{title, body}) {
                e.setFocusable(false);
                e.setCursorVisible(false);
                e.setOnClickListener(v -> snack("Can't edit in Deleted"));
            }
            topGroup.setVisibility(View.GONE);
            leftGroup.setVisibility(View.GONE);
        }
    }

    private int noteColor() {
        return Ui.editorColor(this, note.color);
    }

    private int containerColor() {
        int base = noteColor();
        return Ui.night(this) ? ColorUtils.blendARGB(base, Color.WHITE, 0.09f)
                : ColorUtils.blendARGB(base, Color.BLACK, 0.06f);
    }

    private void applyColors() {
        int bg = noteColor();
        root.setBackgroundColor(bg);
        getWindow().getDecorView().setBackgroundColor(bg);
        int box = containerColor();
        float r = dp(24);
        backBox.setBackground(Ui.rounded(box, r, 0, 0));
        topGroup.setBackground(Ui.rounded(box, r, 0, 0));
        leftGroup.setBackground(Ui.rounded(box, r, 0, 0));
        undoGroup.setBackground(Ui.rounded(box, r, 0, 0));
        moreBox.setBackground(Ui.rounded(box, r, 0, 0));
        bottomBar.setBackgroundColor(bg);
        formatBar.setBackgroundColor(bg);
        updateHistoryButtons();
    }

    private void updateTop() {
        pin.setImageResource(note.pinned ? R.drawable.ic_push_pin_fill1 : R.drawable.ic_push_pin);
        pin.setContentDescription(note.pinned ? "Unpin" : "Pin");
        archive.setImageResource(note.archived ? R.drawable.ic_unarchive : R.drawable.ic_archive);
        archive.setContentDescription(note.archived ? "Unarchive" : "Archive");
    }

    private void updateEdited() {
        long t = note.edited;
        String s;
        if (DateUtils.isToday(t)) {
            s = DateFormat.getTimeFormat(this).format(t);
        } else if (DateUtils.isToday(t + Store.DAY)) {
            s = "yesterday, " + DateFormat.getTimeFormat(this).format(t);
        } else {
            Calendar now = Calendar.getInstance(), then = Calendar.getInstance();
            then.setTimeInMillis(t);
            int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH;
            flags |= now.get(Calendar.YEAR) == then.get(Calendar.YEAR) ? DateUtils.FORMAT_NO_YEAR : DateUtils.FORMAT_SHOW_YEAR;
            s = DateUtils.formatDateTime(this, t, flags);
        }
        edited.setText("Edited " + s);
    }

    private void bindImages() {
        imagesBox.removeAllViews();
        int total = note.attachments.size();
        if (total == 0) return;
        int w = getResources().getDisplayMetrics().widthPixels;
        if (total == 1) {
            Note.Attachment a = note.attachments.get(0);
            File f = store.media(a.file);
            int[] s = Media.size(f);
            ImageView iv = imageView(a, 0);
            int h = Math.min((int) (w * (s[1] / (float) s[0])), (int) (getResources().getDisplayMetrics().heightPixels * 0.7f));
            imagesBox.addView(iv, new LinearLayout.LayoutParams(Ui.MATCH, h));
            Media.load(iv, f, w);
            return;
        }
        int perRow = total == 2 || total == 4 ? 2 : 3;
        int rowH = w / perRow;
        LinearLayout row = null;
        for (int i = 0; i < total; i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                imagesBox.addView(row, new LinearLayout.LayoutParams(Ui.MATCH, rowH));
            }
            Note.Attachment a = note.attachments.get(i);
            ImageView iv = imageView(a, i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.MATCH, 1f);
            if (i % perRow != 0) lp.setMarginStart(dp(2));
            row.addView(iv, lp);
            Media.load(iv, store.media(a.file), rowH);
        }
    }

    private ImageView imageView(Note.Attachment a, int index) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (a.kind == Note.DRAWING) iv.setBackgroundColor(Color.WHITE);
        iv.setContentDescription(a.kind == Note.DRAWING ? "Drawing" : "Image");
        iv.setOnClickListener(v -> {
            save();
            if (a.kind == Note.DRAWING && !readOnly) {
                startActivity(new Intent(this, DrawingActivity.class)
                        .putExtra(EXTRA_ID, note.id).putExtra(DrawingActivity.EXTRA_FILE, a.file));
            } else {
                startActivity(new Intent(this, ImageActivity.class)
                        .putExtra(EXTRA_ID, note.id).putExtra(ImageActivity.EXTRA_INDEX, index));
            }
        });
        return iv;
    }

    private void bindChips() {
        chips.removeAllViews();
        if (note.reminder > 0) {
            TextView c = NoteAdapter.chip(this, Reminders.describe(this, note.reminder),
                    note.repeat != Reminders.REPEAT_NONE ? R.drawable.ic_schedule : R.drawable.ic_alarm);
            if (note.reminderDone && note.repeat == Reminders.REPEAT_NONE) {
                c.setPaintFlags(c.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            }
            c.setOnClickListener(v -> editReminder());
            chips.addView(c);
        }
        for (String l : note.labels) {
            TextView c = NoteAdapter.chip(this, l, 0);
            c.setOnClickListener(v -> openLabels());
            chips.addView(c);
        }
        chips.setVisibility(chips.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    // Checklist --------------------------------------------------------------------------------

    private void buildList() {
        listBox.removeAllViews();
        boolean bottom = Prefs.moveTickedToBottom(this);
        int ticked = 0;
        for (Note.Item it : note.items) {
            if (bottom && it.checked) {
                ticked++;
                continue;
            }
            listBox.addView(itemRow(it));
        }
        if (!readOnly) listBox.addView(addItemRow());
        if (bottom && ticked > 0) {
            View div = new View(this);
            div.setBackgroundColor(Ui.color(this, R.color.keep_stroke));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.MATCH, dp(1));
            dl.setMargins(dp(20), dp(6), dp(20), dp(2));
            listBox.addView(div, dl);
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(16), 0, dp(20), 0);
            head.setBackground(Ui.rippleRounded(this, 0));
            ImageView chevron = Ui.icon(this, showChecked ? R.drawable.ic_expand_less : R.drawable.ic_expand_more,
                    Ui.muted(this), 24);
            head.addView(chevron);
            TextView t = Ui.text(this, ticked + " " + getString(ticked == 1 ? R.string.ticked_item : R.string.ticked_items),
                    16, Ui.muted(this), R.font.google_sans_text_regular);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            tl.setMarginStart(dp(12));
            head.addView(t, tl);
            head.setOnClickListener(v -> {
                showChecked = !showChecked;
                buildList();
            });
            listBox.addView(head, new LinearLayout.LayoutParams(Ui.MATCH, dp(48)));
            if (showChecked) {
                for (Note.Item it : note.items) if (it.checked) listBox.addView(itemRow(it));
            }
        }
        if (focusItem >= 0) {
            final long id = focusItem;
            final int pos = focusPos;
            focusItem = -1;
            listBox.post(() -> {
                EditText e = listBox.findViewWithTag("item" + id);
                if (e != null) {
                    e.requestFocus();
                    e.setSelection(Math.max(0, Math.min(pos < 0 ? e.length() : pos, e.length())));
                    getSystemService(InputMethodManager.class).showSoftInput(e, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    private View itemRow(Note.Item it) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(4), 0, dp(8), 0);

        ImageView handle = Ui.icon(this, R.drawable.ic_drag_indicator, Ui.muted(this), 24);
        handle.setContentDescription("Drag to reorder");
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(24), dp(24));
        hl.topMargin = dp(12);
        row.addView(handle, hl);
        handle.setVisibility(it.checked || readOnly ? View.INVISIBLE : View.VISIBLE);
        if (!it.checked && !readOnly) handle.setOnTouchListener(new DragHandle(row, it));

        ImageButton box = Ui.iconButton(this, it.checked ? R.drawable.ic_check_box : R.drawable.ic_check_box_outline_blank,
                it.checked ? "Unchecked" : "Checked", it.checked ? Ui.muted(this) : Ui.textColor(this));
        box.setContentDescription(it.checked ? "Checked" : "Unchecked");
        box.setOnClickListener(v -> {
            if (readOnly) {
                snack("Can't edit in Deleted");
                return;
            }
            it.checked = !it.checked;
            buildList();
            changed();
        });
        row.addView(box);

        EditText e = new EditText(this);
        e.setTag("item" + it.id);
        styleField(e, 16, R.font.google_sans_text_regular);
        e.setText(it.text);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setPadding(dp(4), dp(12), dp(4), dp(12));
        if (it.checked) {
            e.setTextColor(Ui.muted(this));
            e.setPaintFlags(e.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        ImageButton del = Ui.iconButton(this, R.drawable.ic_close, "Delete", Ui.muted(this));
        del.setVisibility(View.INVISIBLE);
        del.setOnClickListener(v -> {
            int i = note.items.indexOf(it);
            note.items.remove(it);
            if (i > 0) {
                focusItem = note.items.get(Math.min(i - 1, note.items.size() - 1)).id;
                focusPos = -1;
            }
            buildList();
            changed();
        });
        if (readOnly) {
            e.setFocusable(false);
            e.setOnClickListener(v -> snack("Can't edit in Deleted"));
        }
        e.setOnFocusChangeListener((v, has) -> del.setVisibility(has ? View.VISIBLE : View.INVISIBLE));
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (applying) return;
                String str = s.toString();
                int nl = str.indexOf('\n');
                if (nl >= 0) {
                    it.text = str.substring(0, nl);
                    String rest = str.substring(nl + 1).replace("\n", " ");
                    Note.Item next = new Note.Item(nextItemId(), rest, false);
                    note.items.add(note.items.indexOf(it) + 1, next);
                    focusItem = next.id;
                    focusPos = 0;
                    buildList();
                } else {
                    it.text = str;
                }
                changed();
            }
        });
        e.setOnKeyListener((v, keyCode, ev) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL && ev.getAction() == KeyEvent.ACTION_DOWN
                    && e.getSelectionStart() == 0 && e.getSelectionEnd() == 0) {
                int i = note.items.indexOf(it);
                if (i <= 0) return false;
                Note.Item prev = note.items.get(i - 1);
                focusItem = prev.id;
                focusPos = prev.text.length();
                prev.text = prev.text + it.text;
                note.items.remove(it);
                buildList();
                changed();
                return true;
            }
            return false;
        });
        row.addView(e, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        row.addView(del);
        return row;
    }

    private View addItemRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(28), 0, dp(20), 0);
        row.setBackground(Ui.rippleRounded(this, 0));
        row.addView(Ui.icon(this, R.drawable.ic_add, Ui.muted(this), 24));
        TextView t = Ui.text(this, "List item", 16, Ui.muted(this), R.font.google_sans_text_regular);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        tl.setMarginStart(dp(24));
        row.addView(t, tl);
        row.setContentDescription("List item");
        row.setOnClickListener(v -> {
            Note.Item it = new Note.Item(nextItemId(), "", false);
            if (Prefs.addToBottom(this)) {
                int at = 0;
                for (int i = 0; i < note.items.size(); i++) if (!note.items.get(i).checked) at = i + 1;
                note.items.add(at, it);
            } else {
                note.items.add(0, it);
            }
            focusItem = it.id;
            focusPos = 0;
            buildList();
            changed();
        });
        return row;
    }

    private long nextItemId() {
        long m = 0;
        for (Note.Item i : note.items) m = Math.max(m, i.id);
        return m + 1;
    }

    private void focusFirstItem() {
        for (Note.Item i : note.items) {
            if (!i.checked) {
                focusItem = i.id;
                focusPos = -1;
                buildList();
                return;
            }
        }
    }

    private void focusEnd() {
        if (readOnly) return;
        if (note.list) {
            Note.Item last = null;
            for (Note.Item i : note.items) if (!i.checked) last = i;
            if (last != null) {
                focusItem = last.id;
                focusPos = -1;
                buildList();
            }
        } else {
            body.requestFocus();
            body.setSelection(body.length());
            getSystemService(InputMethodManager.class).showSoftInput(body, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /** Drags a list item up or down by its handle. */
    private final class DragHandle implements View.OnTouchListener {
        private final View row;
        private final Note.Item item;
        private float lastY;
        private float offset;

        DragHandle(View row, Note.Item item) {
            this.row = row;
            this.item = item;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastY = e.getRawY();
                    offset = 0;
                    row.setElevation(dp(6));
                    row.setBackgroundColor(noteColor());
                    scroll.requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    offset += e.getRawY() - lastY;
                    lastY = e.getRawY();
                    int index = listBox.indexOfChild(row);
                    if (offset > row.getHeight() / 2f && index + 1 < listBox.getChildCount()) {
                        View below = listBox.getChildAt(index + 1);
                        Note.Item other = itemOf(below);
                        if (other != null && !other.checked) {
                            swap(item, other);
                            listBox.removeView(row);
                            listBox.addView(row, index + 1);
                            offset -= below.getHeight();
                        }
                    } else if (offset < -row.getHeight() / 2f && index > 0) {
                        View above = listBox.getChildAt(index - 1);
                        Note.Item other = itemOf(above);
                        if (other != null && !other.checked) {
                            swap(item, other);
                            listBox.removeView(row);
                            listBox.addView(row, index - 1);
                            offset += above.getHeight();
                        }
                    }
                    row.setTranslationY(offset);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    row.setTranslationY(0);
                    row.setElevation(0);
                    row.setBackground(null);
                    changed();
                    return true;
            }
            return false;
        }

        private Note.Item itemOf(View rowView) {
            for (Note.Item i : note.items) {
                if (rowView.findViewWithTag("item" + i.id) != null) return i;
            }
            return null;
        }

        private void swap(Note.Item a, Note.Item b) {
            int i = note.items.indexOf(a), j = note.items.indexOf(b);
            if (i < 0 || j < 0) return;
            note.items.set(i, b);
            note.items.set(j, a);
        }
    }

    // Audio ------------------------------------------------------------------------------------

    private void bindAudio() {
        audioBox.removeAllViews();
        audioViews.clear();
        for (Note.Audio a : note.audio) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(4), dp(12), dp(4));
            ImageButton play = Ui.iconButton(this, R.drawable.ic_play_arrow, "Play", Ui.textColor(this));
            play.setBackground(Ui.ripple(this, Ui.circle(containerColor())));
            row.addView(play, new LinearLayout.LayoutParams(dp(40), dp(40)));
            SeekBar bar = new SeekBar(this);
            bar.setMax(1000);
            bar.setProgressTintList(ColorStateList.valueOf(Ui.textColor(this)));
            bar.setThumbTintList(ColorStateList.valueOf(Ui.textColor(this)));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.muted(this)));
            row.addView(bar, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            TextView time = Ui.text(this, Recorder.formatDuration(a.durationMs), 13, Ui.muted(this),
                    R.font.google_sans_text_regular);
            row.addView(time);
            ImageButton del = Ui.iconButton(this, R.drawable.ic_close, "Delete recording", Ui.muted(this));
            row.addView(del);
            if (readOnly) del.setVisibility(View.GONE);
            play.setOnClickListener(v -> togglePlay(a));
            del.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setMessage("Delete recording?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (d, w) -> {
                        if (a.file.equals(playing)) stopPlayback();
                        note.audio.remove(a);
                        store.media(a.file).delete();
                        touched();
                        bindAudio();
                    })
                    .show());
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    if (fromUser && player != null && a.file.equals(playing)) {
                        player.seekTo((int) (player.getDuration() * (p / 1000f)));
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            audioViews.add(new Object[]{a, play, bar, time});
            audioBox.addView(row, new LinearLayout.LayoutParams(Ui.MATCH, dp(56)));
        }
    }

    private void togglePlay(Note.Audio a) {
        if (player != null && a.file.equals(playing)) {
            if (player.isPlaying()) player.pause();
            else player.start();
            updateProgress();
            return;
        }
        stopPlayback();
        try {
            player = new MediaPlayer();
            player.setDataSource(store.media(a.file).getPath());
            player.prepare();
            player.setOnCompletionListener(mp -> stopPlayback());
            player.start();
            playing = a.file;
            updateProgress();
        } catch (Exception e) {
            stopPlayback();
            snack("Couldn't play this recording");
        }
    }

    private void stopPlayback() {
        handler.removeCallbacks(progressTick);
        if (player != null) {
            player.release();
            player = null;
        }
        playing = null;
        for (Object[] v : audioViews) {
            ((ImageButton) v[1]).setImageResource(R.drawable.ic_play_arrow);
            ((SeekBar) v[2]).setProgress(0);
            ((TextView) v[3]).setText(Recorder.formatDuration(((Note.Audio) v[0]).durationMs));
        }
    }

    private void updateProgress() {
        handler.removeCallbacks(progressTick);
        for (Object[] v : audioViews) {
            Note.Audio a = (Note.Audio) v[0];
            boolean me = player != null && a.file.equals(playing);
            ((ImageButton) v[1]).setImageResource(me && player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            if (me) {
                int pos = player.getCurrentPosition(), dur = Math.max(1, player.getDuration());
                ((SeekBar) v[2]).setProgress((int) (1000L * pos / dur));
                ((TextView) v[3]).setText(Recorder.formatDuration(pos));
            }
        }
        if (player != null && player.isPlaying()) handler.postDelayed(progressTick, 100);
    }

    // Sheets -----------------------------------------------------------------------------------

    private void showAddSheet() {
        LinearLayout col = Sheets.column(this);
        BottomSheetDialog[] d = new BottomSheetDialog[1];
        int fg = Ui.textColor(this);
        col.addView(Ui.row(this, R.drawable.ic_photo_camera, "Take photo", fg, v -> {
            d[0].dismiss();
            takePhoto();
        }));
        col.addView(Ui.row(this, R.drawable.ic_image, "Add image", fg, v -> {
            d[0].dismiss();
            App.external = true;
            pickImage.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
        }));
        col.addView(Ui.row(this, R.drawable.ic_brush, "Drawing", fg, v -> {
            d[0].dismiss();
            save();
            startActivity(new Intent(this, DrawingActivity.class).putExtra(EXTRA_ID, note.id));
        }));
        col.addView(Ui.row(this, R.drawable.ic_mic, "Recording", fg, v -> {
            d[0].dismiss();
            recorder.record((file, ms) -> {
                note.audio.add(new Note.Audio(file, ms, System.currentTimeMillis()));
                touched();
                bindAudio();
            });
        }));
        if (!note.list) {
            col.addView(Ui.row(this, R.drawable.ic_check_box, getString(R.string.checkboxes), fg, v -> {
                d[0].dismiss();
                toList();
            }));
        }
        d[0] = Sheets.sheet(this, noteColor(), col);
        d[0].show();
    }

    private void showMoreSheet() {
        LinearLayout col = Sheets.column(this);
        BottomSheetDialog[] d = new BottomSheetDialog[1];
        int fg = Ui.textColor(this);
        if (readOnly) {
            col.addView(Ui.row(this, R.drawable.ic_restore_from_trash, "Restore", fg, v -> {
                d[0].dismiss();
                note.trashed = false;
                readOnly = false;
                store.changed();
                Reminders.schedule(this, note);
                MainActivity.pendingMessage = "Note restored";
                finish();
            }));
            col.addView(Ui.row(this, R.drawable.ic_delete_forever, "Delete forever", fg, v -> {
                d[0].dismiss();
                new MaterialAlertDialogBuilder(this)
                        .setMessage("Delete this note forever?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (x, w) -> {
                            store.deleteForever(note);
                            finish();
                        })
                        .show();
            }));
        } else {
            col.addView(Ui.row(this, R.drawable.ic_delete, "Delete", fg, v -> {
                d[0].dismiss();
                deleteNote();
            }));
            col.addView(Ui.row(this, R.drawable.ic_content_copy, "Make a copy", fg, v -> {
                d[0].dismiss();
                save();
                Note copy = store.duplicate(note);
                finish();
                startActivity(new Intent(this, EditorActivity.class).putExtra(EXTRA_ID, copy.id));
            }));
            col.addView(Ui.row(this, R.drawable.ic_share, "Send", fg, v -> {
                d[0].dismiss();
                save();
                Sheets.send(this, note);
            }));
            col.addView(Ui.row(this, R.drawable.ic_label, "Labels", fg, v -> {
                d[0].dismiss();
                openLabels();
            }));
            if (note.list) {
                col.addView(Ui.row(this, R.drawable.ic_check_box_outline_blank, getString(R.string.hide_checkboxes), fg, v -> {
                    d[0].dismiss();
                    toText();
                }));
                boolean anyChecked = false;
                for (Note.Item i : note.items) anyChecked |= i.checked;
                if (anyChecked) {
                    col.addView(Ui.row(this, R.drawable.ic_check_box_outline_blank, getString(R.string.uncheck_all), fg, v -> {
                        d[0].dismiss();
                        for (Note.Item i : note.items) i.checked = false;
                        buildList();
                        changed();
                    }));
                    col.addView(Ui.row(this, R.drawable.ic_delete, getString(R.string.delete_checked), fg, v -> {
                        d[0].dismiss();
                        note.items.removeIf(i -> i.checked);
                        buildList();
                        changed();
                    }));
                }
            }
        }
        col.addView(Ui.row(this, R.drawable.ic_help, "Help & feedback", fg, v -> {
            d[0].dismiss();
            startActivity(new Intent(this, HelpActivity.class));
        }));
        d[0] = Sheets.sheet(this, noteColor(), col);
        d[0].show();
    }

    private void openLabels() {
        save();
        startActivity(new Intent(this, LabelsActivity.class).putExtra(LabelsActivity.EXTRA_NOTES, new long[]{note.id}));
    }

    private void editReminder() {
        if (readOnly) return;
        Sheets.reminder(this, note, noteColor(), (when, repeat) -> {
            note.reminder = when;
            note.repeat = repeat;
            note.reminderDone = false;
            Reminders.schedule(this, note);
            touched();
            bindChips();
        });
    }

    private void toList() {
        save();
        String[] lines = note.text.split("\n");
        note.items.clear();
        long id = 1;
        for (String l : lines) {
            if (l.trim().isEmpty() && lines.length > 1) continue;
            note.items.add(new Note.Item(id++, l, false));
        }
        if (note.items.isEmpty()) note.items.add(new Note.Item(1, "", false));
        note.list = true;
        note.text = "";
        note.spans.clear();
        body.setVisibility(View.GONE);
        listBox.setVisibility(View.VISIBLE);
        focusItem = note.items.get(note.items.size() - 1).id;
        focusPos = -1;
        buildList();
        changed();
    }

    private void toText() {
        StringBuilder sb = new StringBuilder();
        for (Note.Item i : note.items) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(i.text);
        }
        note.list = false;
        note.items.clear();
        note.text = sb.toString();
        note.spans.clear();
        applying = true;
        body.setText(note.text);
        applying = false;
        listBox.removeAllViews();
        listBox.setVisibility(View.GONE);
        body.setVisibility(View.VISIBLE);
        changed();
    }

    // Images -----------------------------------------------------------------------------------

    private void takePhoto() {
        File f = new File(getCacheDir(), "camera.jpg");
        cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
        App.external = true;
        try {
            takePicture.launch(cameraUri);
        } catch (Exception e) {
            snack("No camera app found");
        }
    }

    private void addImage(Uri uri) {
        if (uri == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            String name = Media.importImage(this, uri);
            runOnUiThread(() -> {
                if (name == null) {
                    snack("Couldn't add the image");
                    return;
                }
                note.attachments.add(new Note.Attachment(name, Note.IMAGE));
                touched();
                bindImages();
            });
        });
    }

    // Formatting -------------------------------------------------------------------------------

    private void showFormatBar(boolean show) {
        if (show && (note.list || readOnly)) {
            snack(note.list ? "Formatting isn't available in lists" : "Can't edit in Deleted");
            return;
        }
        formatBar.setVisibility(show ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show && !body.hasFocus()) {
            body.requestFocus();
            body.setSelection(body.length());
        }
    }

    private View formatText(String label, String desc, int kind) {
        TextView t = Ui.text(this, label, 16, Ui.textColor(this), R.font.google_sans_medium);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.borderless(this));
        t.setContentDescription(desc);
        t.setOnClickListener(v -> applyHeading(kind));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return t;
    }

    private View formatIcon(int icon, String desc, int kind) {
        ImageButton b = Ui.iconButton(this, icon, desc, Ui.textColor(this));
        b.setOnClickListener(v -> {
            if (kind < 0) clearFormatting();
            else toggleStyle(kind);
        });
        return b;
    }

    private void applyHeading(int kind) {
        Editable s = body.getText();
        int a = body.getSelectionStart(), b = body.getSelectionEnd();
        if (a < 0) return;
        String str = s.toString();
        int start = str.lastIndexOf('\n', Math.max(0, a - 1)) + 1;
        if (a == 0) start = 0;
        int end = str.indexOf('\n', b);
        if (end < 0) end = str.length();
        for (Fmt.Heading h : s.getSpans(start, end, Fmt.Heading.class)) s.removeSpan(h);
        for (Fmt.Bold bold : s.getSpans(start, end, Fmt.Bold.class)) {
            if (s.getSpanStart(bold) >= start && s.getSpanEnd(bold) <= end) s.removeSpan(bold);
        }
        if (kind != 0 && end > start) {
            s.setSpan(new Fmt.Heading(kind), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
            s.setSpan(new Fmt.Bold(), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        }
        changed();
    }

    private void toggleStyle(int kind) {
        Editable s = body.getText();
        int a = body.getSelectionStart(), b = body.getSelectionEnd();
        if (a < 0 || a == b) {
            snack("Select text to format");
            return;
        }
        Class<?> cls = kind == Note.BOLD ? Fmt.Bold.class : kind == Note.ITALIC ? Fmt.Italic.class : Fmt.Underline.class;
        Object[] existing = s.getSpans(a, b, cls);
        boolean covered = false;
        for (Object o : existing) {
            if (s.getSpanStart(o) <= a && s.getSpanEnd(o) >= b) covered = true;
        }
        if (covered) {
            for (Object o : existing) {
                int os = s.getSpanStart(o), oe = s.getSpanEnd(o);
                s.removeSpan(o);
                if (os < a) s.setSpan(Fmt.span(kind), os, a, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                if (oe > b) s.setSpan(Fmt.span(kind), b, oe, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
            }
        } else {
            s.setSpan(Fmt.span(kind), a, b, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        }
        changed();
    }

    private void clearFormatting() {
        Editable s = body.getText();
        int a = body.getSelectionStart(), b = body.getSelectionEnd();
        if (a < 0) return;
        if (a == b) {
            a = 0;
            b = s.length();
        }
        for (Class<?> cls : new Class<?>[]{Fmt.Bold.class, Fmt.Italic.class, Fmt.Underline.class, Fmt.Heading.class}) {
            for (Object o : s.getSpans(a, b, cls)) s.removeSpan(o);
        }
        changed();
    }

    // History ----------------------------------------------------------------------------------

    private String state() {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("t", title.getText().toString());
            o.put("l", note.list);
            if (note.list) {
                org.json.JSONArray arr = new org.json.JSONArray();
                for (Note.Item i : note.items) {
                    arr.put(new org.json.JSONArray().put(i.id).put(i.text).put(i.checked));
                }
                o.put("i", arr);
            } else {
                o.put("b", body.getText().toString());
                org.json.JSONArray sp = new org.json.JSONArray();
                for (int[] r : Fmt.extract(body.getText())) sp.put(new org.json.JSONArray().put(r[0]).put(r[1]).put(r[2]));
                o.put("s", sp);
            }
            return o.toString();
        } catch (org.json.JSONException e) {
            return "";
        }
    }

    private void changed() {
        if (applying) return;
        handler.removeCallbacks(recordHistory);
        handler.postDelayed(recordHistory, 500);
    }

    private void pushHistory() {
        String s = state();
        if (historyAt >= 0 && history.get(historyAt).equals(s)) return;
        while (history.size() > historyAt + 1) history.remove(history.size() - 1);
        history.add(s);
        historyAt = history.size() - 1;
        if (history.size() > 1) save();
        updateHistoryButtons();
    }

    private void undo() {
        pushHistory();
        if (historyAt <= 0) return;
        historyAt--;
        restore(history.get(historyAt));
    }

    private void redo() {
        if (historyAt >= history.size() - 1) return;
        historyAt++;
        restore(history.get(historyAt));
    }

    private void restore(String s) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(s);
            applying = true;
            title.setText(o.getString("t"));
            note.list = o.getBoolean("l");
            if (note.list) {
                note.items.clear();
                org.json.JSONArray arr = o.getJSONArray("i");
                for (int k = 0; k < arr.length(); k++) {
                    org.json.JSONArray it = arr.getJSONArray(k);
                    note.items.add(new Note.Item(it.getLong(0), it.getString(1), it.getBoolean(2)));
                }
                body.setVisibility(View.GONE);
                listBox.setVisibility(View.VISIBLE);
                buildList();
            } else {
                Note tmp = new Note();
                tmp.text = o.getString("b");
                org.json.JSONArray sp = o.getJSONArray("s");
                for (int k = 0; k < sp.length(); k++) {
                    org.json.JSONArray r = sp.getJSONArray(k);
                    tmp.spans.add(new int[]{r.getInt(0), r.getInt(1), r.getInt(2)});
                }
                body.setText(Fmt.styled(tmp), TextView.BufferType.SPANNABLE);
                body.setSelection(body.length());
                listBox.setVisibility(View.GONE);
                body.setVisibility(View.VISIBLE);
            }
        } catch (org.json.JSONException ignored) {
        } finally {
            applying = false;
        }
        save();
        updateHistoryButtons();
    }

    private void updateHistoryButtons() {
        boolean any = history.size() > 1 && !readOnly;
        undoGroup.setVisibility(any ? View.VISIBLE : View.GONE);
        undoBtn.setEnabled(historyAt > 0);
        undoBtn.setAlpha(historyAt > 0 ? 1f : 0.38f);
        redoBtn.setEnabled(historyAt < history.size() - 1);
        redoBtn.setAlpha(historyAt < history.size() - 1 ? 1f : 0.38f);
    }

    // Saving and closing -----------------------------------------------------------------------

    private String lastSaved;

    private void save() {
        if (readOnly || note == null) return;
        String now = state();
        if (now.equals(lastSaved)) return;
        lastSaved = now;
        note.title = title.getText().toString();
        if (!note.list) {
            note.text = body.getText().toString();
            note.spans.clear();
            note.spans.addAll(Fmt.extract(body.getText()));
        }
        if (note.isCelebrities()) Celebs.touched = true;
        note.edited = Math.max(System.currentTimeMillis(), note.created + 1);
        store.changed();
        updateEdited();
    }

    /** Records a change made outside the text (colour, pin, picture...). */
    private void touched() {
        note.edited = Math.max(System.currentTimeMillis(), note.created + 1);
        store.changed();
        updateEdited();
    }

    private void close() {
        save();
        if (note.isEmpty() && !note.isCelebrities() && !readOnly) {
            Reminders.cancel(this, note);
            store.deleteForever(note);
            MainActivity.pendingMessage = "Empty note discarded";
        }
        finish();
    }

    private void archiveNote() {
        save();
        boolean was = note.archived;
        note.archived = !was;
        if (note.archived) note.pinned = false;
        store.changed();
        final Note n = note;
        MainActivity.pendingMessage = was ? "Note unarchived" : "Note archived";
        MainActivity.pendingUndo = () -> {
            n.archived = was;
            Store.get(getApplicationContext()).changed();
        };
        finish();
    }

    private void deleteNote() {
        save();
        note.trashed = true;
        note.trashedAt = System.currentTimeMillis();
        Reminders.cancel(this, note);
        store.changed();
        final Note n = note;
        MainActivity.pendingMessage = "Note moved to Deleted";
        MainActivity.pendingUndo = () -> {
            n.trashed = false;
            Reminders.schedule(getApplicationContext(), n);
            Store.get(getApplicationContext()).changed();
        };
        finish();
    }

    private void snack(String msg) {
        Snackbar s = Snackbar.make(root, msg, Snackbar.LENGTH_SHORT);
        s.setAnchorView(bottomBar.getVisibility() == View.VISIBLE ? bottomBar : formatBar);
        s.show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
