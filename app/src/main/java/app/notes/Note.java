package app.notes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One note. Stored as JSON by Store. */
final class Note {

    static final int IMAGE = 0, DRAWING = 1;
    /** Formatting kinds, stored as [start, end, kind] next to the plain text. */
    static final int BOLD = 1, ITALIC = 2, UNDERLINE = 3, H1 = 4, H2 = 5;

    long id;
    String title = "";
    String text = "";
    final List<int[]> spans = new ArrayList<>();
    boolean list;
    final List<Item> items = new ArrayList<>();
    int color;
    boolean pinned;
    boolean archived;
    boolean trashed;
    long trashedAt;
    final List<String> labels = new ArrayList<>();
    long reminder;
    int repeat;
    boolean reminderDone;
    long created;
    long edited;
    /** Custom order: notes with a higher value come first. */
    long order;
    final List<Attachment> attachments = new ArrayList<>();
    final List<Audio> audio = new ArrayList<>();
    /** "celebrities" for the special note, otherwise null. */
    String special;

    static final class Item {
        long id;
        String text = "";
        boolean checked;

        Item() {}

        Item(long id, String text, boolean checked) {
            this.id = id;
            this.text = text;
            this.checked = checked;
        }
    }

    static final class Attachment {
        String file;
        int kind;

        Attachment(String file, int kind) {
            this.file = file;
            this.kind = kind;
        }
    }

    static final class Audio {
        String file;
        long durationMs;
        long created;

        Audio(String file, long durationMs, long created) {
            this.file = file;
            this.durationMs = durationMs;
            this.created = created;
        }
    }

    boolean isCelebrities() {
        return "celebrities".equals(special);
    }

    boolean isEmpty() {
        if (!title.trim().isEmpty() || !attachments.isEmpty() || !audio.isEmpty()) return false;
        if (list) {
            for (Item i : items) if (!i.text.trim().isEmpty()) return false;
            return true;
        }
        return text.trim().isEmpty();
    }

    boolean hasDrawing() {
        for (Attachment a : attachments) if (a.kind == DRAWING) return true;
        return false;
    }

    boolean hasImage() {
        for (Attachment a : attachments) if (a.kind == IMAGE) return true;
        return false;
    }

    /** Everything a search should look at. */
    String searchText() {
        StringBuilder sb = new StringBuilder(title).append('\n').append(text);
        for (Item i : items) sb.append('\n').append(i.text);
        for (String l : labels) sb.append('\n').append(l);
        return sb.toString().toLowerCase();
    }

    /** The note as plain text, for Send and Make a copy. */
    String plainText() {
        if (!list) return text;
        StringBuilder sb = new StringBuilder();
        for (Item i : items) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(i.checked ? "☑ " : "☐ ").append(i.text);
        }
        return sb.toString();
    }

    Note copy() {
        try {
            return fromJson(toJson());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("text", text);
        JSONArray sp = new JSONArray();
        for (int[] s : spans) sp.put(new JSONArray().put(s[0]).put(s[1]).put(s[2]));
        o.put("spans", sp);
        o.put("list", list);
        JSONArray it = new JSONArray();
        for (Item i : items) {
            it.put(new JSONObject().put("id", i.id).put("text", i.text).put("checked", i.checked));
        }
        o.put("items", it);
        o.put("color", color);
        o.put("pinned", pinned);
        o.put("archived", archived);
        o.put("trashed", trashed);
        o.put("trashedAt", trashedAt);
        o.put("labels", new JSONArray(labels));
        o.put("reminder", reminder);
        o.put("repeat", repeat);
        o.put("reminderDone", reminderDone);
        o.put("created", created);
        o.put("edited", edited);
        o.put("order", order);
        JSONArray at = new JSONArray();
        for (Attachment a : attachments) at.put(new JSONObject().put("file", a.file).put("kind", a.kind));
        o.put("attachments", at);
        JSONArray au = new JSONArray();
        for (Audio a : audio) {
            au.put(new JSONObject().put("file", a.file).put("duration", a.durationMs).put("created", a.created));
        }
        o.put("audio", au);
        if (special != null) o.put("special", special);
        return o;
    }

    static Note fromJson(JSONObject o) throws JSONException {
        Note n = new Note();
        n.id = o.getLong("id");
        n.title = o.optString("title");
        n.text = o.optString("text");
        JSONArray sp = o.optJSONArray("spans");
        if (sp != null) {
            for (int k = 0; k < sp.length(); k++) {
                JSONArray s = sp.getJSONArray(k);
                n.spans.add(new int[]{s.getInt(0), s.getInt(1), s.getInt(2)});
            }
        }
        n.list = o.optBoolean("list");
        JSONArray it = o.optJSONArray("items");
        if (it != null) {
            for (int k = 0; k < it.length(); k++) {
                JSONObject i = it.getJSONObject(k);
                n.items.add(new Item(i.optLong("id"), i.optString("text"), i.optBoolean("checked")));
            }
        }
        n.color = o.optInt("color");
        n.pinned = o.optBoolean("pinned");
        n.archived = o.optBoolean("archived");
        n.trashed = o.optBoolean("trashed");
        n.trashedAt = o.optLong("trashedAt");
        JSONArray lb = o.optJSONArray("labels");
        if (lb != null) for (int k = 0; k < lb.length(); k++) n.labels.add(lb.getString(k));
        n.reminder = o.optLong("reminder");
        n.repeat = o.optInt("repeat");
        n.reminderDone = o.optBoolean("reminderDone");
        n.created = o.optLong("created");
        n.edited = o.optLong("edited");
        n.order = o.optLong("order");
        JSONArray at = o.optJSONArray("attachments");
        if (at != null) {
            for (int k = 0; k < at.length(); k++) {
                JSONObject a = at.getJSONObject(k);
                n.attachments.add(new Attachment(a.getString("file"), a.optInt("kind")));
            }
        }
        JSONArray au = o.optJSONArray("audio");
        if (au != null) {
            for (int k = 0; k < au.length(); k++) {
                JSONObject a = au.getJSONObject(k);
                n.audio.add(new Audio(a.getString("file"), a.optLong("duration"), a.optLong("created")));
            }
        }
        n.special = o.has("special") ? o.getString("special") : null;
        return n;
    }
}
