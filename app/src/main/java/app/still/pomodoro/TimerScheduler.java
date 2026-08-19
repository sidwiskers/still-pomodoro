package app.still.pomodoro;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class TimerScheduler {
    private static final int REQUEST = 4101;

    static void schedule(Context context, long atMillis) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending(context));
    }

    static void cancel(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms != null) alarms.cancel(pending(context));
    }

    private static PendingIntent pending(Context context) {
        Intent intent = new Intent(context, TimerReceiver.class).setAction("app.still.pomodoro.TIMER_DONE");
        return PendingIntent.getBroadcast(context, REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private TimerScheduler() {}
}
