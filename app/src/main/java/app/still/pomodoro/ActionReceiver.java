package app.still.pomodoro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ActionReceiver extends BroadcastReceiver {
    static final String START = "app.still.pomodoro.START";
    static final String PAUSE = "app.still.pomodoro.PAUSE";
    static final String SKIP = "app.still.pomodoro.SKIP";
    static final String STOP = "app.still.pomodoro.STOP";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        TimerEngine engine = new TimerEngine(context);
        switch (intent.getAction()) {
            case START: engine.start(); break;
            case PAUSE: engine.pause(); break;
            case SKIP: engine.skip(); break;
            case STOP: engine.resetCurrent(); break;
            default: break;
        }
    }
}
