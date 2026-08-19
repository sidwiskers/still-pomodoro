package app.still.pomodoro;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Locale;

final class WheelPickerView extends View {
    private final Paint selected = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint neighbor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF focusRect = new RectF();

    private final int min;
    private final int max;
    private float position;
    private float downY;
    private float downPosition;
    private int lastFeedbackValue;
    private VelocityTracker velocityTracker;
    private ValueAnimator settleAnimator;

    WheelPickerView(Context context, int min, int max, int value) {
        super(context);
        this.min = min;
        this.max = max;
        this.position = clamp(value);
        this.lastFeedbackValue = Math.round(position);

        setClickable(true);
        setFocusable(true);
        setMinimumWidth(Ui.dp(context, 86));
        setMinimumHeight(Ui.dp(context, 66));

        selected.setColor(Ui.TEXT);
        selected.setTextAlign(Paint.Align.CENTER);
        selected.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        neighbor.setColor(Ui.MUTED_2);
        neighbor.setTextAlign(Paint.Align.CENTER);
        neighbor.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        focusFill.setColor(Color.argb(72, 53, 57, 48));
        updateDescription();
    }

    int getValue() {
        return Math.round(position);
    }

    void setValue(int value) {
        position = clamp(value);
        lastFeedbackValue = Math.round(position);
        updateDescription();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float slot = Ui.dp(getContext(), 22);

        focusRect.set(Ui.dp(getContext(), 9), cy - Ui.dp(getContext(), 11), w - Ui.dp(getContext(), 9), cy + Ui.dp(getContext(), 11));
        canvas.drawRoundRect(focusRect, Ui.dp(getContext(), 9), Ui.dp(getContext(), 9), focusFill);

        selected.setTextSize(Ui.dp(getContext(), 12.5f));
        neighbor.setTextSize(Ui.dp(getContext(), 10.5f));

        int centerValue = Math.round(position);
        float fraction = position - centerValue;
        for (int offset = -2; offset <= 2; offset++) {
            int value = centerValue + offset;
            if (value < min || value > max) continue;
            float y = cy + (offset - fraction) * slot;
            Paint paint = offset == 0 && Math.abs(fraction) < 0.5f ? selected : neighbor;
            if (y < -slot || y > h + slot) continue;
            Paint.FontMetrics fm = paint.getFontMetrics();
            float baseline = y - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(String.format(Locale.US, "%d", value), cx, baseline, paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (settleAnimator != null) settleAnimator.cancel();
                downY = event.getY();
                downPosition = position;
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) velocityTracker.addMovement(event);
                float slot = Ui.dp(getContext(), 22);
                position = clamp(downPosition + (downY - event.getY()) / slot);
                maybeFeedback();
                updateDescription();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float velocityY = 0f;
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                    velocityTracker.computeCurrentVelocity(1000);
                    velocityY = velocityTracker.getYVelocity();
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                float projected = position - velocityY / Math.max(1f, Ui.dp(getContext(), 22)) * 0.055f;
                settleTo(Math.round(clamp(projected)));
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void settleTo(int target) {
        target = Math.max(min, Math.min(max, target));
        if (settleAnimator != null) settleAnimator.cancel();
        settleAnimator = ValueAnimator.ofFloat(position, target);
        settleAnimator.setDuration(170L);
        settleAnimator.setInterpolator(new DecelerateInterpolator());
        settleAnimator.addUpdateListener(a -> {
            position = (float) a.getAnimatedValue();
            maybeFeedback();
            updateDescription();
            invalidate();
        });
        settleAnimator.start();
    }

    private void maybeFeedback() {
        int value = Math.round(position);
        if (value != lastFeedbackValue) {
            lastFeedbackValue = value;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private float clamp(float value) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateDescription() {
        setContentDescription("Value " + Math.round(position));
    }
}
