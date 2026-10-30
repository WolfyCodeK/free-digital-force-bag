package app.notes;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Note cards for the notes grid and list, with section headers. */
final class NoteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface Listener {
        void onClick(Note n);

        void onLongClick(Note n, RecyclerView.ViewHolder holder);

        /** Whether cards can be swiped away right now. */
        boolean canSwipe();

        /** A card was swiped away (archive or unarchive it). */
        void onSwiped(Note n);
    }

    /** A full-width line of text above a group of notes. */
    static final class Header {
        final String text;
        final boolean banner;

        Header(String text, boolean banner) {
            this.text = text;
            this.banner = banner;
        }
    }

    private static final int TYPE_HEADER = 0, TYPE_NOTE = 1;
    private static final int MAX_LINES = 10;
    private static final int MAX_ITEMS = 9;

    private final Context c;
    private final Listener listener;
    private final Set<Long> selected;
    final List<Object> items = new ArrayList<>();
    boolean grid = true;

    NoteAdapter(Context c, Set<Long> selected, Listener listener) {
        this.c = c;
        this.selected = selected;
        this.listener = listener;
        setHasStableIds(true);
    }

    void submit(List<Object> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        Object o = items.get(position);
        if (o instanceof Note) return ((Note) o).id;
        return -1000L - ((Header) o).text.hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof Note ? TYPE_NOTE : TYPE_HEADER;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        if (type == TYPE_HEADER) {
            TextView t = Ui.text(c, "", 14, Ui.muted(c), R.font.google_sans_medium);
            StaggeredGridLayoutManager.LayoutParams lp =
                    new StaggeredGridLayoutManager.LayoutParams(Ui.MATCH, Ui.WRAP);
            lp.setFullSpan(true);
            t.setLayoutParams(lp);
            return new RecyclerView.ViewHolder(t) {
            };
        }
        return new Card(c);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        Object o = items.get(position);
        if (o instanceof Header) {
            Header hd = (Header) o;
            TextView t = (TextView) h.itemView;
            t.setText(hd.text);
            if (hd.banner) {
                t.setGravity(Gravity.CENTER);
                t.setTypeface(Ui.font(c, R.font.google_sans_text_regular));
                t.setTextSize(15);
                t.setPadding(Ui.dp(c, 24), Ui.dp(c, 18), Ui.dp(c, 24), Ui.dp(c, 18));
            } else {
                t.setGravity(Gravity.START);
                t.setTypeface(Ui.font(c, R.font.google_sans_medium));
                t.setTextSize(14);
                t.setPadding(Ui.dp(c, 17), Ui.dp(c, 14), Ui.dp(c, 17), Ui.dp(c, 8));
            }
            return;
        }
        Note n = (Note) o;
        Card card = (Card) h;
        card.bind(n, selected.contains(n.id), grid);
        card.itemView.setOnClickListener(v -> listener.onClick(n));
        card.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(n, card);
            return true;
        });
        card.itemView.setOnTouchListener(new SwipeAway(n));
    }

    /** Swipe a card sideways to archive it, as in Keep. Clicks and long presses still work. */
    private final class SwipeAway implements View.OnTouchListener {
        private final Note note;
        private float downX, downY;
        private boolean swiping;
        private VelocityTracker velocity;

        SwipeAway(Note note) {
            this.note = note;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    swiping = false;
                    if (velocity != null) velocity.recycle();
                    velocity = VelocityTracker.obtain();
                    velocity.addMovement(e);
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    if (velocity != null) velocity.addMovement(e);
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!swiping) {
                        int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                        if (Math.abs(dx) > slop && Math.abs(dx) > 1.5f * Math.abs(dy) && listener.canSwipe()) {
                            swiping = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            v.cancelLongPress();
                            v.setPressed(false);
                        } else {
                            return false;
                        }
                    }
                    v.setTranslationX(dx);
                    v.setAlpha(Math.max(0.15f, 1f - Math.abs(dx) / Math.max(1, v.getWidth())));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (!swiping) return false;
                    swiping = false;
                    float dx = e.getRawX() - downX;
                    float vx = 0;
                    if (velocity != null) {
                        velocity.addMovement(e);
                        velocity.computeCurrentVelocity(1000);
                        vx = velocity.getXVelocity();
                        velocity.recycle();
                        velocity = null;
                    }
                    float fling = 1000 * v.getResources().getDisplayMetrics().density;
                    boolean away = e.getActionMasked() == MotionEvent.ACTION_UP
                            && (Math.abs(dx) > v.getWidth() * 0.4f
                            || (Math.abs(vx) > fling && Math.signum(vx) == Math.signum(dx)));
                    if (away) {
                        v.animate().translationX(Math.signum(dx) * v.getWidth() * 1.2f).alpha(0f)
                                .setDuration(140).withEndAction(() -> {
                                    v.setTranslationX(0);
                                    v.setAlpha(1f);
                                    listener.onSwiped(note);
                                }).start();
                    } else {
                        v.animate().translationX(0).alpha(1f).setDuration(160).start();
                    }
                    return true;
                }
            }
            return false;
        }
    }

    /** One note card. */
    final class Card extends RecyclerView.ViewHolder {
        final FrameLayout root;
        final LinearLayout box;
        final LinearLayout images;
        final LinearLayout body;
        final TextView title;
        final TextView text;
        final LinearLayout listBox;
        final TextView more;
        final Wrap chips;

        Card(Context c) {
            super(new FrameLayout(c));
            root = (FrameLayout) itemView;
            StaggeredGridLayoutManager.LayoutParams lp =
                    new StaggeredGridLayoutManager.LayoutParams(Ui.MATCH, Ui.WRAP);
            int m = Ui.dp(c, 4);
            lp.setMargins(m, m, m, m);
            root.setLayoutParams(lp);
            root.setClipToOutline(true);

            box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            root.addView(box, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

            images = new LinearLayout(c);
            images.setOrientation(LinearLayout.VERTICAL);
            box.addView(images, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

            body = new LinearLayout(c);
            body.setOrientation(LinearLayout.VERTICAL);
            // Measured on Keep (16sp title, 14sp text): text 18dp in, baselines as Keep's.
            body.setPadding(Ui.dp(c, 17.5f), Ui.dp(c, 18), Ui.dp(c, 17.5f), Ui.dp(c, 19));
            box.addView(body, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

            title = Ui.text(c, "", 16, Ui.textColor(c), R.font.google_sans_medium);
            title.setMaxLines(3);
            title.setEllipsize(TextUtils.TruncateAt.END);
            body.addView(title);

            text = Ui.text(c, "", 14, Ui.textColor(c), R.font.google_sans_text_regular);
            text.setMaxLines(MAX_LINES);
            text.setEllipsize(TextUtils.TruncateAt.END);
            body.addView(text);

            listBox = new LinearLayout(c);
            listBox.setOrientation(LinearLayout.VERTICAL);
            body.addView(listBox);

            more = Ui.text(c, "", 14, Ui.muted(c), R.font.google_sans_text_regular);
            body.addView(more);

            chips = new Wrap(c, Ui.dp(c, 6), Ui.dp(c, 6));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
            cl.topMargin = Ui.dp(c, 12);
            body.addView(chips, cl);
        }

        void bind(Note n, boolean isSelected, boolean grid) {
            Context c = itemView.getContext();
            int fill = Ui.noteColor(c, n.color);
            float radius = Ui.dp(c, 13);
            GradientDrawable bg;
            if (isSelected) {
                bg = Ui.rounded(fill, radius, Ui.textColor(c), Ui.dp(c, 2.5f));
            } else if (n.color == 0) {
                bg = Ui.rounded(fill, radius, Ui.color(c, R.color.keep_stroke), Ui.dp(c, 1));
            } else {
                bg = Ui.rounded(fill, radius, 0, 0);
            }
            root.setBackground(bg);
            root.setForeground(new RippleDrawable(ColorStateList.valueOf(Ui.rippleColor(c)), null,
                    Ui.rounded(Color.WHITE, radius, 0, 0)));
            root.setContentDescription(describe(n));

            bindImages(n, grid);

            boolean hasTitle = !n.title.isEmpty();
            title.setVisibility(hasTitle ? View.VISIBLE : View.GONE);
            title.setText(n.title);

            listBox.removeAllViews();
            more.setVisibility(View.GONE);
            if (n.list) {
                text.setVisibility(View.GONE);
                bindList(n);
            } else {
                boolean hasText = !n.text.isEmpty();
                text.setVisibility(hasText ? View.VISIBLE : View.GONE);
                text.setText(Fmt.styled(n));
                setTop(text, hasTitle ? 12 : 0);
            }
            setTop(listBox, hasTitle ? 10 : 0);

            bindChips(n);
            boolean anyText = hasTitle || (n.list ? !n.items.isEmpty() : !n.text.isEmpty())
                    || chips.getChildCount() > 0;
            body.setVisibility(anyText || n.attachments.isEmpty() ? View.VISIBLE : View.GONE);
            ((LinearLayout.LayoutParams) chips.getLayoutParams()).topMargin =
                    (hasTitle || !n.text.isEmpty() || !n.items.isEmpty()) ? Ui.dp(c, 12) : 0;
        }

        private void setTop(View v, int dp) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
            lp.topMargin = Ui.dp(v.getContext(), dp);
            v.setLayoutParams(lp);
        }

        private void bindList(Note n) {
            Context c = itemView.getContext();
            int shown = 0, ticked = 0;
            boolean bottom = Prefs.moveTickedToBottom(c);
            for (Note.Item it : n.items) {
                if (it.checked && bottom) {
                    ticked++;
                    continue;
                }
                if (shown >= MAX_ITEMS) {
                    shown++;
                    continue;
                }
                listBox.addView(itemRow(c, it));
                shown++;
            }
            int hidden = Math.max(0, shown - MAX_ITEMS);
            StringBuilder sb = new StringBuilder();
            if (hidden > 0) sb.append("…");
            if (ticked > 0) {
                if (sb.length() > 0) sb.append('\n');
                sb.append("+ ").append(ticked).append(' ')
                        .append(c.getString(ticked == 1 ? R.string.ticked_item : R.string.ticked_items));
            }
            if (sb.length() > 0) {
                more.setText(sb);
                more.setVisibility(View.VISIBLE);
                setTop(more, 4);
            }
        }

        private View itemRow(Context c, Note.Item it) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            ImageView box = Ui.icon(c,
                    it.checked ? R.drawable.ic_check_box : R.drawable.ic_check_box_outline_blank,
                    it.checked ? Ui.muted(c) : Ui.textColor(c), 17);
            LinearLayout.LayoutParams bl = (LinearLayout.LayoutParams) box.getLayoutParams();
            bl.topMargin = Ui.dp(c, 2);
            bl.setMarginEnd(Ui.dp(c, 8));
            row.addView(box, bl);
            TextView t = Ui.text(c, it.text, 14, it.checked ? Ui.muted(c) : Ui.textColor(c),
                    R.font.google_sans_text_regular);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            if (it.checked) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            row.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            row.setPadding(0, Ui.dp(c, 1), 0, Ui.dp(c, 1));
            return row;
        }

        private void bindImages(Note n, boolean grid) {
            Context c = itemView.getContext();
            images.removeAllViews();
            if (n.attachments.isEmpty()) return;
            Store store = Store.get(c);
            int total = n.attachments.size();
            int screenW = c.getResources().getDisplayMetrics().widthPixels;
            int cardW = grid ? screenW / 2 : screenW;
            if (total == 1) {
                File f = store.media(n.attachments.get(0).file);
                int[] s = Media.size(f);
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                int h = Math.min((int) (cardW * (s[1] / (float) s[0])), Ui.dp(c, 420));
                images.addView(iv, new LinearLayout.LayoutParams(Ui.MATCH, h));
                if (n.attachments.get(0).kind == Note.DRAWING) iv.setBackgroundColor(Color.WHITE);
                Media.load(iv, f, cardW);
                return;
            }
            int perRow = total == 2 || total == 4 ? 2 : 3;
            LinearLayout row = null;
            int rowH = (cardW - Ui.dp(c, 20)) / perRow;
            for (int i = 0; i < total; i++) {
                if (i % perRow == 0) {
                    row = new LinearLayout(c);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    images.addView(row, new LinearLayout.LayoutParams(Ui.MATCH, rowH));
                }
                Note.Attachment a = n.attachments.get(i);
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (a.kind == Note.DRAWING) iv.setBackgroundColor(Color.WHITE);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.MATCH, 1f);
                if (i % perRow != 0) lp.setMarginStart(Ui.dp(c, 2));
                row.addView(iv, lp);
                Media.load(iv, store.media(a.file), rowH);
            }
        }

        private void bindChips(Note n) {
            Context c = itemView.getContext();
            chips.removeAllViews();
            if (n.reminder > 0) {
                TextView chip = chip(c, Reminders.describe(c, n.reminder),
                        n.repeat != Reminders.REPEAT_NONE ? R.drawable.ic_schedule : R.drawable.ic_alarm);
                if (n.reminderDone && n.repeat == Reminders.REPEAT_NONE) {
                    chip.setPaintFlags(chip.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                }
                chips.addView(chip);
            }
            for (Note.Audio a : n.audio) {
                chips.addView(chip(c, Recorder.formatDuration(a.durationMs), R.drawable.ic_play_arrow));
            }
            for (String l : n.labels) chips.addView(chip(c, l, 0));
            chips.setVisibility(chips.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        }

        private String describe(Note n) {
            StringBuilder sb = new StringBuilder();
            if (n.pinned) sb.append("Pinned. ");
            if (!n.title.isEmpty()) sb.append(n.title).append(". ");
            if (n.list) {
                for (Note.Item i : n.items) sb.append(i.text).append(". ");
            } else {
                sb.append(n.text.length() > 200 ? n.text.substring(0, 200) : n.text);
            }
            return sb.toString();
        }
    }

    /** A small rounded chip, as shown on cards and in the editor. */
    static TextView chip(Context c, String label, int icon) {
        TextView t = Ui.text(c, label, 13, Ui.textColor(c), R.font.google_sans_medium);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMaxWidth(Ui.dp(c, 150));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Ui.dp(c, icon != 0 ? 8 : 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
        t.setBackground(Ui.rounded(Ui.night(c) ? 0x1FFFFFFF : 0x14000000, Ui.dp(c, 8), 0, 0));
        if (icon != 0) {
            android.graphics.drawable.Drawable d = c.getDrawable(icon).mutate();
            int s = Ui.dp(c, 16);
            d.setBounds(0, 0, s, s);
            d.setTint(Ui.textColor(c));
            t.setCompoundDrawablesRelative(d, null, null, null);
            t.setCompoundDrawablePadding(Ui.dp(c, 5));
        }
        t.setMinHeight(Ui.dp(c, 28));
        return t;
    }
}
