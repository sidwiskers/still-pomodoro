package app.still.pomodoro;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(10, 10, 10);
    static final int SURFACE = Color.rgb(22, 23, 20);
    static final int SURFACE_2 = Color.rgb(32, 34, 29);
    static final int TEXT = Color.rgb(243, 240, 232);
    static final int MUTED = Color.rgb(151, 151, 142);
    static final int ACCENT = Color.rgb(216, 255, 106);

    static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    static TextView pill(Context context, String text, boolean accent) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(14f);
        view.setTextColor(accent ? Color.rgb(18, 20, 14) : TEXT);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans", Typeface.BOLD));
        view.setLetterSpacing(0.02f);
        view.setMinHeight(dp(context, 48));
        view.setPadding(dp(context, 20), 0, dp(context, 20), 0);
        view.setBackground(roundRect(accent ? ACCENT : SURFACE_2, 24f, context));
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.965f).scaleY(0.965f).setDuration(90).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            }
            return false;
        });
        return view;
    }

    static GradientDrawable roundRect(int color, float radiusDp, Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(context, radiusDp));
        return d;
    }

    static void feedback(View view) {
        view.playSoundEffect(SoundEffectConstants.CLICK);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    static void flash(View view) {
        view.setAlpha(0.7f);
        view.animate().alpha(1f).setDuration(180).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { view.setAlpha(1f); }
        }).start();
    }

    private Ui() {}
}
