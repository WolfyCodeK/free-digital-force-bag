package app.notes;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** All notes and labels, kept in memory and saved to files/keep.json. */
final class Store {

    static final long DAY = 24 * 60 * 60 * 1000L;
    static final long TRASH_DAYS = 7;

    private static Store instance;

    private final Context app;
    private final File file;
    private final File mediaDir;
    private final List<Note> notes = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable saveTask = this::saveNow;
    private long nextId = 1;
    private int loadedVersion;

    static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }

    private Store(Context app) {
        this.app = app;
        file = new File(app.getFilesDir(), "keep.json");
        mediaDir = new File(app.getFilesDir(), "media");
        mediaDir.mkdirs();
        if (!load()) {
            seed();
        } else if (loadedVersion < 2) {
            replaceOldNotes();
        }
        purgeTrash();
    }

    // ------------------------------------------------------------------------------------------

    List<Note> notes() {
        return notes;
    }

    Note get(long id) {
        for (Note n : notes) if (n.id == id) return n;
        return null;
    }

    Note celebrities() {
        for (Note n : notes) if (n.isCelebrities()) return n;
        return null;
    }

    /** A new, empty note at the top of the custom order. Not saved until it has content. */
    Note create() {
        Note n = new Note();
        n.id = nextId++;
        n.created = n.edited = System.currentTimeMillis();
        n.order = maxOrder() + 1;
        notes.add(n);
        return n;
    }

    long maxOrder() {
        long m = 0;
        for (Note n : notes) m = Math.max(m, n.order);
        return m;
    }

    /** A copy of the note as a new note, placed just above the original. */
    Note duplicate(Note src) {
        Note n = src.copy();
        n.id = nextId++;
        n.special = null;
        n.created = n.edited = System.currentTimeMillis();
        n.order = maxOrder() + 1;
        n.reminder = 0;
        n.attachments.clear();
        for (Note.Attachment a : src.attachments) {
            String name = newMediaName(a.file.substring(a.file.lastIndexOf('.')));
            copyFile(media(a.file), media(name));
            n.attachments.add(new Note.Attachment(name, a.kind));
        }
        n.audio.clear();
        for (Note.Audio a : src.audio) {
            String name = newMediaName(".m4a");
            copyFile(media(a.file), media(name));
            n.audio.add(new Note.Audio(name, a.durationMs, a.created));
        }
        notes.add(n);
        changed();
        return n;
    }

    /** Removes a note for good, with its pictures and recordings. */
    void deleteForever(Note n) {
        notes.remove(n);
        for (Note.Attachment a : n.attachments) media(a.file).delete();
        for (Note.Audio a : n.audio) media(a.file).delete();
        Reminders.cancel(app, n);
        changed();
    }

    void purgeTrash() {
        long cutoff = System.currentTimeMillis() - TRASH_DAYS * DAY;
        boolean any = false;
        for (Note n : new ArrayList<>(notes)) {
            if (n.trashed && n.trashedAt < cutoff && !n.isCelebrities()) {
                notes.remove(n);
                for (Note.Attachment a : n.attachments) media(a.file).delete();
                for (Note.Audio a : n.audio) media(a.file).delete();
                any = true;
            }
        }
        if (any) changed();
    }

    // Labels ------------------------------------------------------------------------------------

    List<String> labels() {
        return labels;
    }

    boolean hasLabel(String name) {
        for (String l : labels) if (l.equalsIgnoreCase(name)) return true;
        return false;
    }

    boolean addLabel(String name) {
        name = name.trim();
        if (name.isEmpty() || hasLabel(name)) return false;
        labels.add(name);
        sortLabels();
        changed();
        return true;
    }

    boolean renameLabel(String from, String to) {
        to = to.trim();
        if (to.isEmpty()) return false;
        if (!to.equalsIgnoreCase(from) && hasLabel(to)) return false;
        int i = labels.indexOf(from);
        if (i < 0) return false;
        labels.set(i, to);
        for (Note n : notes) {
            int k = n.labels.indexOf(from);
            if (k >= 0) n.labels.set(k, to);
        }
        sortLabels();
        changed();
        return true;
    }

    void deleteLabel(String name) {
        labels.remove(name);
        for (Note n : notes) n.labels.remove(name);
        changed();
    }

    private void sortLabels() {
        labels.sort(String::compareToIgnoreCase);
    }

    // Media -------------------------------------------------------------------------------------

    File media(String name) {
        return new File(mediaDir, name);
    }

    String newMediaName(String ext) {
        return UUID.randomUUID().toString().replace("-", "") + ext;
    }

    static boolean copyFile(File from, File to) {
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Saving ------------------------------------------------------------------------------------

    /** Saves soon, off the main thread. */
    void changed() {
        main.removeCallbacks(saveTask);
        main.postDelayed(saveTask, 150);
    }

    /** Saves right away (when the app goes to the background). */
    void flush() {
        main.removeCallbacks(saveTask);
        saveNow();
    }

    private void saveNow() {
        final String json;
        try {
            JSONObject root = new JSONObject();
            root.put("version", 2);
            root.put("nextId", nextId);
            root.put("labels", new JSONArray(labels));
            JSONArray arr = new JSONArray();
            for (Note n : notes) {
                if (n.isEmpty() && !n.isCelebrities() && n.edited == n.created) continue;
                arr.put(n.toJson());
            }
            root.put("notes", arr);
            json = root.toString();
        } catch (Exception e) {
            return;
        }
        io.execute(() -> {
            AtomicFile af = new AtomicFile(file);
            FileOutputStream out = null;
            try {
                out = af.startWrite();
                out.write(json.getBytes(StandardCharsets.UTF_8));
                af.finishWrite(out);
            } catch (Exception e) {
                if (out != null) af.failWrite(out);
            }
        });
    }

    private boolean load() {
        AtomicFile af = new AtomicFile(file);
        if (!af.getBaseFile().exists()) return false;
        try {
            String s = new String(af.readFully(), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(s);
            loadedVersion = root.optInt("version", 1);
            nextId = root.optLong("nextId", 1);
            JSONArray lb = root.optJSONArray("labels");
            if (lb != null) for (int i = 0; i < lb.length(); i++) labels.add(lb.getString(i));
            JSONArray arr = root.getJSONArray("notes");
            for (int i = 0; i < arr.length(); i++) {
                Note n = Note.fromJson(arr.getJSONObject(i));
                notes.add(n);
                nextId = Math.max(nextId, n.id + 1);
            }
            return true;
        } catch (Exception e) {
            notes.clear();
            labels.clear();
            return false;
        }
    }

    // First run ---------------------------------------------------------------------------------

    private void seed() {
        long now = System.currentTimeMillis();
        Note c = new Note();
        c.id = nextId++;
        c.special = "celebrities";
        c.title = Celebs.TITLE;
        c.text = Celebs.text(app, Celebs.line(app));
        c.created = c.edited = now - 23 * DAY - 5 * 3600_000L;
        c.order = 100;
        notes.add(c);
        addStarterNotes(now, c.order);
        saveNow();
    }

    /**
     * The first version's starter notes, recognised by title and first line. They are swapped
     * for the current ones once; the user's own notes and the Celebrities note stay.
     */
    private static final String[][] OLD_NOTES = {
            {"Shopping", "Milk"}, {"Gift ideas", "Mum \u2013 scarf"}, {"To do", "Call dentist"},
            {"Car service", "Garage on Friday"}, {"Packing", "Passport"}, {"Pasta bake", "200g pasta"},
            {"Books to read", "The Midnight Library"},
    };

    private void replaceOldNotes() {
        for (Note n : new ArrayList<>(notes)) {
            String first = n.list ? (n.items.isEmpty() ? "" : n.items.get(0).text)
                    : n.text == null ? "" : n.text;
            for (String[] old : OLD_NOTES) {
                if (old[0].equals(n.title) && first.startsWith(old[1])) {
                    if (n.reminder != 0) Reminders.cancel(app, n);
                    notes.remove(n);
                    break;
                }
            }
        }
        Note c = celebrities();
        addStarterNotes(System.currentTimeMillis(), c != null ? c.order : maxOrder() + 1);
        saveNow();
    }

    /** Everyday notes around the Celebrities note: two above it, the rest below. */
    private void addStarterNotes(long now, long celebs) {
        long above = celebs + 2;
        long below = celebs - 1;

        Note shop = list("Shopping", now - 64 * DAY, above--,
                "Chicken thighs", "Eggs", "Spring onions", "Silken tofu", "White miso", "Mirin",
                "Panko", "Kewpie", "Frozen udon", "Shiitake", "Ginger",
                "+Rice", "+Nori", "+Milk", "+Coffee beans");
        shop.edited = now - 20 * 3600_000L;
        notes.add(shop);

        Note work = list("Work", now - 30 * DAY, above--,
                "Fix flaky checkout test", "Review Dan's PR (auth refactor)",
                "Update API docs for /v2/orders", "Ask Priya about swapping on-call",
                "+Submit timesheet", "+Move standup notes to Confluence");
        work.edited = now - 2 * DAY;
        notes.add(work);

        notes.add(text("Oyakodon",
                "2 chicken thighs, bite size\n1 onion, sliced thin\n3 eggs, barely beaten\n\n"
                        + "150ml dashi\n2 tbsp soy\n2 tbsp mirin\n1 tbsp sugar\n\n"
                        + "Onion in the sauce 3-4 min, chicken 5 min, 2/3 of the egg, lid on 30s, "
                        + "rest of the egg, off the heat. Over rice + spring onion",
                now - 12 * DAY, below--));

        notes.add(text("To do",
                "Book dentist\nRenew passport\nCancel Netflix trial\nFix bike puncture\nSend Jake the photos",
                now - 5 * DAY, below--));

        notes.add(text("git",
                "git rebase -i HEAD~3\ngit stash push -m \"wip\"\ngit commit --amend --no-edit\n"
                        + "git reflog (when I've broken everything)\ngit bisect start / good / bad\n"
                        + "git push --force-with-lease, NOT --force",
                now - 34 * DAY, below--));

        notes.add(list("Packing", now - 9 * DAY, below--,
                "Plug adapter (type A)", "Pocket wifi confirmation", "Suica on phone", "Hand towel",
                "Coin purse", "+Passport", "+Charger", "+Headphones"));

        Note rail = text("Renew railcard", "Expires end of the month", now - 3 * DAY, below--);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 4);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        rail.reminder = cal.getTimeInMillis();
        notes.add(rail);

        notes.add(text("Bug - dates off by one",
                "Only for users in BST. toISOString() converts to UTC so anything after 11pm "
                        + "lands on the next day\n\nFix: keep it as a local date string, "
                        + "formatInTimeZone for display\nAdd a test with TZ=Europe/London",
                now - 16 * DAY, below--));

        notes.add(text("Katsu curry",
                "Chicken breast, butterflied\nFlour > egg > panko\nFry 170\u00b0C ~6 min\n\n"
                        + "Sauce: onion, carrot, garlic, ginger, 1 tbsp curry powder, 1 tbsp flour, "
                        + "400ml stock, 1 tsp honey, splash of soy. Blend",
                now - 26 * DAY, below--));

        notes.add(text("Gym",
                "Push - bench 3x8, OHP 3x8, dips, lateral raises\n"
                        + "Pull - pull-ups, rows 3x10, face pulls, curls\n"
                        + "Legs - squat 3x5, RDL 3x8, lunges, calf raises",
                now - 19 * DAY, below--));

        notes.add(text("Retro",
                "Went well: deploys way faster since the pipeline change\n"
                        + "Not so well: Wednesday was all meetings\n"
                        + "Action: PR reviews within 24h, no-meeting Thursday mornings",
                now - 8 * DAY, below--));

        notes.add(list("To watch", now - 40 * DAY, below--,
                "Dandadan", "Vinland Saga S2", "Monster", "Shogun", "+Frieren", "+Severance S2"));

        notes.add(text("Side project ideas",
                "CLI to rename photos by EXIF date\nDiscord bot for the group chat (who's in for 5-a-side)\n"
                        + "Rewrite the portfolio site in Astro\nRecipe scraper -> markdown",
                now - 58 * DAY, below--));

        notes.add(text("Japan trip",
                "Tokyo 5 nights, Kyoto 3, Osaka 2\nJR pass - check if still worth it\n"
                        + "teamLab Planets - book ahead\nFushimi Inari early morning\nKuromon market\n"
                        + "Ichiran, Afuri, Tsuta",
                now - 120 * DAY, below--));

        notes.add(text("Things to learn",
                "Rust - ownership / borrowing properly\nKotlin coroutines\n"
                        + "System design: rate limiter, URL shortener, caching\nPostgres indexes (EXPLAIN ANALYZE)",
                now - 75 * DAY, below--));

        notes.add(text("Miso soup",
                "500ml dashi (1 tsp granules)\n2-3 tbsp miso, whisk in off the boil\n"
                        + "Tofu, wakame, spring onion",
                now - 70 * DAY, below--));

        notes.add(text("Gift ideas",
                "Mum - that cookbook from the market\nDad - bike lights\nJess - Uniqlo voucher",
                now - 45 * DAY, below--));

        notes.add(list("Recipes to try", now - 88 * DAY, below--,
                "Karaage", "Gyoza from scratch", "Nikujaga", "Okonomiyaki", "Chashu",
                "+Tamagoyaki", "+Teriyaki salmon"));

        notes.add(text("Laptop setup",
                "brew install git node pnpm python@3.12\nVS Code: Prettier, ESLint, GitLens, Vim\n"
                        + "zsh + starship\nssh-keygen -t ed25519, add to GitHub",
                now - 95 * DAY, below--));

        notes.add(text("Tamagoyaki",
                "3 eggs\n1 tbsp dashi\n1 tsp sugar\n1 tsp mirin\npinch of salt\n\n"
                        + "Low heat, roll in thirds, oil between layers",
                now - 150 * DAY, below--));

        notes.add(text("Bills",
                "Rent - 1st\nSpotify - 3rd\nCouncil tax - 15th\nPhone - 22nd\nGym - 28th",
                now - 200 * DAY, below--));

        Note meters = text("Old flat meter readings", "Electric 34512\nGas 08812\nTaken 31/03",
                now - 210 * DAY, below--);
        meters.archived = true;
        notes.add(meters);

        Note xmas = list("Christmas", now - 300 * DAY, below--,
                "+Mum", "+Dad", "+Jess", "+Secret Santa (under \u00a320)", "+Cards");
        xmas.archived = true;
        notes.add(xmas);

        Reminders.schedule(app, rail);
    }

    private Note text(String title, String body, long when, long order) {
        Note n = new Note();
        n.id = nextId++;
        n.title = title;
        n.text = body;
        n.created = n.edited = when;
        n.order = order;
        return n;
    }

    /** Items starting with '+' are ticked. */
    private Note list(String title, long when, long order, String... items) {
        Note n = text(title, "", when, order);
        n.list = true;
        long id = 1;
        for (String s : items) {
            boolean checked = s.startsWith("+");
            n.items.add(new Note.Item(id++, checked ? s.substring(1) : s, checked));
        }
        return n;
    }
}
