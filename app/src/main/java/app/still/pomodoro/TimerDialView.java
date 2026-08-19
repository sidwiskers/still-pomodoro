package app.still.pomodoro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

final class TimerDialView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint phase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();

    private TimerState state = new TimerState(TimerState.Phase.FOCUS, false, 0L, 25 * 60_000L, 0);
    private long duration = 25 * 60_000L;
    private String phaseText = "FOCUS";

    TimerDialView(Context context) {
        super(context);

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(Ui.SURFACE_2);

        progress.setStyle(Paint.Style.STROKE);
        progress.setStrokeCap(Paint.Cap.ROUND);
        progress.setColor(Ui.ACCENT);

        time.setColor(Ui.TEXT);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setTextAlign(Paint.Align.CENTER);

        phase.setColor(Ui.MUTED);
        phase.setTypeface(Typeface.create("sans", Typeface.BOLD));
        phase.setTextAlign(Paint.Align.CENTER);

        hint.setColor(Ui.MUTED);
        hint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        hint.setTextAlign(Paint.Align.CENTER);
    }

    void bind(TimerState state, long duration, String phaseText) {
        this.state = state;
        this.duration = Math.max(1L, duration);
        this.phaseText = phaseText;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h) * 0.72f;
        float cx = w / 2f;
        float cy = h / 2f + Ui.dp(getContext(), 8);
        float stroke = Math.max(Ui.dp(getContext(), 5), size * 0.025f);
        float radius = size / 2f - stroke;

        track.setStrokeWidth(stroke);
        progress.setStrokeWidth(stroke);
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(arc, -90f, 360f, false, track);

        float fraction = Math.max(0f, Math.min(1f, (float) state.remainingMillis / (float) duration));
        float sweep = 360f * fraction;
        canvas.drawArc(arc, -90f, sweep, false, progress);

        long totalSeconds = Math.max(0L, (state.remainingMillis + 999L) / 1000L);
        String clock = String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);

        time.setTextSize(Math.min(Ui.dp(getContext(), 76), size * 0.28f));
        phase.setTextSize(Math.min(Ui.dp(getContext(), 13), size * 0.052f));
        hint.setTextSize(Math.min(Ui.dp(getContext(), 12), size * 0.047f));

        Paint.FontMetrics tm = time.getFontMetrics();
        float timeBaseline = cy - (tm.ascent + tm.descent) / 2f;
        canvas.drawText(clock, cx, timeBaseline, time);
        canvas.drawText(phaseText, cx, cy - size * 0.25f, phase);
        canvas.drawText(state.running ? "running quietly" : "ready", cx, cy + size * 0.26f, hint);
    }
}
