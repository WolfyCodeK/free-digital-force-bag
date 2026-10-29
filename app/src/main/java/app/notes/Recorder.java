package app.notes;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.Locale;

/** Records a voice note, showing Keep's "Speak now" panel while recording. */
final class Recorder {

    interface Done {
        void on(String file, long durationMs);
    }

    private final AppCompatActivity a;
    private final ActivityResultLauncher<String> permission;
    private Done pending;

    Recorder(AppCompatActivity a) {
        this.a = a;
        permission = a.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            Done d = pending;
            pending = null;
            if (granted && d != null) show(d);
            else if (!granted) {
                View root = a.findViewById(android.R.id.content);
                Snackbar.make(root, "Allow microphone access to record audio", Snackbar.LENGTH_LONG).show();
            }
        });
    }

    void record(Done done) {
        if (a.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            show(done);
        } else {
            pending = done;
            App.external = true;
            permission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    static String formatDuration(long ms) {
        long s = Math.max(0, Math.round(ms / 1000.0));
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60);
    }

    private void show(Done done) {
        Store store = Store.get(a);
        String name = store.newMediaName(".m4a");
        File file = store.media(name);
        MediaRecorder mr = new MediaRecorder(a);
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mr.setAudioSamplingRate(44100);
            mr.setAudioEncodingBitRate(96000);
            mr.setOutputFile(file.getPath());
            mr.prepare();
            mr.start();
        } catch (Exception e) {
            mr.release();
            file.delete();
            View root = a.findViewById(android.R.id.content);
            Snackbar.make(root, "Couldn't start recording", Snackbar.LENGTH_LONG).show();
            return;
        }
        long started = SystemClock.elapsedRealtime();

        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(Ui.dp(a, 24), Ui.dp(a, 28), Ui.dp(a, 24), Ui.dp(a, 36));
        panel.setBackground(Ui.rounded(Ui.color(a, R.color.keep_drawer), Ui.dp(a, 28), 0, 0));
        TextView title = Ui.text(a, "Speak now", 22, Ui.textColor(a), R.font.google_sans_regular);
        panel.addView(title);
        TextView timer = Ui.text(a, "0:00", 14, Ui.muted(a), R.font.google_sans_text_regular);
        timer.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 24));
        panel.addView(timer);

        FrameLayout micBox = new FrameLayout(a);
        View pulse = new View(a);
        pulse.setBackground(Ui.circle(Ui.color(a, R.color.keep_fab_small) & 0x55FFFFFF));
        micBox.addView(pulse, new FrameLayout.LayoutParams(Ui.dp(a, 96), Ui.dp(a, 96), Gravity.CENTER));
        FrameLayout mic = new FrameLayout(a);
        mic.setBackground(Ui.ripple(a, Ui.circle(Ui.color(a, R.color.keep_fab_small))));
        ImageView icon = Ui.icon(a, R.drawable.ic_stop, Ui.color(a, R.color.keep_on_fab_small), 32);
        mic.addView(icon, new FrameLayout.LayoutParams(Ui.dp(a, 32), Ui.dp(a, 32), Gravity.CENTER));
        mic.setContentDescription("Stop recording");
        micBox.addView(mic, new FrameLayout.LayoutParams(Ui.dp(a, 72), Ui.dp(a, 72), Gravity.CENTER));
        panel.addView(micBox, new LinearLayout.LayoutParams(Ui.dp(a, 140), Ui.dp(a, 140)));
        TextView hint = Ui.text(a, "Tap to stop", 14, Ui.muted(a), R.font.google_sans_text_regular);
        hint.setPadding(0, Ui.dp(a, 16), 0, 0);
        panel.addView(hint);

        Handler h = new Handler(Looper.getMainLooper());
        boolean[] stopped = {false};
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (stopped[0]) return;
                long ms = SystemClock.elapsedRealtime() - started;
                timer.setText(formatDuration(ms));
                float level = 0f;
                try {
                    level = Math.min(1f, mr.getMaxAmplitude() / 12000f);
                } catch (IllegalStateException ignored) {
                }
                float s = 1f + 0.45f * level;
                pulse.animate().scaleX(s).scaleY(s).setDuration(90).start();
                h.postDelayed(this, 100);
            }
        };
        Runnable finish = () -> {
            if (stopped[0]) return;
            stopped[0] = true;
            long ms = SystemClock.elapsedRealtime() - started;
            boolean ok = true;
            try {
                mr.stop();
            } catch (RuntimeException e) {
                ok = false;
            }
            mr.release();
            d.dismiss();
            if (!ok || ms < 600) {
                file.delete();
                return;
            }
            done.on(name, ms);
        };
        mic.setOnClickListener(v -> finish.run());
        d.setOnCancelListener(x -> {
            if (stopped[0]) return;
            stopped[0] = true;
            try {
                mr.stop();
            } catch (RuntimeException ignored) {
            }
            mr.release();
            file.delete();
        });
        d.setContentView(panel);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 16);
            lp.y = Ui.dp(a, 24);
            w.setAttributes(lp);
        }
        d.show();
        h.post(tick);
    }
}
