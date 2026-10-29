package app.notes;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/** Knows whether one of this app's screens is in front, and tells GestureService. */
public class App extends Application {

    private static int resumed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable left = () -> GestureService.setAppInFront(false);

    static boolean inFront() {
        return resumed > 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity a) {
                if (resumed++ == 0) {
                    handler.removeCallbacks(left);
                    GestureService.setAppInFront(true);
                }
            }

            @Override
            public void onActivityPaused(Activity a) {
                // Moving between two of our own screens pauses one before resuming the next.
                if (--resumed == 0) handler.postDelayed(left, 400);
            }

            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
}
