package app.still.pomodoro;

import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import java.util.concurrent.atomic.AtomicBoolean;

public final class OverlayService extends Service implements OverlayChipView.Host {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final int COLLAPSED_WIDTH_DP = 132;
    private static final int COLLAPSED_HEIGHT_DP = 50;
    private static final int EXPANDED_WIDTH_DP = 158;
    private static final int EXPANDED_HEIGHT_DP = 82;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private OverlayChipView chip;
    private ValueAnimator snapAnimator;
    private ValueAnimator sizeAnimator;

    static boolean isRunning() { return RUNNING.get(); }

    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        RUNNING.set(true);
        windowManager = getSystemService(WindowManager.class);
        if (windowManager == null) {
            stopSelf();
            return;
        }

        chip = new OverlayChipView(this, this);
        params = new WindowManager.LayoutParams(
                Ui.dp(this, COLLAPSED_WIDTH_DP), Ui.dp(this, COLLAPSED_HEIGHT_DP),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = getResources().getDisplayMetrics().widthPixels - params.width - Ui.dp(this, 12);
        params.y = Ui.dp(this, 92);
        params.alpha = 0.99f;
        windowManager.addView(chip, params);

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NotificationHelper.OVERLAY_ID, NotificationHelper.overlayNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NotificationHelper.OVERLAY_ID, NotificationHelper.overlayNotification(this));
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        RUNNING.set(false);
        if (snapAnimator != null) snapAnimator.cancel();
        if (sizeAnimator != null) sizeAnimator.cancel();
        if (chip != null && windowManager != null) {
            try { windowManager.removeView(chip); } catch (IllegalArgumentException ignored) {}
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void moveBy(int dx, int dy) {
        if (params == null || windowManager == null) return;
        if (sizeAnimator != null && sizeAnimator.isRunning()) sizeAnimator.cancel();
        params.x += dx;
        params.y += dy;
        clampBounds();
        windowManager.updateViewLayout(chip, params);
    }

    @Override public void snapToEdge() {
        if (params == null || windowManager == null) return;
        int screen = getResources().getDisplayMetrics().widthPixels;
        int target = params.x + params.width / 2 < screen / 2
                ? Ui.dp(this, 9)
                : screen - params.width - Ui.dp(this, 9);
        if (snapAnimator != null) snapAnimator.cancel();
        snapAnimator = ValueAnimator.ofInt(params.x, target);
        snapAnimator.setDuration(160L);
        snapAnimator.setInterpolator(new DecelerateInterpolator());
        snapAnimator.addUpdateListener(a -> {
            params.x = (int) a.getAnimatedValue();
            try { windowManager.updateViewLayout(chip, params); } catch (IllegalArgumentException ignored) {}
        });
        snapAnimator.start();
    }

    @Override public void setExpanded(boolean expanded) {
        if (params == null || windowManager == null) return;
        if (sizeAnimator != null) sizeAnimator.cancel();

        int startW = params.width;
        int startH = params.height;
        int targetW = Ui.dp(this, expanded ? EXPANDED_WIDTH_DP : COLLAPSED_WIDTH_DP);
        int targetH = Ui.dp(this, expanded ? EXPANDED_HEIGHT_DP : COLLAPSED_HEIGHT_DP);
        if (startW == targetW && startH == targetH) return;

        int screenW = getResources().getDisplayMetrics().widthPixels;
        boolean rightAnchored = params.x + startW / 2 > screenW / 2;
        int anchorRight = params.x + startW;
        int anchorX = params.x;
        int anchorCenterY = params.y + startH / 2;

        sizeAnimator = ValueAnimator.ofFloat(0f, 1f);
        sizeAnimator.setDuration(190L);
        sizeAnimator.setInterpolator(new DecelerateInterpolator());
        sizeAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            params.width = Math.round(startW + (targetW - startW) * t);
            params.height = Math.round(startH + (targetH - startH) * t);
            params.x = rightAnchored ? anchorRight - params.width : anchorX;
            params.y = anchorCenterY - params.height / 2;
            clampBounds();
            try { windowManager.updateViewLayout(chip, params); } catch (IllegalArgumentException ignored) {}
        });
        sizeAnimator.start();
    }

    @Override public void closeOverlay() { stopSelf(); }

    private void clampBounds() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int side = Ui.dp(this, 8);
        int vertical = Ui.dp(this, 22);
        params.x = Math.max(side, Math.min(screenW - params.width - side, params.x));
        params.y = Math.max(vertical, Math.min(screenH - params.height - vertical, params.y));
    }
}
