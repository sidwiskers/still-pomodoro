package app.still.pomodoro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class TimerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        new TimerEngine(context).finishIfDue();
    }
}
