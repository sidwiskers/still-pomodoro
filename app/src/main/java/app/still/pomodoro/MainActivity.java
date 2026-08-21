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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TimerEngine engine;
    private TimerDialView dial;
    private TextView primary;
    private TextView cycle;
    private TextView today;
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
        setContentView(buildContent());
        configureWindow();
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
        boolean tablet = isTablet();
        MaxWidthLinearLayout root = new MaxWidthLinearLayout(this, tablet ? 720 : 620);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int horizontalPadding = Ui.dp(this, tablet ? 28 : 22);
        root.setPadding(horizontalPadding, Ui.dp(this, tablet ? 36 : 30), horizontalPadding, Ui.dp(this, tablet ? 28 : 22));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.START);

        TextView brand = new TextView(this);
        brand.setText("Still");
        brand.setTextColor(Ui.TEXT);
        brand.setTextSize(tablet ? 28f : 26f);
        brand.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        brand.setLetterSpacing(-0.01f);
        header.addView(brand, wrap());

        TextView sub = new TextView(this);
        sub.setText("focus without the noise");
        sub.setTextColor(Ui.MUTED);
        sub.setTextSize(tablet ? 12f : 11.5f);
        sub.setLetterSpacing(0.035f);
        LinearLayout.LayoutParams subLp = wrap();
        subLp.topMargin = Ui.dp(this, 3);
        header.addView(sub, subLp);

        View accentRule = new View(this);
        accentRule.setBackground(Ui.roundRect(Ui.ACCENT, 2f, this));
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(Ui.dp(this, tablet ? 26 : 22), Ui.dp(this, 3));
        ruleLp.topMargin = Ui.dp(this, 13);
        header.addView(accentRule, ruleLp);

        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout dialCard = new FrameLayout(this);
        dialCard.setBackground(Ui.panel(this, 34f));
        dialCard.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        LinearLayout.LayoutParams dialCardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, tablet ? 360 : 332));
        dialCardLp.topMargin = Ui.dp(this, tablet ? 24 : 20);
        root.addView(dialCard, dialCardLp);

        dial = new TimerDialView(this);
        dialCard.addView(dial, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cycle = new TextView(this);
        cycle.setText("SESSION 1 / 4");
        cycle.setTextColor(Ui.MUTED);
        cycle.setTextSize(tablet ? 11f : 10.5f);
        cycle.setGravity(Gravity.CENTER);
        cycle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        cycle.setLetterSpacing(0.105f);
        LinearLayout.LayoutParams cycleLp = wrap();
        cycleLp.topMargin = Ui.dp(this, 12);
        root.addView(cycle, cycleLp);

        today = new TextView(this);
        today.setTextColor(Ui.MUTED);
        today.setTextSize(tablet ? 12f : 11.5f);
        today.setGravity(Gravity.CENTER);
        today.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        today.setLetterSpacing(0.06f);
        today.setMinHeight(Ui.dp(this, 34));
        today.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        today.setClickable(true);
        today.setFocusable(true);
        today.setOnClickListener(v -> { Ui.feedback(v); showFocusHistory(); });
        LinearLayout.LayoutParams todayLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 34));
        todayLp.topMargin = Ui.dp(this, 3);
        root.addView(today, todayLp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlsLp.topMargin = Ui.dp(this, 9);

        primary = Ui.pill(this, "Start", true);
        primary.setOnClickListener(v -> {
            Ui.feedback(v);
            requestNotificationsIfNeeded();
            TimerState s = engine.snapshot();
            if (s.running) engine.pause(); else engine.start();
            render();
        });
        controls.addView(primary, new LinearLayout.LayoutParams(0, Ui.dp(this, tablet ? 52 : 50), 1f));

        Space gap = new Space(this);
        controls.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));

        TextView reset = Ui.pill(this, "Reset", false);
        reset.setOnClickListener(v -> { Ui.feedback(v); engine.resetCurrent(); render(); });
        controls.addView(reset, new LinearLayout.LayoutParams(0, Ui.dp(this, tablet ? 52 : 50), 0.58f));
        root.addView(controls, controlsLp);

        LinearLayout features = new LinearLayout(this);
        features.setOrientation(LinearLayout.HORIZONTAL);
        features.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams featuresLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        featuresLp.topMargin = Ui.dp(this, 9);

        TextView overlay = Ui.segmentTile(this, "Float", 0);
        overlay.setOnClickListener(v -> { Ui.feedback(v); toggleOverlay(); });
        features.addView(overlay, new LinearLayout.LayoutParams(0, Ui.dp(this, tablet ? 46 : 44), 1f));

        Space g2 = new Space(this);
        features.addView(g2, new LinearLayout.LayoutParams(Ui.dp(this, 4), 1));

        TextView lowPower = Ui.segmentTile(this, "Low power", 1);
        lowPower.setOnClickListener(v -> { Ui.feedback(v); startActivity(new Intent(this, LowPowerActivity.class)); });
        features.addView(lowPower, new LinearLayout.LayoutParams(0, Ui.dp(this, tablet ? 46 : 44), 1f));

        Space g3 = new Space(this);
        features.addView(g3, new LinearLayout.LayoutParams(Ui.dp(this, 4), 1));

        TextView timing = Ui.segmentTile(this, "Timing", 2);
        timing.setOnClickListener(v -> { Ui.feedback(v); showSettings(); });
        features.addView(timing, new LinearLayout.LayoutParams(0, Ui.dp(this, tablet ? 46 : 44), 1f));
        root.addView(features, featuresLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        ScrollView.LayoutParams rootLp = new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rootLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        scroll.addView(root, rootLp);

        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(Ui.BG);
        shell.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return shell;
    }

    private void render() {
        TimerState s = engine.snapshot();
        if (s.running && s.remainingMillis <= 0L) s = engine.finishIfDue();
        dial.bind(s, engine.durationFor(s.phase), engine.phaseLabel(s.phase), engine.cycleSize());
        primary.setText(s.running ? "Pause" : "Start");
        cycle.setText("SESSION " + Math.min(engine.cycleSize(), s.focusInCycle + 1) + " / " + engine.cycleSize());
        bindToday(engine.focusTodayMillis());
    }

    private void bindToday(long millis) {
        String duration = formatFocusDuration(millis);
        String text = "TODAY  ·  " + duration + "  ›";
        SpannableString span = new SpannableString(text);
        int start = text.indexOf(duration);
        span.setSpan(new ForegroundColorSpan(Ui.ACCENT), start, start + duration.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        today.setText(span);
        today.setContentDescription("Today, " + duration + " focused. Open focus history.");
    }

    private void showFocusHistory() {
        long[] days = engine.focusLastDays(7);
        long total = 0L;
        for (long day : days) total += day;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 12));

        TextView note = new TextView(this);
        note.setText("Focus time only");
        note.setTextColor(Ui.MUTED);
        note.setTextSize(11.5f);
        note.setLetterSpacing(0.04f);
        content.addView(note, wrap());

        FocusHistoryView chart = new FocusHistoryView(this);
        chart.setValues(days);
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, isTablet() ? 224 : 198));
        chartLp.topMargin = Ui.dp(this, 8);
        content.addView(chart, chartLp);

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(0, Ui.dp(this, 4), 0, 0);
        summary.addView(historyMetric("7 DAYS", formatFocusDuration(total)), new LinearLayout.LayoutParams(0, Ui.dp(this, 58), 1f));
        summary.addView(historyMetric("DAILY AVG", formatFocusDuration(total / 7L)), new LinearLayout.LayoutParams(0, Ui.dp(this, 58), 1f));
        content.addView(summary, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Focus history").setView(content).setPositiveButton("Close", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Ui.ACCENT);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(Ui.panel(this, 28f));
                dialog.getWindow().setDimAmount(0.72f);
            }
        });
        dialog.show();
    }

    private View historyMetric(String labelText, String valueText) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(Ui.MUTED_2);
        label.setTextSize(9.5f);
        label.setLetterSpacing(0.11f);
        label.setGravity(Gravity.CENTER);
        box.addView(label, wrap());
        TextView value = new TextView(this);
        value.setText(valueText);
        value.setTextColor(Ui.TEXT);
        value.setTextSize(16f);
        value.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        value.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valueLp = wrap();
        valueLp.topMargin = Ui.dp(this, 3);
        box.addView(value, valueLp);
        return box;
    }

    private void showSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 5), Ui.dp(this, 18), Ui.dp(this, 10));

        WheelPickerView focus = picker(1, 180, engine.focusMinutes());
        WheelPickerView shortBreak = picker(1, 60, engine.shortMinutes());
        WheelPickerView longBreak = picker(1, 120, engine.longMinutes());
        WheelPickerView cycles = picker(1, 12, engine.cycleSize());
        content.addView(settingRow("Focus minutes", focus), rowParams());
        content.addView(settingRow("Short break", shortBreak), rowParams());
        content.addView(settingRow("Long break", longBreak), rowParams());
        content.addView(settingRow("Long break after", cycles), rowParams());

        CheckBox autoBreak = check("Auto-start breaks", engine.autoBreak());
        CheckBox autoFocus = check("Auto-start focus", engine.autoFocus());
        CheckBox vibrate = check("Completion vibration", engine.vibrateEnabled());
        content.addView(autoBreak);
        content.addView(autoFocus);
        content.addView(vibrate);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Timing")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Start over", null)
                .setPositiveButton("Save", (d, which) -> {
                    engine.setDurations(focus.getValue(), shortBreak.getValue(), longBreak.getValue(), cycles.getValue());
                    engine.setAutoStart(autoBreak.isChecked(), autoFocus.isChecked());
                    engine.setVibrate(vibrate.isChecked());
                    render();
                }).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Ui.ACCENT);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Ui.MUTED);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Ui.MUTED);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { Ui.feedback(v); confirmStartOver(dialog); });
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(Ui.panel(this, 28f));
                dialog.getWindow().setDimAmount(0.72f);
            }
        });
        dialog.show();
    }

    private void confirmStartOver(AlertDialog parent) {
        AlertDialog confirm = new AlertDialog.Builder(this)
                .setTitle("Start over this cycle?")
                .setMessage("The timer returns to Focus · Session 1. Today's focus time stays recorded.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start over", (d, which) -> { engine.resetAll(); parent.dismiss(); render(); })
                .create();
        confirm.setOnShowListener(d -> {
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Ui.ACCENT);
            confirm.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Ui.MUTED);
            if (confirm.getWindow() != null) {
                confirm.getWindow().setBackgroundDrawable(Ui.panel(this, 26f));
                confirm.getWindow().setDimAmount(0.78f);
            }
        });
        confirm.show();
    }

    private View settingRow(String title, WheelPickerView picker) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        row.setBackground(Ui.outlinedSurface(this, 18f));
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Ui.TEXT);
        label.setTextSize(13f);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        row.addView(label, new LinearLayout.LayoutParams(0, Ui.dp(this, 72), 1f));
        row.addView(picker, new LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 72)));
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        return lp;
    }

    private WheelPickerView picker(int min, int max, int value) { return new WheelPickerView(this, min, max, value); }

    private CheckBox check(String text, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(Ui.TEXT);
        c.setTextSize(13f);
        c.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        c.setChecked(checked);
        c.setMinHeight(Ui.dp(this, 44));
        return c;
    }

    private void toggleOverlay() {
        if (OverlayService.isRunning()) { stopService(new Intent(this, OverlayService.class)); return; }
        if (!Settings.canDrawOverlays(this)) {
            overlayRequested = true;
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
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

    private boolean isTablet() { return getResources().getConfiguration().smallestScreenWidthDp >= 600; }

    private void configureWindow() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Ui.BG);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            View decor = getWindow().getDecorView();
            WindowInsetsController c = decor.getWindowInsetsController();
            if (c != null) c.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            decor.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
            decor.requestApplyInsets();
        }
    }

    private static String formatFocusDuration(long millis) {
        long totalMinutes = Math.max(0L, millis / 60_000L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L && minutes > 0L) return hours + "h " + minutes + "m";
        if (hours > 0L) return hours + "h";
        return totalMinutes + "m";
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
