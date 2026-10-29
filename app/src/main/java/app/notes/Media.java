package app.notes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads pictures off the main thread and keeps recent ones in memory. */
final class Media {

    private static final LruCache<String, Bitmap> cache =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap b) {
                    return b.getByteCount();
                }
            };
    private static final ExecutorService pool = Executors.newFixedThreadPool(2);
    private static final Handler main = new Handler(Looper.getMainLooper());

    private Media() {}

    /** Shows the picture in the view, scaled down to about the given width. */
    static void load(ImageView v, File f, int width) {
        String key = f.getPath() + "@" + width + "#" + f.lastModified();
        v.setTag(R.id.media_key, key);
        Bitmap b = cache.get(key);
        if (b != null) {
            v.setImageBitmap(b);
            return;
        }
        v.setImageDrawable(null);
        pool.execute(() -> {
            Bitmap d = decode(f, width);
            if (d == null) return;
            cache.put(key, d);
            main.post(() -> {
                if (key.equals(v.getTag(R.id.media_key))) v.setImageBitmap(d);
            });
        });
    }

    static void forget(File f) {
        String prefix = f.getPath() + "@";
        for (String k : cache.snapshot().keySet()) if (k.startsWith(prefix)) cache.remove(k);
    }

    /** Width and height of the picture without decoding it. */
    static int[] size(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        return new int[]{Math.max(1, o.outWidth), Math.max(1, o.outHeight)};
    }

    static Bitmap decode(File f, int width) {
        if (!f.exists()) return null;
        int[] s = size(f);
        int sample = 1;
        while (s[0] / (sample * 2) >= width) sample *= 2;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /**
     * Copies a picked or captured picture into the app's own storage, upright and at most
     * 2048 px on its long side. Returns the new file name, or null.
     */
    static String importImage(Context c, Uri uri) {
        Store store = Store.get(c);
        String name = store.newMediaName(".jpg");
        File out = store.media(name);
        try {
            File tmp = new File(c.getCacheDir(), "import");
            try (InputStream in = c.getContentResolver().openInputStream(uri);
                 FileOutputStream o = new FileOutputStream(tmp)) {
                if (in == null) return null;
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
            }
            Bitmap b = decode(tmp, 2048);
            if (b == null) return null;
            int rotation = 0;
            try {
                int orient = new ExifInterface(tmp.getPath()).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orient == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (orient == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (orient == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            } catch (Exception ignored) {
            }
            float scale = Math.min(1f, 2048f / Math.max(b.getWidth(), b.getHeight()));
            if (rotation != 0 || scale < 1f) {
                Matrix m = new Matrix();
                m.postScale(scale, scale);
                m.postRotate(rotation);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
            try (FileOutputStream o = new FileOutputStream(out)) {
                b.compress(Bitmap.CompressFormat.JPEG, 90, o);
            }
            tmp.delete();
            return name;
        } catch (Exception e) {
            out.delete();
            return null;
        }
    }
}
