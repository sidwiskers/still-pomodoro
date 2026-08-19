package app.still.pomodoro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

final class LowPowerView extends View {
    private final TimerEngine engine;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint meta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint controls = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean controlsVisible;
    private long controlsUntil;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            TimerState s = engine.snapshot();
            if (s.running && s.remainingMillis <= 0L) engine.finishIfDue();
            if (controlsVisible && System.currentTimeMillis() > controlsUntil) controlsVisible = false;
            invalidate();
            handler.postDelayed(this, 1000L - (System.currentTimeMillis() % 1000L) + 10L);
        }
    };

    LowPowerView(Context context) {
        super(context);
        engine = new TimerEngine(context);
        setBackgroundColor(Color.BLACK);

        time.setColor(Color.rgb(128, 130, 120));
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setTextAlign(Paint.Align.CENTER);

        meta.setColor(Color.rgb(68, 70, 64));
        meta.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        meta.setTextAlign(Paint.Align.CENTER);

        controls.setColor(Color.rgb(82, 84, 76));
        controls.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        controls.setTextAlign(Paint.Align.CENTER);

        track.setColor(Color.rgb(24, 25, 23));
        track.setStrokeWidth(Ui.dp(context, 1));
        track.setStrokeCap(Paint.Cap.ROUND);

        progress.setColor(Color.rgb(67, 78, 52));
        progress.setStrokeWidth(Ui.dp(context, 1.5f));
        progress.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        TimerState s = engine.snapshot();
        int minuteDrift = (int)((System.currentTimeMillis() / 60_000L) % 5L) - 2;
        float drift = Ui.dp(getContext(), minuteDrift * 1.5f);
        float cx = getWidth() / 2f + drift;
        float cy = getHeight() / 2f - drift;

        long seconds = Math.max(0L, (s.remainingMillis + 999L) / 1000L);
        String clock = String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
        time.setTextSize(Math.min(Ui.dp(getContext(), 116), getWidth() * 0.24f));
        Paint.FontMetrics fm = time.getFontMetrics();
        canvas.drawText(clock, cx, cy - (fm.ascent + fm.descent) / 2f, time);

        meta.setTextSize(Ui.dp(getContext(), 10));
        canvas.drawText(engine.phaseLabel(s.phase).toUpperCase(Locale.US), cx, cy - Ui.dp(getContext(), 84), meta);

        float lineWidth = Math.min(Ui.dp(getContext(), 112), getWidth() * 0.28f);
        float left = cx - lineWidth / 2f;
        float right = cx + lineWidth / 2f;
        float lineY = cy + Ui.dp(getContext(), 72);
        canvas.drawLine(left, lineY, right, lineY, track);
        float fraction = Math.max(0f, Math.min(1f, (float) s.remainingMillis / Math.max(1f, engine.durationFor(s.phase))));
        canvas.drawLine(left, lineY, left + lineWidth * fraction, lineY, progress);

        if (controlsVisible) {
            controls.setTextSize(Ui.dp(getContext(), 11));
            canvas.drawText(s.running ? "pause" : "resume", getWidth() * 0.28f, getHeight() - Ui.dp(getContext(), 48), controls);
            canvas.drawText("exit", getWidth() * 0.72f, getHeight() - Ui.dp(getContext(), 48), controls);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
        float y = event.getY();
        if (controlsVisible && y > getHeight() * 0.72f) {
            if (event.getX() < getWidth() / 2f) {
                TimerState s = engine.snapshot();
                if (s.running) engine.pause(); else engine.start();
                Ui.feedback(this);
                controlsUntil = System.currentTimeMillis() + 2500L;
            } else {
                Ui.feedback(this);
                ((android.app.Activity)getContext()).finish();
            }
        } else {
            controlsVisible = !controlsVisible;
            controlsUntil = System.currentTimeMillis() + 2500L;
            Ui.feedback(this);
        }
        invalidate();
        return true;
    }
}
