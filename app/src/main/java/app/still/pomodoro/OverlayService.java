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

import java.util.concurrent.atomic.AtomicBoolean;

public final class OverlayService extends Service implements OverlayChipView.Host {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final int COLLAPSED_WIDTH_DP = 126;
    private static final int EXPANDED_WIDTH_DP = 186;
    private static final int HEIGHT_DP = 46;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private OverlayChipView chip;
    private ValueAnimator snapAnimator;

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
                Ui.dp(this, COLLAPSED_WIDTH_DP), Ui.dp(this, HEIGHT_DP),
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
        if (chip != null && windowManager != null) {
            try { windowManager.removeView(chip); } catch (IllegalArgumentException ignored) {}
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void moveBy(int dx, int dy) {
        if (params == null || windowManager == null) return;
        params.x += dx;
        params.y += dy;
        clampY();
        windowManager.updateViewLayout(chip, params);
    }

    @Override public void snapToEdge() {
        if (params == null || windowManager == null) return;
        int screen = getResources().getDisplayMetrics().widthPixels;
        int target = params.x + params.width / 2 < screen / 2 ? Ui.dp(this, 9) : screen - params.width - Ui.dp(this, 9);
        if (snapAnimator != null) snapAnimator.cancel();
        snapAnimator = ValueAnimator.ofInt(params.x, target);
        snapAnimator.setDuration(160L);
        snapAnimator.addUpdateListener(a -> {
            params.x = (int) a.getAnimatedValue();
            try { windowManager.updateViewLayout(chip, params); } catch (IllegalArgumentException ignored) {}
        });
        snapAnimator.start();
    }

    @Override public void setExpanded(boolean expanded) {
        if (params == null || windowManager == null) return;
        int oldWidth = params.width;
        params.width = Ui.dp(this, expanded ? EXPANDED_WIDTH_DP : COLLAPSED_WIDTH_DP);
        int screen = getResources().getDisplayMetrics().widthPixels;
        if (params.x > screen / 2) params.x -= params.width - oldWidth;
        params.x = Math.max(Ui.dp(this, 8), Math.min(screen - params.width - Ui.dp(this, 8), params.x));
        windowManager.updateViewLayout(chip, params);
    }

    @Override public void closeOverlay() { stopSelf(); }

    private void clampY() {
        int screen = getResources().getDisplayMetrics().heightPixels;
        params.y = Math.max(Ui.dp(this, 22), Math.min(screen - params.height - Ui.dp(this, 22), params.y));
    }
}
