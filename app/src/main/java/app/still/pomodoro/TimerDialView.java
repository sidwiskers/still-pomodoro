package app.still.pomodoro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

final class TimerDialView extends View {
    private static final float START_ANGLE = 120f;
    private static final float TOTAL_SWEEP = 300f;

    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerCut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ticks = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint phase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint session = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();

    private TimerState state = new TimerState(TimerState.Phase.FOCUS, false, 0L, 25 * 60_000L, 0);
    private long duration = 25 * 60_000L;
    private String phaseText = "FOCUS";
    private int cycleSize = 4;

    TimerDialView(Context context) {
        super(context);

        disc.setColor(Ui.SURFACE);

        innerBorder.setStyle(Paint.Style.STROKE);
        innerBorder.setStrokeWidth(Ui.dp(context, 1));
        innerBorder.setColor(Color.rgb(39, 42, 35));

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(Ui.SURFACE_3);

        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeCap(Paint.Cap.ROUND);
        halo.setColor(Color.argb(28, 216, 255, 106));

        progress.setStyle(Paint.Style.STROKE);
        progress.setStrokeCap(Paint.Cap.ROUND);
        progress.setColor(Ui.ACCENT);

        markerGlow.setStyle(Paint.Style.FILL);
        markerGlow.setColor(Color.argb(42, 216, 255, 106));
        marker.setStyle(Paint.Style.FILL);
        marker.setColor(Ui.ACCENT);
        markerCut.setStyle(Paint.Style.FILL);
        markerCut.setColor(Color.rgb(20, 22, 18));

        ticks.setStyle(Paint.Style.STROKE);
        ticks.setStrokeCap(Paint.Cap.ROUND);
        ticks.setStrokeWidth(Ui.dp(context, 1));
        ticks.setColor(Color.rgb(53, 56, 47));

        time.setColor(Ui.TEXT);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setTextAlign(Paint.Align.CENTER);

        phase.setColor(Ui.ACCENT);
        phase.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        phase.setTextAlign(Paint.Align.CENTER);

        hint.setColor(Ui.MUTED);
        hint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        hint.setTextAlign(Paint.Align.CENTER);

        session.setStyle(Paint.Style.FILL);
    }

    void bind(TimerState state, long duration, String phaseText, int cycleSize) {
        this.state = state;
        this.duration = Math.max(1L, duration);
        this.phaseText = phaseText;
        this.cycleSize = Math.max(1, cycleSize);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h) * 0.80f;
        float cx = w / 2f;
        float cy = h / 2f - Ui.dp(getContext(), 1);
        float stroke = Math.max(Ui.dp(getContext(), 6), size * 0.026f);
        float radius = size / 2f - stroke * 1.6f;

        canvas.drawCircle(cx, cy, radius * 0.78f, disc);
        canvas.drawCircle(cx, cy, radius * 0.78f, innerBorder);

        track.setStrokeWidth(stroke);
        halo.setStrokeWidth(stroke + Ui.dp(getContext(), 9));
        progress.setStrokeWidth(stroke);
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius);

        canvas.drawArc(arc, START_ANGLE, TOTAL_SWEEP, false, track);
        drawTicks(canvas, cx, cy, radius, stroke);

        float fraction = Math.max(0f, Math.min(1f, (float) state.remainingMillis / (float) duration));
        float sweep = TOTAL_SWEEP * fraction;
        if (sweep > 0.4f) {
            canvas.drawArc(arc, START_ANGLE, sweep, false, halo);
            canvas.drawArc(arc, START_ANGLE, sweep, false, progress);
            drawEndMarker(canvas, cx, cy, radius, START_ANGLE + sweep, stroke);
        }

        long totalSeconds = Math.max(0L, (state.remainingMillis + 999L) / 1000L);
        String clock = String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);

        time.setTextSize(Math.min(Ui.dp(getContext(), 72), size * 0.255f));
        phase.setTextSize(Math.min(Ui.dp(getContext(), 11), size * 0.042f));
        hint.setTextSize(Math.min(Ui.dp(getContext(), 11), size * 0.041f));

        Paint.FontMetrics tm = time.getFontMetrics();
        float timeBaseline = cy - (tm.ascent + tm.descent) / 2f + Ui.dp(getContext(), 2);
        canvas.drawText(clock, cx, timeBaseline, time);

        String phaseLabel = phaseText.toUpperCase(Locale.US).replace(" BREAK", "");
        canvas.drawText(phaseLabel, cx, cy - radius * 0.40f, phase);
        canvas.drawText(state.running ? "quietly in progress" : "ready when you are", cx, cy + radius * 0.41f, hint);

        drawSessionDots(canvas, cx, cy + radius, cycleSize, state.focusInCycle);
    }

    private void drawTicks(Canvas canvas, float cx, float cy, float radius, float stroke) {
        float inner = radius + stroke * 1.45f;
        float outer = inner + Ui.dp(getContext(), 4.5f);
        for (int i = 0; i <= 10; i++) {
            float angle = START_ANGLE + TOTAL_SWEEP * (i / 10f);
            double rad = Math.toRadians(angle);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            canvas.drawLine(cx + cos * inner, cy + sin * inner, cx + cos * outer, cy + sin * outer, ticks);
        }
    }

    private void drawEndMarker(Canvas canvas, float cx, float cy, float radius, float angle, float stroke) {
        double rad = Math.toRadians(angle);
        float x = cx + (float) Math.cos(rad) * radius;
        float y = cy + (float) Math.sin(rad) * radius;
        float dot = Math.max(Ui.dp(getContext(), 4.2f), stroke * 0.54f);
        canvas.drawCircle(x, y, dot * 2.2f, markerGlow);
        canvas.drawCircle(x, y, dot, marker);
        canvas.drawCircle(x, y, Math.max(Ui.dp(getContext(), 1.4f), dot * 0.30f), markerCut);
    }

    private void drawSessionDots(Canvas canvas, float cx, float y, int count, int current) {
        int visible = Math.min(8, Math.max(1, count));
        float gap = Ui.dp(getContext(), 11);
        float start = cx - ((visible - 1) * gap) / 2f;
        int active = Math.min(visible - 1, Math.max(0, current));
        for (int i = 0; i < visible; i++) {
            session.setColor(i == active ? Ui.ACCENT : Color.rgb(58, 61, 52));
            float r = Ui.dp(getContext(), i == active ? 2.4f : 1.7f);
            canvas.drawCircle(start + i * gap, y, r, session);
        }
    }
}
