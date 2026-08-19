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

    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surfaceInset = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lobe = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbitTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbitProgress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ivory = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint meta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint controlRail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint controlActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint status = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF bodyRect = new RectF();
    private final RectF insetRect = new RectF();
    private final RectF orbitRect = new RectF();
    private final RectF railRect = new RectF();
    private final RectF activeRect = new RectF();
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

        shadow.setColor(Color.argb(76, 0, 0, 0));
        surface.setColor(Color.argb(252, 13, 15, 12));
        surfaceInset.setColor(Color.argb(255, 18, 21, 17));
        lobe.setColor(Color.argb(255, 21, 24, 19));

        border.setColor(Color.argb(205, 54, 60, 49));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Ui.dp(context, 0.9f));

        rim.setColor(Color.argb(28, 243, 240, 232));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Ui.dp(context, 0.75f));
        rim.setStrokeCap(Paint.Cap.ROUND);

        orbitTrack.setColor(Color.argb(66, 216, 255, 106));
        orbitTrack.setStyle(Paint.Style.STROKE);
        orbitTrack.setStrokeWidth(Ui.dp(context, 1.65f));
        orbitTrack.setStrokeCap(Paint.Cap.ROUND);

        orbitProgress.setColor(Ui.ACCENT);
        orbitProgress.setStyle(Paint.Style.STROKE);
        orbitProgress.setStrokeWidth(Ui.dp(context, 2.05f));
        orbitProgress.setStrokeCap(Paint.Cap.ROUND);

        accent.setColor(Ui.ACCENT);
        accent.setStyle(Paint.Style.STROKE);
        accent.setStrokeCap(Paint.Cap.ROUND);
        accent.setStrokeWidth(Ui.dp(context, 1.65f));

        ivory.setColor(Ui.TEXT);
        ivory.setStyle(Paint.Style.STROKE);
        ivory.setStrokeCap(Paint.Cap.ROUND);
        ivory.setStrokeWidth(Ui.dp(context, 1.65f));

        time.setColor(Ui.TEXT);
        time.setTextAlign(Paint.Align.LEFT);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        meta.setColor(Ui.MUTED);
        meta.setTextAlign(Paint.Align.LEFT);
        meta.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        glyph.setColor(Ui.TEXT);
        glyph.setStrokeWidth(Ui.dp(context, 1.45f));
        glyph.setStrokeCap(Paint.Cap.ROUND);
        glyph.setStyle(Paint.Style.STROKE);

        controlRail.setColor(Color.argb(116, 25, 29, 23));
        controlActive.setColor(Color.argb(48, 216, 255, 106));

        divider.setColor(Color.argb(72, 78, 84, 69));
        divider.setStrokeWidth(Ui.dp(context, 0.8f));

        status.setStyle(Paint.Style.FILL);
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

        float d = Ui.dp(getContext(), 1);
        float w = getWidth();
        float h = getHeight();
        float topSectionH = Math.min(h, 50f * d);
        float bodyLeft = 14f * d;
        float bodyRadius = 17f * d;

        bodyRect.set(bodyLeft, 2f * d, w - 2f * d, h - 2f * d);
        RectF shadowRect = new RectF(bodyRect);
        shadowRect.offset(0, 2.2f * d);
        canvas.drawRoundRect(shadowRect, bodyRadius, bodyRadius, shadow);
        canvas.drawRoundRect(bodyRect, bodyRadius, bodyRadius, surface);
        canvas.drawRoundRect(bodyRect, bodyRadius, bodyRadius, border);

        insetRect.set(bodyLeft + 2f * d, 4f * d, w - 4f * d, topSectionH - 3f * d);
        canvas.drawRoundRect(insetRect, 14.5f * d, 14.5f * d, surfaceInset);
        canvas.drawLine(43f * d, 6.5f * d, w - 16f * d, 6.5f * d, rim);

        float lobeX = 21f * d;
        float lobeY = 25f * d;
        float lobeR = 18f * d;
        canvas.drawCircle(lobeX, lobeY + 1.6f * d, lobeR, shadow);
        canvas.drawCircle(lobeX, lobeY, lobeR, lobe);
        canvas.drawCircle(lobeX, lobeY, lobeR, border);

        TimerState s = engine.snapshot();
        long seconds = Math.max(0L, (s.remainingMillis + 999L) / 1000L);
        String clock = String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
        float fraction = Math.max(0f, Math.min(1f,
                (float) s.remainingMillis / Math.max(1f, engine.durationFor(s.phase))));

        float orbitR = 14.6f * d;
        orbitRect.set(lobeX - orbitR, lobeY - orbitR, lobeX + orbitR, lobeY + orbitR);
        canvas.drawArc(orbitRect, 135f, 270f, false, orbitTrack);
        if (fraction > 0.002f) canvas.drawArc(orbitRect, 135f, 270f * fraction, false, orbitProgress);
        drawStillMark(canvas, lobeX, lobeY);

        float textX = 45f * d;
        meta.setTextSize(6.6f * d);
        time.setTextSize(17.8f * d);
        String phase = engine.phaseLabel(s.phase).replace(" BREAK", "").toUpperCase(Locale.US);
        canvas.drawText(phase, textX, 17f * d, meta);
        canvas.drawText(clock, textX, 36f * d, time);

        float statusX = w - 13f * d;
        float statusY = 14f * d;
        if (s.running) {
            status.setColor(Ui.ACCENT);
            canvas.drawCircle(statusX, statusY, 2.1f * d, status);
            status.setColor(Color.argb(36, 216, 255, 106));
            canvas.drawCircle(statusX, statusY, 4.4f * d, status);
        } else {
            status.setColor(Color.argb(130, 118, 124, 106));
            canvas.drawCircle(statusX, statusY, 1.7f * d, status);
        }

        if (expanded && h > 58f * d) drawControlDeck(canvas, s, d, w, h);
    }

    private void drawControlDeck(Canvas canvas, TimerState s, float d, float w, float h) {
        float railTop = 52f * d;
        float railBottom = h - 8f * d;
        float railLeft = 32f * d;
        float railRight = w - 9f * d;
        if (railBottom <= railTop + 8f * d) return;

        railRect.set(railLeft, railTop, railRight, railBottom);
        canvas.drawRoundRect(railRect, 11f * d, 11f * d, controlRail);

        float zone = (railRight - railLeft) / 3f;
        activeRect.set(railLeft + 3f * d, railTop + 3f * d,
                railLeft + zone - 3f * d, railBottom - 3f * d);
        canvas.drawRoundRect(activeRect, 8f * d, 8f * d, controlActive);

        float cy = (railTop + railBottom) / 2f;
        float x1 = railLeft + zone * 0.5f;
        float x2 = railLeft + zone * 1.5f;
        float x3 = railLeft + zone * 2.5f;

        canvas.drawLine(railLeft + zone, railTop + 7f * d,
                railLeft + zone, railBottom - 7f * d, divider);
        canvas.drawLine(railLeft + zone * 2f, railTop + 7f * d,
                railLeft + zone * 2f, railBottom - 7f * d, divider);

        drawPausePlay(canvas, x1, cy, s.running);
        drawSkip(canvas, x2, cy);
        drawClose(canvas, x3, cy);

        Paint accentLine = orbitProgress;
        float underlineY = railBottom - 3.2f * d;
        canvas.drawLine(x1 - 6f * d, underlineY, x1 + 6f * d, underlineY, accentLine);
    }

    private void drawStillMark(Canvas canvas, float cx, float cy) {
        float d = Ui.dp(getContext(), 1);
        markTop.reset();
        markTop.moveTo(cx - 5.2f * d, cy - 2.8f * d);
        markTop.cubicTo(cx - 2.8f * d, cy - 6.1f * d,
                cx + 3.4f * d, cy - 6.2f * d, cx + 5.2f * d, cy - 2.9f * d);
        markTop.cubicTo(cx + 6.2f * d, cy - 1.0f * d,
                cx + 2.8f * d, cy - 0.1f * d, cx, cy + 0.8f * d);
        canvas.drawPath(markTop, ivory);

        markBottom.reset();
        markBottom.moveTo(cx, cy + 0.8f * d);
        markBottom.cubicTo(cx - 3.4f * d, cy + 1.8f * d,
                cx - 5.9f * d, cy + 2.8f * d, cx - 5.0f * d, cy + 5.2f * d);
        markBottom.cubicTo(cx - 2.4f * d, cy + 7.5f * d,
                cx + 3.8f * d, cy + 6.8f * d, cx + 5.8f * d, cy + 3.5f * d);
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
                    handleExpandedTap(event.getX(), event.getY());
                }
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void handleExpandedTap(float x, float y) {
        float d = Ui.dp(getContext(), 1);
        if (y < 49f * d) {
            expanded = false;
            host.setExpanded(false);
            return;
        }

        float railLeft = 32f * d;
        float railRight = getWidth() - 9f * d;
        if (x < railLeft || x > railRight) {
            expanded = false;
            host.setExpanded(false);
            return;
        }

        float zone = (railRight - railLeft) / 3f;
        int index = Math.min(2, Math.max(0, (int) ((x - railLeft) / zone)));
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
        float s = Ui.dp(getContext(), 4.5f);
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
        float s = Ui.dp(getContext(), 4.4f);
        c.drawLine(x - s, y - s, x + s * 0.3f, y, glyph);
        c.drawLine(x + s * 0.3f, y, x - s, y + s, glyph);
        c.drawLine(x + s, y - s, x + s, y + s, glyph);
    }

    private void drawClose(Canvas c, float x, float y) {
        float s = Ui.dp(getContext(), 3.8f);
        c.drawLine(x - s, y - s, x + s, y + s, glyph);
        c.drawLine(x + s, y - s, x - s, y + s, glyph);
    }
}
