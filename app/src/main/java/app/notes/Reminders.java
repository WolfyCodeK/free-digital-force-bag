package app.notes;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import java.util.Calendar;
import java.util.Locale;

/** Reminder alarms and their notifications. */
final class Reminders {

    static final String CHANNEL = "reminders";
    static final int REPEAT_NONE = 0, REPEAT_DAILY = 1, REPEAT_WEEKLY = 2, REPEAT_MONTHLY = 3, REPEAT_YEARLY = 4;

    private Reminders() {}

    static void createChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Reminders",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Notifications for notes with a reminder");
        nm.createNotificationChannel(ch);
    }

    private static PendingIntent alarmIntent(Context c, Note n, int flags) {
        Intent i = new Intent(c, Receiver.class).setAction("reminder").putExtra("id", n.id);
        return PendingIntent.getBroadcast(c, (int) n.id, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c, Note n) {
        cancel(c, n);
        if (n.reminder <= 0 || n.reminderDone || n.trashed) return;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long at = Math.max(n.reminder, System.currentTimeMillis() + 1000);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at,
                alarmIntent(c, n, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    static void cancel(Context c, Note n) {
        PendingIntent pi = alarmIntent(c, n, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) c.getSystemService(AlarmManager.class).cancel(pi);
    }

    static void scheduleAll(Context c) {
        for (Note n : Store.get(c).notes()) if (n.reminder > 0) schedule(c, n);
    }

    /** "Today, 18:00", "Tomorrow, 08:00", "Mon, 08:00", "12 Nov, 08:00", "12 Nov 2027, 08:00". */
    static String describe(Context c, long when) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(when);
        String time = DateFormat.getTimeFormat(c).format(t.getTime());
        int days = daysBetween(now, t);
        String day;
        if (days == 0) day = "Today";
        else if (days == 1) day = "Tomorrow";
        else if (days == -1) day = "Yesterday";
        else if (days > 1 && days < 7) {
            day = t.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
        } else {
            int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH;
            if (t.get(Calendar.YEAR) == now.get(Calendar.YEAR)) flags |= DateUtils.FORMAT_NO_YEAR;
            else flags |= DateUtils.FORMAT_SHOW_YEAR;
            day = DateUtils.formatDateTime(c, when, flags);
        }
        return day + ", " + time;
    }

    static int daysBetween(Calendar a, Calendar b) {
        Calendar x = (Calendar) a.clone();
        Calendar y = (Calendar) b.clone();
        for (Calendar k : new Calendar[]{x, y}) {
            k.set(Calendar.HOUR_OF_DAY, 12);
            k.set(Calendar.MINUTE, 0);
            k.set(Calendar.SECOND, 0);
            k.set(Calendar.MILLISECOND, 0);
        }
        return (int) Math.round((y.getTimeInMillis() - x.getTimeInMillis()) / (double) Store.DAY);
    }

    static long next(long when, int repeat) {
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(when);
        do {
            switch (repeat) {
                case REPEAT_DAILY: t.add(Calendar.DAY_OF_YEAR, 1); break;
                case REPEAT_WEEKLY: t.add(Calendar.WEEK_OF_YEAR, 1); break;
                case REPEAT_MONTHLY: t.add(Calendar.MONTH, 1); break;
                case REPEAT_YEARLY: t.add(Calendar.YEAR, 1); break;
                default: return when;
            }
        } while (t.getTimeInMillis() < System.currentTimeMillis());
        return t.getTimeInMillis();
    }

    /** Shows the reminder, and reschedules everything after a reboot. */
    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
                scheduleAll(c);
                return;
            }
            Store store = Store.get(c);
            Note n = store.get(intent.getLongExtra("id", -1));
            if (n == null || n.reminder <= 0 || n.trashed) return;
            createChannel(c);
            Intent open = new Intent(c, EditorActivity.class)
                    .putExtra(EditorActivity.EXTRA_ID, n.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content = PendingIntent.getActivity(c, (int) n.id, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String title = n.title.isEmpty() ? "Reminder" : n.title;
            String body = n.list ? n.plainText() : n.text;
            android.app.Notification notif = new android.app.Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_lightbulb)
                    .setColor(0xFFFBBC04)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                    .setContentIntent(content)
                    .setAutoCancel(true)
                    .setWhen(n.reminder)
                    .setShowWhen(true)
                    .build();
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            try {
                nm.notify((int) n.id, notif);
            } catch (SecurityException ignored) {
            }
            if (n.repeat != REPEAT_NONE) {
                n.reminder = next(n.reminder, n.repeat);
                schedule(c, n);
            } else {
                n.reminderDone = true;
            }
            store.flush();
        }
    }
}
