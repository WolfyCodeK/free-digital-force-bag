package app.notes;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Knows whether one of this app's screens is in front. Tells GestureService, and when the app
 * is left (home, recents, another app) puts everything back to a normal state: the Celebrities
 * note is restored, secondary screens close and the notes screen starts fresh next time.
 */
public class App extends Application {

    /** Set while the app has sent the user to the camera, a picker or a permission prompt. */
    static boolean external;

    private static int resumed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Activity> alive = new ArrayList<>();
    private final Runnable left = this::onLeft;

    static boolean inFront() {
        return resumed > 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.applyTheme(this);
        Reminders.createChannel(this);
        Celebs.restore(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity a, Bundle b) {
                alive.add(a);
            }

            @Override
            public void onActivityResumed(Activity a) {
                // After a performance with "change the celebrity every time" on, locking the
                // phone primes the next one, so the screen mustn't time out during the reveal.
                if (Force.rotating(a) && !Force.armed(a) && Force.current(a) >= 1) {
                    a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    a.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                if (resumed++ == 0) {
                    handler.removeCallbacks(left);
                    GestureService.setAppInFront(true);
                }
                external = false;
            }

            @Override
            public void onActivityPaused(Activity a) {
                // Moving between two of our own screens pauses one before resuming the next.
                if (--resumed == 0) handler.postDelayed(left, 400);
            }

            @Override
            public void onActivityDestroyed(Activity a) {
                alive.remove(a);
            }

            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
        });
    }

    private void onLeft() {
        if (resumed > 0) return;
        GestureService.setAppInFront(false);
        Store.get(this).flush();
        boolean screenOn = getSystemService(PowerManager.class).isInteractive();
        if (external || !screenOn) return;
        Force.performed(this);
        putAway();
    }

    /**
     * Puts the app back to a plain state: the Celebrities note restored, secondary screens
     * closed and the notes screen starting fresh. Also used while the screen is off, when
     * locking the phone primes the next performance (see GestureService).
     */
    static void putAway(android.content.Context c) {
        ((App) c.getApplicationContext()).putAway();
    }

    private void putAway() {
        Celebs.restore(this);
        Store.get(this).flush();
        for (Activity a : new ArrayList<>(alive)) {
            if (!(a instanceof MainActivity) && !a.isFinishing()) a.finish();
        }
        MainActivity.resetOnReturn = true;
    }
}
