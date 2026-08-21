package app.still.pomodoro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

final class NotificationHelper {
    static final int TIMER_ID = 1001;
    static final int COMPLETION_ID = 1002;
    static final int OVERLAY_ID = 1003;

    private static final String TIMER_CHANNEL = "timer";
    private static final String FOCUS_DONE_CHANNEL = "focus_done_v2";
    private static final String BREAK_DONE_CHANNEL = "break_done_v2";
    private static final String LEGACY_FOCUS_DONE_CHANNEL = "focus_done_v1";
    private static final String LEGACY_BREAK_DONE_CHANNEL = "break_done_v1";
    static final String OVERLAY_CHANNEL = "overlay";
    private static final String ACTIVE_GROUP = "still_active";

    static void ensureChannels(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel timer = new NotificationChannel(TIMER_CHANNEL, "Active timer", NotificationManager.IMPORTANCE_LOW);
        timer.setDescription("Quiet countdown while a Pomodoro is running");
        timer.setSound(null, null);
        timer.enableVibration(false);
        timer.setShowBadge(false);
        nm.createNotificationChannel(timer);

        AudioAttributes alarmAudio = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        NotificationChannel focusDone = new NotificationChannel(FOCUS_DONE_CHANNEL, "Focus complete", NotificationManager.IMPORTANCE_HIGH);
        focusDone.setDescription("Sound used when a focus session finishes");
        focusDone.setSound(resourceUri(context, R.raw.focus_complete), alarmAudio);
        focusDone.enableVibration(false);
        nm.createNotificationChannel(focusDone);

        NotificationChannel breakDone = new NotificationChannel(BREAK_DONE_CHANNEL, "Break complete", NotificationManager.IMPORTANCE_HIGH);
        breakDone.setDescription("Sound used when a break finishes");
        breakDone.setSound(resourceUri(context, R.raw.break_complete), alarmAudio);
        breakDone.enableVibration(false);
        nm.createNotificationChannel(breakDone);

        nm.deleteNotificationChannel(LEGACY_FOCUS_DONE_CHANNEL);
        nm.deleteNotificationChannel(LEGACY_BREAK_DONE_CHANNEL);

        NotificationChannel overlay = new NotificationChannel(OVERLAY_CHANNEL, "Floating timer", NotificationManager.IMPORTANCE_MIN);
        overlay.setDescription("Required only while the floating timer is visible");
        overlay.setSound(null, null);
        overlay.enableVibration(false);
        overlay.setShowBadge(false);
        nm.createNotificationChannel(overlay);
    }

    static void showRunning(Context context, TimerState state) {
        ensureChannels(context);
        notify(context, TIMER_ID, runningNotification(context, state));
        TimerNotificationService.start(context);
    }

    static Notification runningNotification(Context context, TimerState state) {
        TimerEngine engine = new TimerEngine(context);
        long remaining = state.running
                ? Math.max(0L, state.endAtMillis - System.currentTimeMillis())
                : Math.max(0L, state.remainingMillis);
        return baseTimerBuilder(context)
                .setContentTitle(titleCase(engine.phaseLabel(state.phase)))
                .setContentText(sessionText(engine, state))
                .setWhen(System.currentTimeMillis() + remaining)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setProgress(1000, elapsedProgress(engine, state, remaining), false)
                .setOngoing(true)
                .addAction(action(context, android.R.drawable.ic_media_pause, "Pause", ActionReceiver.PAUSE, 11))
                .addAction(action(context, android.R.drawable.ic_media_next, "Skip", ActionReceiver.SKIP, 12))
                .addAction(action(context, android.R.drawable.ic_menu_close_clear_cancel, "Stop", ActionReceiver.STOP, 13))
                .build();
    }

    static void showPaused(Context context, TimerState state) {
        TimerNotificationService.stop(context);
        ensureChannels(context);
        TimerEngine engine = new TimerEngine(context);
        Notification.Builder b = baseTimerBuilder(context)
                .setContentTitle(titleCase(engine.phaseLabel(state.phase)) + " · paused")
                .setContentText(formatTime(state.remainingMillis) + " remaining · " + sessionText(engine, state).toLowerCase(java.util.Locale.US))
                .setProgress(1000, elapsedProgress(engine, state, state.remainingMillis), false)
                .setShowWhen(false)
                .setOngoing(false)
                .addAction(action(context, android.R.drawable.ic_media_play, "Resume", ActionReceiver.START, 21))
                .addAction(action(context, android.R.drawable.ic_media_next, "Skip", ActionReceiver.SKIP, 22))
                .addAction(action(context, android.R.drawable.ic_menu_close_clear_cancel, "Stop", ActionReceiver.STOP, 23));
        notify(context, TIMER_ID, b.build());
    }

    static void showCompletion(Context context, TimerState.Phase completed, TimerState next, boolean vibrate) {
        TimerNotificationService.stop(context);
        ensureChannels(context);
        boolean focusCompleted = completed == TimerState.Phase.FOCUS;
        String channel = focusCompleted ? FOCUS_DONE_CHANNEL : BREAK_DONE_CHANNEL;
        TimerEngine engine = new TimerEngine(context);
        String title = focusCompleted ? "Focus complete" : "Break complete";
        String body = titleCase(engine.phaseLabel(next.phase)) + (next.running ? " started" : " is ready");
        Notification.Builder b = new Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.rgb(216, 255, 106))
                .setContentTitle(title)
                .setContentText(body)
                .setSubText("Still")
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (!next.running) {
            b.addAction(action(context, android.R.drawable.ic_media_play, "Start", ActionReceiver.START, 31));
        }
        notify(context, COMPLETION_ID, b.build());
        if (vibrate) vibrate(context);
    }

    static Notification overlayNotification(Context context) {
        ensureChannels(context);
        return new Notification.Builder(context, OVERLAY_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.rgb(216, 255, 106))
                .setContentTitle("Floating timer")
                .setContentText("Tap to open Still")
                .setSubText("Still")
                .setContentIntent(openApp(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setGroup(ACTIVE_GROUP)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                .build();
    }

    static void cancelTimer(Context context) {
        TimerNotificationService.stop(context);
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(TIMER_ID);
    }

    private static Notification.Builder baseTimerBuilder(Context context) {
        return new Notification.Builder(context, TIMER_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.rgb(216, 255, 106))
                .setSubText("Still")
                .setContentIntent(openApp(context))
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setGroup(ACTIVE_GROUP)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY);
    }

    private static Notification.Action action(Context context, int icon, String title, String action, int requestCode) {
        Intent intent = new Intent(context, ActionReceiver.class).setAction(action);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(icon, title, pending).build();
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void notify(Context context, int id, Notification notification) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(id, notification);
    }

    private static Uri resourceUri(Context context, int id) {
        return Uri.parse("android.resource://" + context.getPackageName() + "/" + id);
    }

    private static int elapsedProgress(TimerEngine engine, TimerState state, long remaining) {
        long duration = Math.max(1L, engine.durationFor(state.phase));
        long elapsed = Math.max(0L, Math.min(duration, duration - remaining));
        return (int) Math.min(1000L, (elapsed * 1000L) / duration);
    }

    private static String sessionText(TimerEngine engine, TimerState state) {
        int total = Math.max(1, engine.cycleSize());
        int current = Math.min(total, Math.max(1, state.focusInCycle + 1));
        return "Session " + current + " of " + total;
    }

    private static String formatTime(long millis) {
        long total = Math.max(0L, (millis + 999L) / 1000L);
        return String.format(java.util.Locale.US, "%02d:%02d", total / 60L, total % 60L);
    }

    private static String titleCase(String text) {
        String lower = text.toLowerCase(java.util.Locale.US);
        StringBuilder out = new StringBuilder(lower.length());
        boolean nextUpper = true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (nextUpper && Character.isLetter(c)) {
                out.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                out.append(c);
            }
            if (c == ' ') nextUpper = true;
        }
        return out.toString();
    }

    private static void vibrate(Context context) {
        VibrationEffect effect = VibrationEffect.createWaveform(new long[]{0, 70, 60, 120}, -1);
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = context.getSystemService(VibratorManager.class);
            if (manager != null) manager.getDefaultVibrator().vibrate(effect);
        } else {
            Vibrator vibrator = context.getSystemService(Vibrator.class);
            if (vibrator != null) vibrator.vibrate(effect);
        }
    }

    private NotificationHelper() {}
}
