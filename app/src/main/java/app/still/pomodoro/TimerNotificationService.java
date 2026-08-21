package app.still.pomodoro;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

final class TimerNotificationService extends Service {
    private static final long REFRESH_INTERVAL_MS = 10_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TimerEngine engine;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            TimerState state = engine.snapshot();
            if (!state.running) {
                stopSelf();
                return;
            }

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.notify(NotificationHelper.TIMER_ID,
                        NotificationHelper.runningNotification(TimerNotificationService.this, state));
            }

            if (state.remainingMillis <= 0L) {
                stopSelf();
                return;
            }
            scheduleNextRefresh();
        }
    };

    static void start(Context context) {
        Intent intent = new Intent(context, TimerNotificationService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (RuntimeException ignored) {
            // Timer state and its exact boundary alarm remain authoritative if an OEM blocks this UI refresher.
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, TimerNotificationService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        engine = new TimerEngine(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        TimerState state = engine.snapshot();
        if (!state.running) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = NotificationHelper.runningNotification(this, state);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NotificationHelper.TIMER_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NotificationHelper.TIMER_ID, notification);
        }

        handler.removeCallbacks(refresh);
        scheduleNextRefresh();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(refresh);
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH);
        else stopForeground(false);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private void scheduleNextRefresh() {
        long now = SystemClock.uptimeMillis();
        long delay = REFRESH_INTERVAL_MS - (now % REFRESH_INTERVAL_MS);
        handler.postDelayed(refresh, Math.max(1_000L, delay));
    }
}
