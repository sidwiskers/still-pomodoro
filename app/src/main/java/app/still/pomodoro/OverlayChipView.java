package app.still.pomodoro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

final class OverlayChipView extends View {
    interface Host {
        void moveBy(int dx, int dy);
        void snapToEdge();
        void setExpanded(boolean expanded);
        void closeOverlay();
    }

    private final Host host;
    private final TimerEngine engine;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lobe = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentSoft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ivory = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint meta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint controlFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path markTop = new Path();
    private final Path markBottom = new Path();
    private boolean expanded;
    private float downRawX, downRawY, lastRawX, lastRawY;
    private boolean dragging;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            TimerState s = engine.snapshot();
            if (s.running && s.remainingMillis <= 0L) engine.finishIfDue();
            invalidate();
            handler.postDelayed(this, 1000L - (System.currentTimeMillis() % 1000L) + 16L);
        }
    };

    OverlayChipView(Context context, Host host) {
        super(context);
        this.host = host;
        this.engine = new TimerEngine(context);

        surface.setColor(Color.argb(250, 15, 17, 14));
        lobe.setColor(Color.argb(255, 24, 27, 22));
        border.setColor(Color.argb(190, 52, 56, 47));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Ui.dp(context, 1));

        accent.setColor(Ui.ACCENT);
        accent.setStyle(Paint.Style.STROKE);
        accent.setStrokeCap(Paint.Cap.ROUND);
        accent.setStrokeWidth(Ui.dp(context, 1.7f));
        accentSoft.setColor(Color.argb(52, 216, 255, 106));
        accentSoft.setStrokeCap(Paint.Cap.ROUND);
        ivory.setColor(Ui.TEXT);
        ivory.setStyle(Paint.Style.STROKE);
        ivory.setStrokeCap(Paint.Cap.ROUND);
        ivory.setStrokeWidth(Ui.dp(context, 1.7f));

        time.setColor(Ui.TEXT);
        time.setTextAlign(Paint.Align.CENTER);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        meta.setColor(Ui.MUTED);
        meta.setTextAlign(Paint.Align.CENTER);
        meta.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        glyph.setColor(Ui.TEXT);
        glyph.setStrokeWidth(Ui.dp(context, 1.55f));
        glyph.setStrokeCap(Paint.Cap.ROUND);
        glyph.setStyle(Paint.Style.STROKE);

        controlFill.setColor(Color.argb(66, 63, 67, 57));
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
        float h = getHeight();
        float cy = h / 2f;
        float bodyLeft = Ui.dp(getContext(), 14);
        float radius = Ui.dp(getContext(), 15);
        rect.set(bodyLeft, Ui.dp(getContext(), 1), getWidth() - Ui.dp(getContext(), 1), h - Ui.dp(getContext(), 1));
        canvas.drawRoundRect(rect, radius, radius, surface);
        canvas.drawRoundRect(rect, radius, radius, border);

        float lobeX = Ui.dp(getContext(), 18);
        float lobeR = Ui.dp(getContext(), 15);
        canvas.drawCircle(lobeX, cy, lobeR, lobe);
        canvas.drawCircle(lobeX, cy, lobeR, border);
        drawStillMark(canvas, lobeX, cy);

        TimerState s = engine.snapshot();
        long seconds = Math.max(0L, (s.remainingMillis + 999L) / 1000L);
        String clock = String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);

        if (!expanded) {
            meta.setTextSize(Ui.dp(getContext(), 6.8f));
            time.setTextSize(Ui.dp(getContext(), 17f));
            String phase = engine.phaseLabel(s.phase).replace(" BREAK", "").toUpperCase(Locale.US);
            canvas.drawText(phase, Ui.dp(getContext(), 53), Ui.dp(getContext(), 15), meta);
            canvas.drawText(clock, Ui.dp(getContext(), 82), Ui.dp(getContext(), 33), time);
        } else {
            time.setTextSize(Ui.dp(getContext(), 15.5f));
            canvas.drawText(clock, Ui.dp(getContext(), 51), Ui.dp(getContext(), 29), time);
            float start = Ui.dp(getContext(), 80);
            float zone = (getWidth() - start - Ui.dp(getContext(), 3)) / 3f;
            for (int i = 0; i < 3; i++) {
                float x = start + zone * (i + 0.5f);
                canvas.drawCircle(x, cy, Ui.dp(getContext(), 10.2f), controlFill);
            }
            drawPausePlay(canvas, start + zone * 0.5f, cy, s.running);
            drawSkip(canvas, start + zone * 1.5f, cy);
            drawClose(canvas, start + zone * 2.5f, cy);
        }

        float fraction = Math.max(0f, Math.min(1f, (float) s.remainingMillis / Math.max(1f, engine.durationFor(s.phase))));
        float left = Ui.dp(getContext(), 38);
        float right = getWidth() - Ui.dp(getContext(), 8);
        float y = h - Ui.dp(getContext(), 4);
        accentSoft.setStrokeWidth(Ui.dp(getContext(), 1.5f));
        canvas.drawLine(left, y, right, y, accentSoft);
        accent.setStrokeWidth(Ui.dp(getContext(), 1.5f));
        canvas.drawLine(left, y, left + (right - left) * fraction, y, accent);
    }

    private void drawStillMark(Canvas canvas, float cx, float cy) {
        float d = Ui.dp(getContext(), 1);
        markTop.reset();
        markTop.moveTo(cx - 6 * d, cy - 3 * d);
        markTop.cubicTo(cx - 3 * d, cy - 7 * d, cx + 4 * d, cy - 7 * d, cx + 6 * d, cy - 3 * d);
        markTop.cubicTo(cx + 7 * d, cy - 1 * d, cx + 3 * d, cy, cx, cy + 1 * d);
        canvas.drawPath(markTop, ivory);

        markBottom.reset();
        markBottom.moveTo(cx, cy + 1 * d);
        markBottom.cubicTo(cx - 4 * d, cy + 2 * d, cx - 7 * d, cy + 3 * d, cx - 6 * d, cy + 6 * d);
        markBottom.cubicTo(cx - 3 * d, cy + 9 * d, cx + 4 * d, cy + 8 * d, cx + 7 * d, cy + 4 * d);
        canvas.drawPath(markBottom, accent);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = lastRawX = event.getRawX();
                downRawY = lastRawY = event.getRawY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dxTotal = event.getRawX() - downRawX;
                float dyTotal = event.getRawY() - downRawY;
                if (!dragging && Math.hypot(dxTotal, dyTotal) > Ui.dp(getContext(), 7)) dragging = true;
                if (dragging) {
                    int dx = Math.round(event.getRawX() - lastRawX);
                    int dy = Math.round(event.getRawY() - lastRawY);
                    host.moveBy(dx, dy);
                    lastRawX = event.getRawX();
                    lastRawY = event.getRawY();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    host.snapToEdge();
                    return true;
                }
                Ui.feedback(this);
                if (!expanded) {
                    expanded = true;
                    host.setExpanded(true);
                } else {
                    handleExpandedTap(event.getX());
                }
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void handleExpandedTap(float x) {
        float start = Ui.dp(getContext(), 80);
        if (x < start) {
            expanded = false;
            host.setExpanded(false);
            return;
        }
        float zone = (getWidth() - start - Ui.dp(getContext(), 3)) / 3f;
        int index = Math.min(2, Math.max(0, (int)((x - start) / zone)));
        if (index == 0) {
            TimerState s = engine.snapshot();
            if (s.running) engine.pause(); else engine.start();
        } else if (index == 1) {
            engine.skip();
        } else {
            host.closeOverlay();
        }
    }

    private void drawPausePlay(Canvas c, float x, float y, boolean running) {
        float s = Ui.dp(getContext(), 4.7f);
        if (running) {
            c.drawLine(x - s / 2, y - s, x - s / 2, y + s, glyph);
            c.drawLine(x + s / 2, y - s, x + s / 2, y + s, glyph);
        } else {
            Path p = new Path();
            p.moveTo(x - s * 0.7f, y - s);
            p.lineTo(x + s, y);
            p.lineTo(x - s * 0.7f, y + s);
            p.close();
            c.drawPath(p, glyph);
        }
    }

    private void drawSkip(Canvas c, float x, float y) {
        float s = Ui.dp(getContext(), 4.6f);
        c.drawLine(x - s, y - s, x + s * 0.3f, y, glyph);
        c.drawLine(x + s * 0.3f, y, x - s, y + s, glyph);
        c.drawLine(x + s, y - s, x + s, y + s, glyph);
    }

    private void drawClose(Canvas c, float x, float y) {
        float s = Ui.dp(getContext(), 4f);
        c.drawLine(x - s, y - s, x + s, y + s, glyph);
        c.drawLine(x + s, y - s, x - s, y + s, glyph);
    }
}
