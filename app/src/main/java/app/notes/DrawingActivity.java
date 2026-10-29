package app.notes;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Keep's drawing canvas: pen, marker, highlighter and eraser. */
public class DrawingActivity extends AppCompatActivity {

    static final String EXTRA_FILE = "file";

    private static final int PEN = 0, MARKER = 1, HIGHLIGHTER = 2, ERASER = 3;
    private static final int[] COLORS = {0xFF000000, 0xFFEA4335, 0xFFFBBC04, 0xFF34A853, 0xFF4285F4,
            0xFFA142F4, 0xFFF06292, 0xFF795548, 0xFF9AA0A6, 0xFFFFFFFF};
    private static final float[] SIZES = {2f, 4f, 8f, 14f, 22f};

    private Canvas2 canvas;
    private Store store;
    private Note note;
    private String file;
    private int tool = PEN;
    private final int[] toolColor = {0xFF000000, 0xFFEA4335, 0xFFFBBC04, 0};
    private final int[] toolSize = {1, 2, 3, 3};
    private LinearLayout panel;
    private final ImageButton[] toolButtons = new ImageButton[4];
    private ImageButton undoBtn, redoBtn;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        store = Store.get(this);
        long id = getIntent().getLongExtra(EditorActivity.EXTRA_ID, -1);
        note = id >= 0 ? store.get(id) : null;
        file = getIntent().getStringExtra(EXTRA_FILE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.color(this, R.color.keep_card));
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        ImageButton back = Ui.iconButton(this, R.drawable.ic_arrow_back, "Navigate up", Ui.textColor(this));
        back.setOnClickListener(v -> done());
        top.addView(back);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        undoBtn = Ui.iconButton(this, R.drawable.ic_undo, "Undo", Ui.textColor(this));
        redoBtn = Ui.iconButton(this, R.drawable.ic_redo, "Redo", Ui.textColor(this));
        ImageButton more = Ui.iconButton(this, R.drawable.ic_more_vert, "More options", Ui.textColor(this));
        undoBtn.setOnClickListener(v -> {
            canvas.undo();
            updateHistory();
        });
        redoBtn.setOnClickListener(v -> {
            canvas.redo();
            updateHistory();
        });
        more.setOnClickListener(this::showMore);
        top.addView(undoBtn);
        top.addView(redoBtn);
        top.addView(more);
        root.addView(top, new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(this, 56)));

        canvas = new Canvas2(this);
        if (file != null) canvas.base = BitmapFactory.decodeFile(store.media(file).getPath());
        root.addView(canvas, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setVisibility(View.GONE);
        root.addView(panel, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER);
        int[] icons = {R.drawable.ic_ink_pen, R.drawable.ic_ink_marker, R.drawable.ic_ink_highlighter, R.drawable.ic_ink_eraser};
        String[] names = {"Pen", "Marker", "Highlighter", "Eraser"};
        for (int i = 0; i < 4; i++) {
            final int t = i;
            ImageButton b = Ui.iconButton(this, icons[i], names[i], Ui.textColor(this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 48));
            lp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                if (tool == t) togglePanel();
                else {
                    tool = t;
                    panel.setVisibility(View.GONE);
                    updateTools();
                }
            });
            toolButtons[i] = b;
            tools.addView(b);
        }
        root.addView(tools, new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(this, 64)));
        updateTools();
        updateHistory();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            root.setPadding(0, sys.top, 0, sys.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (panel.getVisibility() == View.VISIBLE) panel.setVisibility(View.GONE);
                else done();
            }
        });
    }

    private void updateTools() {
        for (int i = 0; i < 4; i++) {
            ImageButton b = toolButtons[i];
            boolean on = i == tool;
            b.setBackground(on ? Ui.rounded(Ui.color(this, R.color.keep_selected), Ui.dp(this, 16), 0, 0)
                    : Ui.borderless(this));
            int tint = on ? Ui.color(this, R.color.keep_on_selected) : Ui.textColor(this);
            if (on && i != ERASER && toolColor[i] != 0xFFFFFFFF && toolColor[i] != 0xFF000000) tint = toolColor[i];
            b.setImageTintList(ColorStateList.valueOf(tint));
        }
        canvas.setTool(tool, toolColor[tool], SIZES[toolSize[tool]]);
    }

    private void togglePanel() {
        if (tool == ERASER) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage("Clear page?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear", (d, w) -> {
                        canvas.clear();
                        updateHistory();
                    })
                    .show();
            return;
        }
        if (panel.getVisibility() == View.VISIBLE) {
            panel.setVisibility(View.GONE);
            return;
        }
        panel.removeAllViews();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout colors = new LinearLayout(this);
        colors.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 4));
        for (int c : COLORS) {
            View dot = new View(this);
            boolean sel = c == toolColor[tool];
            dot.setBackground(Ui.ripple(this, Ui.rounded(c, Ui.dp(this, 18),
                    sel ? Ui.color(this, R.color.keep_primary) : Ui.color(this, R.color.keep_stroke),
                    Ui.dp(this, sel ? 3 : 1))));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36));
            lp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
            dot.setOnClickListener(v -> {
                toolColor[tool] = c;
                updateTools();
                panel.setVisibility(View.GONE);
            });
            colors.addView(dot, lp);
        }
        hs.addView(colors);
        panel.addView(hs);
        LinearLayout sizes = new LinearLayout(this);
        sizes.setGravity(Gravity.CENTER);
        sizes.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        for (int i = 0; i < SIZES.length; i++) {
            final int s = i;
            FrameLayout box = new FrameLayout(this);
            box.setBackground(i == toolSize[tool]
                    ? Ui.rounded(Ui.color(this, R.color.keep_selected), Ui.dp(this, 20), 0, 0) : Ui.borderless(this));
            View dot = new View(this);
            int d = Ui.dp(this, 4 + i * 4);
            dot.setBackground(Ui.circle(Ui.textColor(this)));
            box.addView(dot, new FrameLayout.LayoutParams(d, d, Gravity.CENTER));
            box.setContentDescription("Size " + (i + 1));
            box.setOnClickListener(v -> {
                toolSize[tool] = s;
                updateTools();
                panel.setVisibility(View.GONE);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 40));
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            sizes.addView(box, lp);
        }
        panel.addView(sizes);
        panel.setVisibility(View.VISIBLE);
    }

    private void showMore(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor, Gravity.END);
        m.getMenu().add(0, 1, 0, canvas.grid ? "Hide grid" : "Show grid");
        m.getMenu().add(0, 2, 1, "Clear page");
        if (file != null) m.getMenu().add(0, 3, 2, "Delete");
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    canvas.grid = !canvas.grid;
                    canvas.invalidate();
                    break;
                case 2:
                    canvas.clear();
                    updateHistory();
                    break;
                case 3:
                    if (note != null) {
                        note.attachments.removeIf(a -> a.file.equals(file));
                        store.media(file).delete();
                        store.changed();
                    }
                    finish();
                    break;
            }
            return true;
        });
        m.show();
    }

    private void updateHistory() {
        undoBtn.setEnabled(canvas.canUndo());
        undoBtn.setAlpha(canvas.canUndo() ? 1f : 0.38f);
        redoBtn.setEnabled(canvas.canRedo());
        redoBtn.setAlpha(canvas.canRedo() ? 1f : 0.38f);
    }

    /** Saves the drawing into its note (a new one if needed) and returns to it. */
    private void done() {
        if (!canvas.dirty) {
            finish();
            return;
        }
        Bitmap out = canvas.render();
        String name = file != null ? file : store.newMediaName(".png");
        File f = store.media(name);
        try (FileOutputStream o = new FileOutputStream(f)) {
            out.compress(Bitmap.CompressFormat.PNG, 100, o);
        } catch (Exception e) {
            finish();
            return;
        }
        Media.forget(f);
        boolean created = false;
        if (note == null) {
            note = store.create();
            note.edited = note.created + 1;
            String label = getIntent().getStringExtra(EditorActivity.EXTRA_LABEL);
            if (label != null) note.labels.add(label);
            created = true;
        }
        if (file == null) note.attachments.add(new Note.Attachment(name, Note.DRAWING));
        note.edited = System.currentTimeMillis();
        store.changed();
        finish();
        if (created) {
            startActivity(new Intent(this, EditorActivity.class).putExtra(EditorActivity.EXTRA_ID, note.id));
        }
    }

    /** The drawing surface. Strokes are kept so they can be undone. */
    static final class Canvas2 extends View {
        Bitmap base;
        boolean grid;
        boolean dirty;
        private final List<Stroke> strokes = new ArrayList<>();
        private final List<Stroke> undone = new ArrayList<>();
        private Stroke current;
        private int tool;
        private int color;
        private float size;
        private Bitmap layer;
        private Canvas layerCanvas;
        private final Paint gridPaint = new Paint();
        private float lastX, lastY;

        static final class Stroke {
            final Path path = new Path();
            final Paint paint;
            final boolean clearAll;

            Stroke(Paint p, boolean clearAll) {
                this.paint = p;
                this.clearAll = clearAll;
            }
        }

        Canvas2(Context c) {
            super(c);
            gridPaint.setColor(0x22000000);
            gridPaint.setStrokeWidth(1);
        }

        void setTool(int tool, int color, float sizeDp) {
            this.tool = tool;
            this.color = color;
            this.size = sizeDp * getResources().getDisplayMetrics().density;
        }

        private Paint paint() {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(color);
            p.setStrokeWidth(size);
            switch (tool) {
                case MARKER:
                    p.setStrokeWidth(size * 1.8f);
                    break;
                case HIGHLIGHTER:
                    p.setStrokeWidth(size * 3f);
                    p.setAlpha(90);
                    p.setStrokeCap(Paint.Cap.SQUARE);
                    break;
                case ERASER:
                    p.setStrokeWidth(size * 3f);
                    p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    p.setColor(Color.BLACK);
                    break;
            }
            return p;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (w <= 0 || h <= 0) return;
            layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            layerCanvas = new Canvas(layer);
            redraw();
        }

        private void redraw() {
            if (layer == null) return;
            layer.eraseColor(Color.TRANSPARENT);
            if (base != null) {
                float s = Math.min(getWidth() / (float) base.getWidth(), getHeight() / (float) base.getHeight());
                s = Math.min(s, 1f * getWidth() / base.getWidth());
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.setScale(s, s);
                layerCanvas.drawBitmap(base, m, null);
            }
            for (Stroke st : strokes) {
                if (st.clearAll) layer.eraseColor(Color.TRANSPARENT);
                else layerCanvas.drawPath(st.path, st.paint);
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(Color.WHITE);
            if (grid) {
                float step = 24 * getResources().getDisplayMetrics().density;
                for (float x = step; x < getWidth(); x += step) c.drawLine(x, 0, x, getHeight(), gridPaint);
                for (float y = step; y < getHeight(); y += step) c.drawLine(0, y, getWidth(), y, gridPaint);
            }
            if (layer != null) c.drawBitmap(layer, 0, 0, null);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    current = new Stroke(paint(), false);
                    current.path.moveTo(x, y);
                    current.path.lineTo(x + 0.1f, y + 0.1f);
                    lastX = x;
                    lastY = y;
                    strokes.add(current);
                    undone.clear();
                    dirty = true;
                    layerCanvas.drawPath(current.path, current.paint);
                    invalidate();
                    notifyHistory();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (current == null) return true;
                    for (int i = 0; i < e.getHistorySize(); i++) {
                        quad(e.getHistoricalX(i), e.getHistoricalY(i));
                    }
                    quad(x, y);
                    redrawCurrent();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (current != null) {
                        current.path.lineTo(x, y);
                        redraw();
                    }
                    current = null;
                    return true;
            }
            return false;
        }

        private void quad(float x, float y) {
            current.path.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f);
            lastX = x;
            lastY = y;
        }

        private void redrawCurrent() {
            if (tool == HIGHLIGHTER || tool == ERASER) redraw();
            else {
                layerCanvas.drawPath(current.path, current.paint);
                invalidate();
            }
        }

        void undo() {
            if (strokes.isEmpty()) return;
            undone.add(strokes.remove(strokes.size() - 1));
            dirty = true;
            redraw();
        }

        void redo() {
            if (undone.isEmpty()) return;
            strokes.add(undone.remove(undone.size() - 1));
            dirty = true;
            redraw();
        }

        void clear() {
            strokes.add(new Stroke(new Paint(), true));
            undone.clear();
            dirty = true;
            redraw();
        }

        boolean canUndo() {
            return !strokes.isEmpty();
        }

        boolean canRedo() {
            return !undone.isEmpty();
        }

        private void notifyHistory() {
            if (getContext() instanceof DrawingActivity) ((DrawingActivity) getContext()).updateHistory();
        }

        Bitmap render() {
            Bitmap out = Bitmap.createBitmap(Math.max(1, getWidth()), Math.max(1, getHeight()), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.drawColor(Color.WHITE);
            if (layer != null) c.drawBitmap(layer, 0, 0, null);
            return out;
        }
    }
}
