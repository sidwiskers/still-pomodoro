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
            engine.reset();
            render();
        });
        controls.addView(reset, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));
        root.addView(controls, matchWrap());

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams modesLp = matchWrap();
        modesLp.topMargin = Ui.dp(this, 12);

        TextView lowPower = Ui.pill(this, "Low power", false);
        lowPower.setOnClickListener(v -> {
            Ui.feedback(v);
            startActivity(new Intent(this, LowPowerActivity.class));
        });
        modes.addView(lowPower, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));

        Space modeGap = new Space(this);
        modes.addView(modeGap, new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));

        TextView overlay = Ui.pill(this, "Float", false);
        overlay.setOnClickListener(v -> {
            Ui.feedback(v);
            toggleOverlay();
        });
        modes.addView(overlay, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        root.addView(modes, modesLp);

        TextView settings = new TextView(this);
        settings.setText("Timing");
        settings.setTextColor(Ui.MUTED);
        settings.setGravity(Gravity.CENTER);
        settings.setTextSize(12f);
        settings.setPadding(Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 14), Ui.dp(this, 15));
        settings.setBackground(Ui.outline(this, 18));
        settings.setOnClickListener(v -> {
            Ui.feedback(v);
            showSettings();
        });
        LinearLayout.LayoutParams settingsLp = matchWrap();
        settingsLp.topMargin = Ui.dp(this, 12);
        root.addView(settings, settingsLp);

        FrameLayout viewport = new FrameLayout(this);
        viewport.setBackgroundColor(Ui.BG);
        viewport.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        viewport.setFitsSystemWindows(false);
        return viewport;
    }

    private void render() {
        if (engine == null || dial == null || primary == null || cycle == null) return;
        TimerState state = engine.snapshot();
        long remaining = state.remainingNow();
        long total = engine.durationFor(state.phase);
        float progress = total <= 0L ? 0f : 1f - Math.min(1f, Math.max(0f, remaining / (float) total));
        dial.setState(engine.phaseLabel(state.phase), remaining, progress, state.running);
        primary.setText(state.running ? "Pause" : (remaining < total ? "Resume" : "Start"));
        cycle.setText("cycle " + (Math.min(engine.cycleLength(), state.focusesInCycle + 1)) + " of " + engine.cycleLength());
    }

    private void showSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);

        NumberPicker focus = picker(1, 120, (int) (engine.focusMillis() / 60000L));
        NumberPicker shortBreak = picker(1, 60, (int) (engine.shortBreakMillis() / 60000L));
        NumberPicker longBreak = picker(1, 120, (int) (engine.longBreakMillis() / 60000L));
        NumberPicker cycleLength = picker(2, 12, engine.cycleLength());

        content.addView(label("Focus minutes"));
        content.addView(focus);
        content.addView(label("Short break minutes"));
        content.addView(shortBreak);
        content.addView(label("Long break minutes"));
        content.addView(longBreak);
        content.addView(label("Long break every"));
        content.addView(cycleLength);

        CheckBox auto = new CheckBox(this);
        auto.setText("Start next phase automatically");
        auto.setTextColor(Ui.TEXT);
        auto.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        auto.setChecked(engine.autoStart());
        content.addView(auto);

        CheckBox vibrate = new CheckBox(this);
        vibrate.setText("Vibrate when a phase ends");
        vibrate.setTextColor(Ui.TEXT);
        vibrate.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        vibrate.setChecked(engine.vibrate());
        content.addView(vibrate);

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Timing")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> {
                    engine.saveSettings(focus.getValue(), shortBreak.getValue(), longBreak.getValue(), cycleLength.getValue(), auto.isChecked(), vibrate.isChecked());
                    render();
                })
                .show();
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
        requestNotificationsIfNeeded();
        Intent service = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = Build.VERSION.SDK_INT >= 29
                    ? insets.getInsets(WindowInsets.Type.systemBars())
                    : android.graphics.Insets.of(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private NumberPicker picker(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(Math.max(min, Math.min(max, value)));
        picker.setWrapSelectorWheel(false);
        return picker;
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Ui.MUTED);
        label.setTextSize(12f);
        label.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 3));
        return label;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
