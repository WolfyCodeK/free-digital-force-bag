package app.notes;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The "Celebrities" note: a numbered list of names from assets/celebrities.txt, with the forced
 * name (line 1 of that file, or the name set on the hidden settings screen) on the line given by
 * the captured number.
 *
 * <p>How the note behaves, so that nobody can break the trick by touching it:
 * <ul>
 *   <li>Whenever the notes screen shows and a new number has been captured since the list was
 *   last built, the list is rebuilt with the forced name on that line. Without any number, the
 *   forced name goes on a random line, which then stays put like any real note.</li>
 *   <li>Inside the app the note behaves like any other note: it can be edited, coloured,
 *   labelled, archived or deleted, and those changes show while the app stays open.</li>
 *   <li>As soon as the app is left (home, recents or another app), the note is put back exactly
 *   as it was: same title, the clean list for the current number, in Notes, default colour, no
 *   labels or reminder. Copies made with "Make a copy" are ordinary notes and are kept.</li>
 * </ul>
 */
final class Celebs {

    static final String TITLE = "Celebrities";

    private static List<String> names;
    /** True once someone edits the note in this visit to the app. */
    static boolean touched;

    private Celebs() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("c", Context.MODE_PRIVATE);
    }

    /** The names from celebrities.txt; the first is the forced one. */
    static synchronized List<String> names(Context c) {
        if (names != null) return names;
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                c.getAssets().open("celebrities.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) out.add(line);
            }
        } catch (Exception e) {
            throw new IllegalStateException("celebrities.txt is missing", e);
        }
        names = out;
        return names;
    }

    static int count(Context c) {
        return Math.min(100, names(c).size());
    }

    /** The whole list with the forced name on the given line (1-based). */
    static String text(Context c, int line) {
        List<String> all = names(c);
        String forced = Force.forcedName(c);
        // If the forced name is one of the fillers, the default forced name takes its place, so
        // no name is on the list twice.
        List<String> fillers = new ArrayList<>(all.subList(1, all.size()));
        for (int i = 0; i < fillers.size(); i++) {
            if (fillers.get(i).equalsIgnoreCase(forced)) fillers.set(i, all.get(0));
        }
        int count = count(c);
        line = Math.max(1, Math.min(count, line));
        StringBuilder sb = new StringBuilder();
        int filler = 0;
        for (int i = 1; i <= count; i++) {
            String name = i == line ? forced : fillers.get(filler++);
            sb.append(i).append(". ").append(name);
            if (i < count) sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * The line the forced name is on. Takes a newly captured number if there is one, otherwise
     * keeps the previous line, otherwise picks a random line once.
     */
    static int line(Context c) {
        SharedPreferences p = prefs(c);
        int n = Force.current(c);
        long t = Force.capturedAt(c);
        if (n >= 1 && n <= 100 && t != p.getLong("t", -1)) {
            p.edit().putInt("n", n).putLong("t", t).apply();
            return n;
        }
        int stored = p.getInt("n", -1);
        if (stored >= 1) return stored;
        int random = 1 + new Random().nextInt(Math.max(1, count(c)));
        p.edit().putInt("n", random).putLong("t", 0).apply();
        return random;
    }

    /** The line the forced name is on right now, or -1 if none has been chosen yet. */
    static int currentLine(Context c) {
        return prefs(c).getInt("n", -1);
    }

    /** Forgets the current line, so the next showing picks a new random one. */
    static void forgetLine(Context c) {
        prefs(c).edit().clear().apply();
    }

    /** Brings the note up to date before it is shown. */
    static void refresh(Context c) {
        Store store = Store.get(c);
        Note note = store.celebrities();
        if (note == null) {
            restore(c);
            return;
        }
        long before = prefs(c).getLong("t", -1);
        int n = line(c);
        boolean newNumber = prefs(c).getLong("t", -1) != before;
        String want = text(c, n);
        if (newNumber || (!touched && !note.list && !want.equals(note.text))) {
            note.text = want;
            note.spans.clear();
            note.list = false;
            note.items.clear();
            if (newNumber) touched = false;
            store.changed();
        }
    }

    /** Puts the note back to its clean state; called whenever the app is left. */
    static void restore(Context c) {
        Store store = Store.get(c);
        Note note = store.celebrities();
        SharedPreferences p = prefs(c);
        if (note == null) {
            note = store.create();
            note.special = "celebrities";
            note.created = System.currentTimeMillis() - 23 * Store.DAY;
            p.edit().putLong("edited", note.created).apply();
        }
        if (!p.contains("edited")) p.edit().putLong("edited", note.edited).apply();
        note.title = TITLE;
        note.text = text(c, line(c));
        note.spans.clear();
        note.list = false;
        note.items.clear();
        note.color = 0;
        note.archived = false;
        note.trashed = false;
        note.trashedAt = 0;
        note.labels.clear();
        if (note.reminder != 0) Reminders.cancel(c, note);
        note.reminder = 0;
        note.reminderDone = false;
        for (Note.Attachment a : note.attachments) store.media(a.file).delete();
        note.attachments.clear();
        for (Note.Audio a : note.audio) store.media(a.file).delete();
        note.audio.clear();
        note.edited = p.getLong("edited", note.created);
        touched = false;
        store.changed();
    }
}
