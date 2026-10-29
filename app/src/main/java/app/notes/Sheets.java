package app.notes;

import android.Manifest;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

/** Bottom sheets and dialogs used by more than one screen. */
final class Sheets {

    interface IntCallback {
        void on(int value);
    }

    interface StringCallback {
        void on(String value);
    }

    interface ReminderCallback {
        /** when == 0 removes the reminder. */
        void on(long when, int repeat);
    }

    private Sheets() {}

    /** A bottom sheet in the given colour, with the content stacked vertically. */
    static BottomSheetDialog sheet(Context c, int bg, View content) {
        BottomSheetDialog d = new BottomSheetDialog(c);
        d.setContentView(content);
        View sheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) sheet.setBackgroundTintList(ColorStateList.valueOf(bg));
        d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        d.getBehavior().setSkipCollapsed(true);
        return d;
    }

    static LinearLayout column(Context c) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 20));
        return col;
    }

    // Colours ----------------------------------------------------------------------------------

    static void colors(AppCompatActivity a, int current, int bg, IntCallback cb) {
        LinearLayout col = column(a);
        TextView title = Ui.text(a, a.getString(R.string.colour), 16, Ui.textColor(a), R.font.google_sans_medium);
        title.setPadding(Ui.dp(a, 24), Ui.dp(a, 12), Ui.dp(a, 24), Ui.dp(a, 12));
        col.addView(title);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(a, 18), Ui.dp(a, 4), Ui.dp(a, 18), Ui.dp(a, 8));
        hs.addView(row);
        col.addView(hs);
        BottomSheetDialog[] holder = new BottomSheetDialog[1];
        int[] chosen = {current};
        View[] dots = new View[Ui.COLOR_NAMES.length];
        for (int i = 0; i < Ui.COLOR_NAMES.length; i++) {
            final int index = i;
            FrameLayout dot = new FrameLayout(a);
            dot.setContentDescription(Ui.COLOR_NAMES[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 52), Ui.dp(a, 52));
            lp.setMargins(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
            row.addView(dot, lp);
            dots[i] = dot;
            dot.setOnClickListener(v -> {
                chosen[0] = index;
                for (int k = 0; k < dots.length; k++) styleDot(a, (FrameLayout) dots[k], k, k == index);
                cb.on(index);
            });
            styleDot(a, dot, i, i == current);
        }
        BottomSheetDialog d = sheet(a, bg, col);
        holder[0] = d;
        d.show();
    }

    private static void styleDot(Context c, FrameLayout dot, int index, boolean selected) {
        dot.removeAllViews();
        int fill = Ui.noteColor(c, index);
        int stroke = selected ? Ui.color(c, R.color.keep_primary) : Ui.color(c, R.color.keep_stroke);
        float sw = Ui.dp(c, selected ? 2.5f : 1.5f);
        dot.setBackground(Ui.ripple(c, Ui.rounded(fill, Ui.dp(c, 26), stroke, sw)));
        int icon = selected ? R.drawable.ic_check : index == 0 ? R.drawable.ic_format_color_reset : 0;
        if (icon != 0) {
            ImageView iv = Ui.icon(c, icon, Ui.textColor(c), 22);
            dot.addView(iv, new FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER));
        }
        dot.setSelected(selected);
    }

    // Reminders --------------------------------------------------------------------------------

    static void reminder(AppCompatActivity a, Note existing, int bg, ReminderCallback cb) {
        askNotificationPermission(a);
        LinearLayout col = column(a);
        TextView title = Ui.text(a, "Remind me later", 16, Ui.textColor(a), R.font.google_sans_medium);
        title.setPadding(Ui.dp(a, 24), Ui.dp(a, 12), Ui.dp(a, 24), Ui.dp(a, 8));
        col.addView(title);
        BottomSheetDialog[] d = new BottomSheetDialog[1];
        Calendar now = Calendar.getInstance();
        int evening = Prefs.evening(a), morning = Prefs.morning(a);
        if (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) < evening - 30) {
            long t = at(0, evening);
            col.addView(timeRow(a, "Later today", t, v -> {
                d[0].dismiss();
                cb.on(t, Reminders.REPEAT_NONE);
            }));
        }
        long tomorrow = at(1, morning);
        col.addView(timeRow(a, "Tomorrow morning", tomorrow, v -> {
            d[0].dismiss();
            cb.on(tomorrow, Reminders.REPEAT_NONE);
        }));
        int dow = now.get(Calendar.DAY_OF_WEEK);
        int toMonday = (Calendar.MONDAY - dow + 7) % 7;
        if (toMonday == 0) toMonday = 7;
        if (toMonday > 1) {
            long monday = at(toMonday, morning);
            col.addView(timeRow(a, "Monday morning", monday, v -> {
                d[0].dismiss();
                cb.on(monday, Reminders.REPEAT_NONE);
            }));
        }
        col.addView(Ui.row(a, R.drawable.ic_schedule, "Choose a date & time", Ui.textColor(a), v -> {
            d[0].dismiss();
            dateTime(a, existing, cb);
        }));
        if (existing != null && existing.reminder > 0) {
            col.addView(Ui.row(a, R.drawable.ic_delete, "Delete reminder", Ui.textColor(a), v -> {
                d[0].dismiss();
                cb.on(0, Reminders.REPEAT_NONE);
            }));
        }
        d[0] = sheet(a, bg, col);
        d[0].show();
    }

    private static View timeRow(Context c, String label, long when, View.OnClickListener click) {
        LinearLayout r = Ui.row(c, R.drawable.ic_schedule, label, Ui.textColor(c), click);
        TextView t = Ui.text(c, DateFormat.getTimeFormat(c).format(when), 14, Ui.muted(c),
                R.font.google_sans_text_regular);
        r.addView(t);
        return r;
    }

    private static long at(int daysAhead, int minutes) {
        Calendar t = Calendar.getInstance();
        t.add(Calendar.DAY_OF_YEAR, daysAhead);
        t.set(Calendar.HOUR_OF_DAY, minutes / 60);
        t.set(Calendar.MINUTE, minutes % 60);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        return t.getTimeInMillis();
    }

    static void askNotificationPermission(AppCompatActivity a) {
        if (a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            App.external = true;
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    /** Keep's "Choose a date & time" dialog: date, time and repeat. */
    static void dateTime(AppCompatActivity a, Note existing, ReminderCallback cb) {
        Calendar when = Calendar.getInstance();
        if (existing != null && existing.reminder > 0) {
            when.setTimeInMillis(existing.reminder);
        } else {
            when.setTimeInMillis(at(1, Prefs.morning(a)));
        }
        int[] repeat = {existing != null ? existing.repeat : Reminders.REPEAT_NONE};
        String[] repeats = {"Does not repeat", "Daily", "Weekly", "Monthly", "Yearly"};

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), 0);
        TextView date = field(a);
        TextView time = field(a);
        TextView rep = field(a);
        Runnable refresh = () -> {
            int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_YEAR;
            int days = Reminders.daysBetween(Calendar.getInstance(), when);
            date.setText(days == 0 ? "Today" : days == 1 ? "Tomorrow"
                    : DateUtils.formatDateTime(a, when.getTimeInMillis(), flags));
            time.setText(DateFormat.getTimeFormat(a).format(when.getTime()));
            rep.setText(repeats[repeat[0]]);
        };
        refresh.run();
        date.setOnClickListener(v -> {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.clear();
            utc.set(when.get(Calendar.YEAR), when.get(Calendar.MONTH), when.get(Calendar.DAY_OF_MONTH));
            MaterialDatePicker<Long> p = MaterialDatePicker.Builder.datePicker()
                    .setSelection(utc.getTimeInMillis()).build();
            p.addOnPositiveButtonClickListener(sel -> {
                Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                u.setTimeInMillis(sel);
                when.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH));
                refresh.run();
            });
            p.show(a.getSupportFragmentManager(), "date");
        });
        time.setOnClickListener(v -> {
            MaterialTimePicker p = new MaterialTimePicker.Builder()
                    .setTimeFormat(DateFormat.is24HourFormat(a) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                    .setHour(when.get(Calendar.HOUR_OF_DAY))
                    .setMinute(when.get(Calendar.MINUTE))
                    .build();
            p.addOnPositiveButtonClickListener(x -> {
                when.set(Calendar.HOUR_OF_DAY, p.getHour());
                when.set(Calendar.MINUTE, p.getMinute());
                when.set(Calendar.SECOND, 0);
                when.set(Calendar.MILLISECOND, 0);
                refresh.run();
            });
            p.show(a.getSupportFragmentManager(), "time");
        });
        rep.setOnClickListener(v -> new MaterialAlertDialogBuilder(a)
                .setSingleChoiceItems(repeats, repeat[0], (d, which) -> {
                    repeat[0] = which;
                    refresh.run();
                    d.dismiss();
                })
                .show());
        col.addView(date);
        col.addView(time);
        col.addView(rep);
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a)
                .setTitle("Choose date & time")
                .setView(col)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> cb.on(when.getTimeInMillis(), repeat[0]));
        if (existing != null && existing.reminder > 0) {
            b.setNeutralButton("Delete", (d, w) -> cb.on(0, Reminders.REPEAT_NONE));
        }
        b.show();
    }

    private static TextView field(Context c) {
        TextView t = Ui.text(c, "", 16, Ui.textColor(c), R.font.google_sans_text_regular);
        t.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        android.graphics.drawable.Drawable arrow = c.getDrawable(R.drawable.ic_keyboard_arrow_down).mutate();
        arrow.setTint(Ui.muted(c));
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrow, null);
        t.setBackground(Ui.rippleRounded(c, Ui.dp(c, 8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        lp.setMargins(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        t.setLayoutParams(lp);
        return t;
    }

    // Prompt -----------------------------------------------------------------------------------

    static void prompt(AppCompatActivity a, String title, String initial, StringCallback cb) {
        EditText e = new EditText(a);
        e.setText(initial);
        e.setSingleLine(true);
        e.setSelectAllOnFocus(true);
        e.setTypeface(Ui.font(a, R.font.google_sans_text_regular));
        FrameLayout box = new FrameLayout(a);
        box.setPadding(Ui.dp(a, 24), Ui.dp(a, 8), Ui.dp(a, 24), 0);
        box.addView(e);
        new MaterialAlertDialogBuilder(a)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Done", (d, w) -> cb.on(e.getText().toString()))
                .show();
        e.requestFocus();
    }

    // Send -------------------------------------------------------------------------------------

    static void send(Context c, Note n) {
        Intent i = new Intent();
        String body = n.plainText();
        String text = n.title.isEmpty() ? body : body.isEmpty() ? n.title : n.title + "\n" + body;
        ArrayList<Uri> uris = new ArrayList<>();
        for (Note.Attachment at : n.attachments) {
            try {
                uris.add(FileProvider.getUriForFile(c, c.getPackageName() + ".files",
                        Store.get(c).media(at.file)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (uris.isEmpty()) {
            i.setAction(Intent.ACTION_SEND).setType("text/plain");
        } else if (uris.size() == 1) {
            i.setAction(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            i.setAction(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        if (!n.title.isEmpty()) i.putExtra(Intent.EXTRA_SUBJECT, n.title);
        i.putExtra(Intent.EXTRA_TEXT, text);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        App.external = true;
        c.startActivity(Intent.createChooser(i, null));
    }

    // Account ----------------------------------------------------------------------------------

    /** The Google account card that opens from the profile picture. */
    static void account(AppCompatActivity a) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(Ui.color(a, R.color.keep_drawer), Ui.dp(a, 28), 0, 0));
        card.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 12));

        FrameLayout top = new FrameLayout(a);
        android.widget.ImageButton close = Ui.iconButton(a, R.drawable.ic_close, "Close", Ui.textColor(a));
        top.addView(close, new FrameLayout.LayoutParams(Ui.dp(a, 48), Ui.dp(a, 48), Gravity.START | Gravity.CENTER_VERTICAL));
        TextView logo = Ui.text(a, google(), 22, Ui.textColor(a), R.font.google_sans_medium);
        top.addView(logo, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        top.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 8), 0);
        card.addView(top, new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(a, 56)));
        close.setOnClickListener(v -> d.dismiss());

        LinearLayout acct = new LinearLayout(a);
        acct.setOrientation(LinearLayout.VERTICAL);
        acct.setGravity(Gravity.CENTER_HORIZONTAL);
        acct.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 16));
        acct.setBackground(Ui.rounded(Ui.color(a, R.color.keep_card), Ui.dp(a, 20), 0, 0));
        AvatarView av = new AvatarView(a);
        av.setClickable(false);
        av.setBackground(null);
        acct.addView(av, new LinearLayout.LayoutParams(Ui.dp(a, 76), Ui.dp(a, 76)));
        String name = AvatarView.Account.name(a), email = AvatarView.Account.email(a);
        if (!name.isEmpty()) {
            TextView hi = Ui.text(a, "Hi, " + name.split(" ")[0] + "!", 22, Ui.textColor(a), R.font.google_sans_regular);
            hi.setPadding(0, Ui.dp(a, 10), 0, 0);
            acct.addView(hi);
        }
        if (!email.isEmpty()) {
            TextView em = Ui.text(a, email, 14, Ui.muted(a), R.font.google_sans_text_regular);
            em.setPadding(0, Ui.dp(a, 2), 0, 0);
            acct.addView(em);
        }
        TextView manage = Ui.text(a, "Manage your Google Account", 14, Ui.textColor(a), R.font.google_sans_medium);
        manage.setGravity(Gravity.CENTER);
        manage.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), 0);
        manage.setBackground(Ui.ripple(a, Ui.rounded(Color.TRANSPARENT, Ui.dp(a, 20),
                Ui.color(a, R.color.keep_outline), Ui.dp(a, 1))));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(a, 40));
        ml.topMargin = Ui.dp(a, 16);
        acct.addView(manage, ml);
        manage.setOnClickListener(v -> {
            d.dismiss();
            Intent i = new Intent("com.google.android.gms.accountsettings.action.VIEW_SETTINGS")
                    .setPackage("com.google.android.gms");
            if (!email.isEmpty()) i.putExtra("extra.accountName", email);
            if (!launch(a, i)) launch(a, new Intent(Settings.ACTION_SYNC_SETTINGS));
        });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        al.setMargins(Ui.dp(a, 12), Ui.dp(a, 4), Ui.dp(a, 12), Ui.dp(a, 8));
        card.addView(acct, al);

        card.addView(Ui.row(a, R.drawable.ic_person_add, "Add another account", Ui.textColor(a), v -> {
            d.dismiss();
            launch(a, new Intent(Settings.ACTION_ADD_ACCOUNT)
                    .putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[]{"com.google"}));
        }));
        card.addView(Ui.row(a, R.drawable.ic_manage_accounts, "Manage accounts on this device", Ui.textColor(a), v -> {
            d.dismiss();
            launch(a, new Intent(Settings.ACTION_SYNC_SETTINGS));
        }));
        View div = new View(a);
        div.setBackgroundColor(Ui.color(a, R.color.keep_stroke));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(a, 1));
        dl.setMargins(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        card.addView(div, dl);
        LinearLayout links = new LinearLayout(a);
        links.setGravity(Gravity.CENTER);
        TextView privacy = Ui.text(a, "Privacy Policy", 12, Ui.muted(a), R.font.google_sans_text_regular);
        TextView dot = Ui.text(a, "  •  ", 12, Ui.muted(a), R.font.google_sans_text_regular);
        TextView terms = Ui.text(a, "Terms of Service", 12, Ui.muted(a), R.font.google_sans_text_regular);
        privacy.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        terms.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        privacy.setOnClickListener(v -> launch(a, new Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy"))));
        terms.setOnClickListener(v -> launch(a, new Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/terms"))));
        links.addView(privacy);
        links.addView(dot);
        links.addView(terms);
        card.addView(links);

        d.setContentView(card);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.TOP);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = Math.min(a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 24), Ui.dp(a, 480));
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.y = Ui.dp(a, 56);
            w.setAttributes(lp);
        }
        d.show();
    }

    /** "Google" in its four colours. */
    private static CharSequence google() {
        SpannableString s = new SpannableString("Google");
        int[] colors = {0xFF4285F4, 0xFFEA4335, 0xFFFBBC04, 0xFF4285F4, 0xFF34A853, 0xFFEA4335};
        for (int i = 0; i < 6; i++) {
            s.setSpan(new ForegroundColorSpan(colors[i]), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    static boolean launch(Context c, Intent i) {
        try {
            App.external = true;
            c.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            App.external = false;
            return false;
        }
    }
}
