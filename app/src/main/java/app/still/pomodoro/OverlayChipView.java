package app.still.pomodoro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentSoft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint meta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
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

        surface.setColor(Color.argb(246, 17, 19, 16));
        border.setColor(Color.argb(180, 48, 52, 43));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Ui.dp(context, 1));

        accent.setColor(Ui.ACCENT);
        accent.setStrokeCap(Paint.Cap.ROUND);
        accentSoft.setColor(Color.argb(74, 216, 255, 106));
        accentSoft.setStrokeCap(Paint.Cap.ROUND);

        time.setColor(Ui.TEXT);
        time.setTextAlign(Paint.Align.CENTER);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        meta.setColor(Ui.MUTED);
        meta.setTextAlign(Paint.Align.CENTER);
        meta.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        glyph.setColor(Ui.TEXT);
        glyph.setStrokeWidth(Ui.dp(context, 1.7f));
        glyph.setStrokeCap(Paint.Cap.ROUND);
        glyph.setStyle(Paint.Style.STROKE);
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
        float radius = Ui.dp(getContext(), 22);
        rect.set(Ui.dp(getContext(), 1), Ui.dp(getContext(), 1), getWidth() - Ui.dp(getContext(), 1), getHeight() - Ui.dp(getContext(), 1));
        canvas.drawRoundRect(rect, radius, radius, surface);
        canvas.drawRoundRect(rect, radius, radius, border);

        TimerState s = engine.snapshot();
        long seconds = Math.max(0L, (s.remainingMillis + 999L) / 1000L);
        String clock = String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);

        accent.setStrokeWidth(Ui.dp(getContext(), 2.4f));
        canvas.drawLine(Ui.dp(getContext(), 13), Ui.dp(getContext(), 15), Ui.dp(getContext(), 13), getHeight() - Ui.dp(getContext(), 15), accent);

        if (!expanded) {
            meta.setTextSize(Ui.dp(getContext(), 8.5f));
            time.setTextSize(Ui.dp(getContext(), 19));
            canvas.drawText(engine.phaseLabel(s.phase).replace(" BREAK", "").toUpperCase(Locale.US), Ui.dp(getContext(), 45), Ui.dp(getContext(), 22), meta);
            canvas.drawText(clock, getWidth() * 0.63f, Ui.dp(getContext(), 36), time);
        } else {
            time.setTextSize(Ui.dp(getContext(), 18));
            canvas.drawText(clock, Ui.dp(getContext(), 50), Ui.dp(getContext(), 35), time);
            float zone = (getWidth() - Ui.dp(getContext(), 92)) / 3f;
            drawPausePlay(canvas, Ui.dp(getContext(), 92) + zone * 0.5f, getHeight() / 2f, s.running);
            drawSkip(canvas, Ui.dp(getContext(), 92) + zone * 1.5f, getHeight() / 2f);
            drawClose(canvas, Ui.dp(getContext(), 92) + zone * 2.5f, getHeight() / 2f);
        }

        float fraction = Math.max(0f, Math.min(1f, (float) s.remainingMillis / Math.max(1f, engine.durationFor(s.phase))));
        float left = Ui.dp(getContext(), 23);
        float right = getWidth() - Ui.dp(getContext(), 17);
        float y = getHeight() - Ui.dp(getContext(), 6);
        accentSoft.setStrokeWidth(Ui.dp(getContext(), 2.2f));
        canvas.drawLine(left, y, right, y, accentSoft);
        accent.setStrokeWidth(Ui.dp(getContext(), 2.2f));
        canvas.drawLine(left, y, left + (right - left) * fraction, y, accent);
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
        float start = Ui.dp(getContext(), 88);
        if (x < start) {
            expanded = false;
            host.setExpanded(false);
            return;
        }
        float zone = (getWidth() - start) / 3f;
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
        float s = Ui.dp(getContext(), 6);
        if (running) {
            c.drawLine(x - s / 2, y - s, x - s / 2, y + s, glyph);
            c.drawLine(x + s / 2, y - s, x + s / 2, y + s, glyph);
        } else {
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(x - s * 0.7f, y - s);
            p.lineTo(x + s, y);
            p.lineTo(x - s * 0.7f, y + s);
            p.close();
            c.drawPath(p, glyph);
        }
    }

    private void drawSkip(Canvas c, float x, float y) {
        float s = Ui.dp(getContext(), 6);
        c.drawLine(x - s, y - s, x + s * 0.35f, y, glyph);
        c.drawLine(x + s * 0.35f, y, x - s, y + s, glyph);
        c.drawLine(x + s, y - s, x + s, y + s, glyph);
    }

    private void drawClose(Canvas c, float x, float y) {
        float s = Ui.dp(getContext(), 5);
        c.drawLine(x - s, y - s, x + s, y + s, glyph);
        c.drawLine(x + s, y - s, x - s, y + s, glyph);
    }
}
