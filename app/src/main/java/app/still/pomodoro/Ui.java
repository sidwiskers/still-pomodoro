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
    static final int BG = Color.rgb(9, 10, 8);
    static final int SURFACE = Color.rgb(18, 19, 16);
    static final int SURFACE_2 = Color.rgb(28, 30, 25);
    static final int SURFACE_3 = Color.rgb(38, 40, 34);
    static final int TEXT = Color.rgb(246, 243, 234);
    static final int MUTED = Color.rgb(151, 153, 141);
    static final int MUTED_2 = Color.rgb(92, 95, 86);
    static final int ACCENT = Color.rgb(216, 255, 106);
    static final int ACCENT_DARK = Color.rgb(181, 224, 72);

    static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    static TextView pill(Context context, String text, boolean accent) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(accent ? 14.5f : 13.5f);
        view.setTextColor(accent ? Color.rgb(15, 17, 11) : TEXT);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(accent ? 0.015f : 0.025f);
        view.setMinHeight(dp(context, 48));
        view.setPadding(dp(context, 18), 0, dp(context, 18), 0);
        view.setBackground(accent ? accentButton(context) : darkButton(context));
        view.setClickable(true);
        view.setFocusable(true);
        addPressMotion(view);
        return view;
    }

    static TextView tile(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(12.5f);
        view.setTextColor(TEXT);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(0.035f);
        view.setMinHeight(dp(context, 46));
        view.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        view.setBackground(outlinedSurface(context, 18f));
        view.setClickable(true);
        view.setFocusable(true);
        addPressMotion(view);
        return view;
    }

    static TextView tag(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(10.5f);
        view.setTextColor(MUTED);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(0.11f);
        view.setPadding(dp(context, 13), dp(context, 8), dp(context, 13), dp(context, 8));
        view.setBackground(outlinedSurface(context, 16f));
        return view;
    }

    static GradientDrawable roundRect(int color, float radiusDp, Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(context, radiusDp));
        return d;
    }

    static GradientDrawable outlinedSurface(Context context, float radiusDp) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(31, 33, 28), Color.rgb(23, 25, 21)});
        d.setCornerRadius(dp(context, radiusDp));
        d.setStroke(dp(context, 1), Color.rgb(48, 51, 43));
        return d;
    }

    static GradientDrawable panel(Context context, float radiusDp) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(22, 24, 20), Color.rgb(14, 15, 13)});
        d.setCornerRadius(dp(context, radiusDp));
        d.setStroke(dp(context, 1), Color.rgb(36, 39, 32));
        return d;
    }

    private static GradientDrawable accentButton(Context context) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(229, 255, 146), ACCENT_DARK});
        d.setCornerRadius(dp(context, 22));
        return d;
    }

    private static GradientDrawable darkButton(Context context) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(42, 44, 38), Color.rgb(28, 30, 25)});
        d.setCornerRadius(dp(context, 22));
        d.setStroke(dp(context, 1), Color.rgb(55, 58, 49));
        return d;
    }

    private static void addPressMotion(View view) {
        view.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.972f).scaleY(0.972f).alpha(0.9f).setDuration(80).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start();
            }
            return false;
        });
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
