package app.still.pomodoro;

import android.content.Context;
import android.content.SharedPreferences;

final class TimerEngine {
    private static final String PREFS = "still_timer";
    private static final String K_PHASE = "phase";
    private static final String K_RUNNING = "running";
    private static final String K_END = "end";
    private static final String K_REMAINING = "remaining";
    private static final String K_CYCLE = "cycle";
    private static final String K_FOCUS_MIN = "focus_min";
    private static final String K_SHORT_MIN = "short_min";
    private static final String K_LONG_MIN = "long_min";
    private static final String K_CYCLE_SIZE = "cycle_size";
    private static final String K_AUTO_BREAK = "auto_break";
    private static final String K_AUTO_FOCUS = "auto_focus";
    private static final String K_VIBRATE = "vibrate";

    static final int DEFAULT_FOCUS = 25;
    static final int DEFAULT_SHORT = 5;
    static final int DEFAULT_LONG = 15;
    static final int DEFAULT_CYCLE = 4;

    private final Context app;
    private final SharedPreferences prefs;

    TimerEngine(Context context) {
        app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureInitialized();
    }

    synchronized TimerState snapshot() {
        TimerState.Phase phase = readPhase();
        boolean running = prefs.getBoolean(K_RUNNING, false);
        long end = prefs.getLong(K_END, 0L);
        long remaining = prefs.getLong(K_REMAINING, durationFor(phase));
        if (running) {
            remaining = Math.max(0L, end - System.currentTimeMillis());
        }
        return new TimerState(phase, running, end, remaining, prefs.getInt(K_CYCLE, 0));
    }

    synchronized TimerState start() {
        TimerState before = snapshot();
        long remaining = before.remainingMillis;
        if (remaining <= 0L) remaining = durationFor(before.phase);
        long end = System.currentTimeMillis() + remaining;
        prefs.edit()
                .putBoolean(K_RUNNING, true)
                .putLong(K_END, end)
                .putLong(K_REMAINING, remaining)
                .apply();
        TimerScheduler.schedule(app, end);
        NotificationHelper.showRunning(app, snapshot());
        return snapshot();
    }

    synchronized TimerState pause() {
        TimerState before = snapshot();
        long remaining = before.running ? Math.max(0L, before.endAtMillis - System.currentTimeMillis()) : before.remainingMillis;
        prefs.edit()
                .putBoolean(K_RUNNING, false)
                .putLong(K_END, 0L)
                .putLong(K_REMAINING, remaining)
                .apply();
        TimerScheduler.cancel(app);
        NotificationHelper.showPaused(app, snapshot());
        return snapshot();
    }

    synchronized TimerState resetCurrent() {
        TimerState.Phase phase = readPhase();
        prefs.edit()
                .putBoolean(K_RUNNING, false)
                .putLong(K_END, 0L)
                .putLong(K_REMAINING, durationFor(phase))
                .apply();
        TimerScheduler.cancel(app);
        NotificationHelper.cancelTimer(app);
        return snapshot();
    }

    synchronized TimerState skip() {
        TimerState before = snapshot();
        advanceFrom(before.phase, false);
        TimerScheduler.cancel(app);
        NotificationHelper.cancelTimer(app);
        return snapshot();
    }

    synchronized TimerState finishIfDue() {
        TimerState before = snapshot();
        if (!before.running || before.endAtMillis > System.currentTimeMillis() + 1500L) return before;
        TimerState.Phase completed = before.phase;
        advanceFrom(completed, true);
        NotificationHelper.showCompletion(app, completed, snapshot(), vibrateEnabled());
        TimerState after = snapshot();
        boolean autoStart = after.phase == TimerState.Phase.FOCUS ? autoFocus() : autoBreak();
        if (autoStart) return start();
        NotificationHelper.cancelTimer(app);
        return after;
    }

    synchronized void rescheduleAfterBoot() {
        TimerState s = snapshot();
        if (!s.running) return;
        if (s.endAtMillis <= System.currentTimeMillis()) {
            finishIfDue();
        } else {
            TimerScheduler.schedule(app, s.endAtMillis);
            NotificationHelper.showRunning(app, s);
        }
    }

    synchronized void setDurations(int focus, int shortBreak, int longBreak, int cycleSize) {
        focus = clamp(focus, 1, 180);
        shortBreak = clamp(shortBreak, 1, 60);
        longBreak = clamp(longBreak, 1, 120);
        cycleSize = clamp(cycleSize, 1, 12);
        TimerState before = snapshot();
        prefs.edit()
                .putInt(K_FOCUS_MIN, focus)
                .putInt(K_SHORT_MIN, shortBreak)
                .putInt(K_LONG_MIN, longBreak)
                .putInt(K_CYCLE_SIZE, cycleSize)
                .apply();
        if (!before.running) {
            prefs.edit().putLong(K_REMAINING, durationFor(before.phase)).apply();
        }
    }

    synchronized void setAutoStart(boolean breakAuto, boolean focusAuto) {
        prefs.edit().putBoolean(K_AUTO_BREAK, breakAuto).putBoolean(K_AUTO_FOCUS, focusAuto).apply();
    }

    synchronized void setVibrate(boolean enabled) {
        prefs.edit().putBoolean(K_VIBRATE, enabled).apply();
    }

    int focusMinutes() { return prefs.getInt(K_FOCUS_MIN, DEFAULT_FOCUS); }
    int shortMinutes() { return prefs.getInt(K_SHORT_MIN, DEFAULT_SHORT); }
    int longMinutes() { return prefs.getInt(K_LONG_MIN, DEFAULT_LONG); }
    int cycleSize() { return prefs.getInt(K_CYCLE_SIZE, DEFAULT_CYCLE); }
    boolean autoBreak() { return prefs.getBoolean(K_AUTO_BREAK, false); }
    boolean autoFocus() { return prefs.getBoolean(K_AUTO_FOCUS, false); }
    boolean vibrateEnabled() { return prefs.getBoolean(K_VIBRATE, true); }

    long durationFor(TimerState.Phase phase) {
        int minutes;
        switch (phase) {
            case SHORT_BREAK: minutes = shortMinutes(); break;
            case LONG_BREAK: minutes = longMinutes(); break;
            case FOCUS:
            default: minutes = focusMinutes(); break;
        }
        return minutes * 60_000L;
    }

    String phaseLabel(TimerState.Phase phase) {
        switch (phase) {
            case SHORT_BREAK: return "SHORT BREAK";
            case LONG_BREAK: return "LONG BREAK";
            case FOCUS:
            default: return "FOCUS";
        }
    }

    private void advanceFrom(TimerState.Phase completed, boolean countFocus) {
        int cycle = prefs.getInt(K_CYCLE, 0);
        TimerState.Phase next;
        if (completed == TimerState.Phase.FOCUS) {
            if (countFocus) cycle++;
            if (cycle >= cycleSize()) {
                next = TimerState.Phase.LONG_BREAK;
                cycle = 0;
            } else {
                next = TimerState.Phase.SHORT_BREAK;
            }
        } else {
            next = TimerState.Phase.FOCUS;
        }
        prefs.edit()
                .putString(K_PHASE, next.name())
                .putInt(K_CYCLE, cycle)
                .putBoolean(K_RUNNING, false)
                .putLong(K_END, 0L)
                .putLong(K_REMAINING, durationFor(next))
                .apply();
    }

    private TimerState.Phase readPhase() {
        try {
            return TimerState.Phase.valueOf(prefs.getString(K_PHASE, TimerState.Phase.FOCUS.name()));
        } catch (IllegalArgumentException ignored) {
            return TimerState.Phase.FOCUS;
        }
    }

    private void ensureInitialized() {
        if (!prefs.contains(K_PHASE)) {
            prefs.edit()
                    .putString(K_PHASE, TimerState.Phase.FOCUS.name())
                    .putBoolean(K_RUNNING, false)
                    .putLong(K_REMAINING, DEFAULT_FOCUS * 60_000L)
                    .putInt(K_FOCUS_MIN, DEFAULT_FOCUS)
                    .putInt(K_SHORT_MIN, DEFAULT_SHORT)
                    .putInt(K_LONG_MIN, DEFAULT_LONG)
                    .putInt(K_CYCLE_SIZE, DEFAULT_CYCLE)
                    .putBoolean(K_AUTO_BREAK, false)
                    .putBoolean(K_AUTO_FOCUS, false)
                    .putBoolean(K_VIBRATE, true)
                    .apply();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
