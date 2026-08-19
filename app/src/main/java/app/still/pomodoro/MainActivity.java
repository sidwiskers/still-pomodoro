package app.still.pomodoro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Space;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TimerEngine engine;
    private TimerDialView dial;
    private TextView primary;
    private TextView cycle;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean overlayRequested;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            long delay = 1000L - (System.currentTimeMillis() % 1000L) + 12L;
            handler.postDelayed(this, delay);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = new TimerEngine(this);
        configureWindow();
        setContentView(buildContent());
    }

    @Override protected void onResume() {
        super.onResume();
        if (overlayRequested && Settings.canDrawOverlays(this)) {
            overlayRequested = false;
            startOverlay();
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private View buildContent() {
        MaxWidthLinearLayout root = new MaxWidthLinearLayout(this, 620);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 44), Ui.dp(this, 24), Ui.dp(this, 30));

        TextView brand = new TextView(this);
        brand.setText("Still");
        brand.setTextColor(Ui.TEXT);
        brand.setTextSize(17f);
        brand.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        brand.setLetterSpacing(0.02f);
        root.addView(brand, wrap());

        TextView sub = new TextView(this);
        sub.setText("focus without the noise");
        sub.setTextColor(Ui.MUTED);
        sub.setTextSize(12f);
        LinearLayout.LayoutParams subLp = wrap();
        subLp.topMargin = Ui.dp(this, 5);
        root.addView(sub, subLp);

        dial = new TimerDialView(this);
        LinearLayout.LayoutParams dialLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 330));
        dialLp.topMargin = Ui.dp(this, 8);
        dialLp.bottomMargin = Ui.dp(this, 10);
        root.addView(dial, dialLp);

        cycle = new TextView(this);
        cycle.setTextColor(Ui.MUTED);
        cycle.setTextSize(12f);
        cycle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cycleLp = wrap();
        cycleLp.bottomMargin = Ui.dp(this, 18);
        root.addView(cycle, cycleLp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        primary = Ui.pill(this, "Start", true);
        primary.setOnClickListener(v -> {
            Ui.feedback(v);
            requestNotificationsIfNeeded();
            TimerState s = engine.snapshot();
            if (s.running) engine.pause(); else engine.start();
            render();
        });
        controls.addView(primary, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));

        Space gap = new Space(this);
        controls.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));

        TextView reset = Ui.pill(this, "Reset", false);
        reset.setOnClickListener(v -> {
            Ui.feedback(v);
            engine.resetCurrent();
            render();
        });
        controls.addView(reset, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 0.62f));
        root.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout features = new LinearLayout(this);
        features.setOrientation(LinearLayout.HORIZONTAL);
        features.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams featuresLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        featuresLp.topMargin = Ui.dp(this, 12);

        TextView overlay = Ui.pill(this, "Float", false);
        overlay.setOnClickListener(v -> {
            Ui.feedback(v);
            toggleOverlay();
        });
        features.addView(overlay, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));

        Space g2 = new Space(this);
        features.addView(g2, new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));

        TextView clock = Ui.pill(this, "Low power", false);
        clock.setOnClickListener(v -> {
            Ui.feedback(v);
            startActivity(new Intent(this, LowPowerActivity.class));
        });
        features.addView(clock, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));

        Space g3 = new Space(this);
        features.addView(g3, new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));

        TextView settings = Ui.pill(this, "Tune", false);
        settings.setOnClickListener(v -> {
            Ui.feedback(v);
            showSettings();
        });
        features.addView(settings, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 0.8f));

        root.addView(features, featuresLp);

        TextView foot = new TextView(this);
        foot.setText("No account · No network · No background polling");
        foot.setTextColor(Color.rgb(92, 93, 87));
        foot.setTextSize(10f);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footLp = wrap();
        footLp.topMargin = Ui.dp(this, 18);
        root.addView(foot, footLp);

        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(Ui.BG);
        FrameLayout.LayoutParams shellLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
        shell.addView(root, shellLp);
        return shell;
    }

    private void render() {
        TimerState s = engine.snapshot();
        if (s.running && s.remainingMillis <= 0L) s = engine.finishIfDue();
        dial.bind(s, engine.durationFor(s.phase), engine.phaseLabel(s.phase));
        primary.setText(s.running ? "Pause" : "Start");
        cycle.setText("session " + Math.min(engine.cycleSize(), s.focusInCycle + 1) + " of " + engine.cycleSize());
    }

    private void showSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), Ui.dp(this, 6));

        NumberPicker focus = picker(1, 180, engine.focusMinutes());
        NumberPicker shortBreak = picker(1, 60, engine.shortMinutes());
        NumberPicker longBreak = picker(1, 120, engine.longMinutes());
        NumberPicker cycles = picker(1, 12, engine.cycleSize());
        content.addView(settingRow("Focus minutes", focus));
        content.addView(settingRow("Short break", shortBreak));
        content.addView(settingRow("Long break", longBreak));
        content.addView(settingRow("Long break after", cycles));

        CheckBox autoBreak = check("Auto-start breaks", engine.autoBreak());
        CheckBox autoFocus = check("Auto-start focus", engine.autoFocus());
        CheckBox vibrate = check("Completion vibration", engine.vibrateEnabled());
        content.addView(autoBreak);
        content.addView(autoFocus);
        content.addView(vibrate);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Tune Still")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> {
                    engine.setDurations(focus.getValue(), shortBreak.getValue(), longBreak.getValue(), cycles.getValue());
                    engine.setAutoStart(autoBreak.isChecked(), autoFocus.isChecked());
                    engine.setVibrate(vibrate.isChecked());
                    render();
                })
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Ui.ACCENT));
        dialog.show();
    }

    private View settingRow(String title, NumberPicker picker) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Ui.TEXT);
        label.setTextSize(14f);
        row.addView(label, new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f));
        row.addView(picker, new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 64)));
        return row;
    }

    private NumberPicker picker(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
        picker.setWrapSelectorWheel(false);
        return picker;
    }

    private CheckBox check(String text, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(Ui.TEXT);
        c.setTextSize(14f);
        c.setChecked(checked);
        c.setMinHeight(Ui.dp(this, 46));
        return c;
    }

    private void toggleOverlay() {
        if (OverlayService.isRunning()) {
            stopService(new Intent(this, OverlayService.class));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            overlayRequested = true;
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        startOverlay();
    }

    private void startOverlay() {
        Intent intent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4001);
        }
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Ui.BG);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) c.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
        }
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
