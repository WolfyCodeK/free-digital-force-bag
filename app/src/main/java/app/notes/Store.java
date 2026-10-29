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

    static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }

    private Store(Context app) {
        this.app = app;
        file = new File(app.getFilesDir(), "keep.json");
        mediaDir = new File(app.getFilesDir(), "media");
        mediaDir.mkdirs();
        if (!load()) seed();
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
            root.put("version", 1);
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
        long order = 20;

        Note c = new Note();
        c.id = nextId++;
        c.special = "celebrities";
        c.title = Celebs.TITLE;
        c.text = Celebs.text(app, Celebs.line(app));
        c.created = c.edited = now - 23 * DAY - 5 * 3600_000L;
        c.order = order--;
        notes.add(c);

        Note shop = list("Shopping", now - 2 * DAY, order--,
                "Milk", "Eggs", "Bread", "Coffee", "Apples", "+Washing up liquid", "+Bin bags");
        notes.add(shop);

        Note gifts = text("Gift ideas",
                "Mum – scarf, the grey one\nDad – that cycling book\nSam – headphones\nGrandma – photo calendar",
                now - 9 * DAY, order--);
        notes.add(gifts);

        Note todo = text("To do",
                "Call dentist\nRenew parking permit\nBook flights for June\nReturn library books",
                now - 4 * DAY, order--);
        todo.color = 3;
        notes.add(todo);

        Note car = text("Car service", "Garage on Friday – ask about the brakes", now - 6 * DAY, order--);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 4);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        car.reminder = cal.getTimeInMillis();
        notes.add(car);

        Note packing = list("Packing", now - 15 * DAY, order--,
                "+Passport", "+Phone charger", "Adapter plug", "Sunglasses", "Swimsuit", "Book for the plane");
        notes.add(packing);

        Note pasta = text("Pasta bake",
                "200g pasta\n1 tin chopped tomatoes\n2 cloves garlic\nMozzarella\nFresh basil\n\n200°C for 25 min",
                now - 31 * DAY, order--);
        pasta.color = 2;
        notes.add(pasta);

        Note books = text("Books to read",
                "The Midnight Library\nProject Hail Mary\nKlara and the Sun\nThe Thursday Murder Club",
                now - 47 * DAY, order--);
        notes.add(books);

        saveNow();
        Reminders.schedule(app, car);
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
