package app.still.pomodoro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

final class FocusHistoryView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long[] values = new long[7];

    FocusHistoryView(Context context) {
        super(context);
    }

    void setValues(long[] source) {
        for (int i = 0; i < values.length; i++) {
            values[i] = source != null && i < source.length ? Math.max(0L, source[i]) : 0L;
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float height = getHeight();
        float left = 4f * density;
        float right = width - 4f * density;
        float chartTop = 12f * density;
        float chartBottom = height - 52f * density;
        float slot = (right - left) / 7f;
        float barWidth = Math.min(26f * density, slot * 0.52f);

        long max = 60L * 60_000L;
        for (long value : values) max = Math.max(max, value);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, density));
        paint.setColor(Ui.SURFACE_3);
        canvas.drawLine(left, chartBottom, right, chartBottom, paint);

        LocalDate first = LocalDate.now().minusDays(6);
        for (int i = 0; i < 7; i++) {
            float center = left + slot * (i + 0.5f);
            long value = values[i];
            float available = Math.max(1f, chartBottom - chartTop);
            float barHeight = value <= 0L ? 0f : Math.max(4f * density, available * ((float) value / (float) max));
            if (barHeight > 0f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(i == 6 ? Ui.ACCENT : Ui.SURFACE_3);
                RectF bar = new RectF(center - barWidth / 2f, chartBottom - barHeight, center + barWidth / 2f, chartBottom);
                canvas.drawRoundRect(bar, 5f * density, 5f * density, paint);
            }

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            paint.setTextSize(10f * getResources().getDisplayMetrics().scaledDensity);
            paint.setColor(i == 6 ? Ui.ACCENT : Ui.MUTED);
            String day = first.plusDays(i).getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.getDefault());
            canvas.drawText(day.toUpperCase(Locale.getDefault()), center, chartBottom + 17f * density, paint);

            paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
            paint.setTextSize(8.5f * getResources().getDisplayMetrics().scaledDensity);
            paint.setColor(i == 6 ? Ui.TEXT : Ui.MUTED_2);
            canvas.drawText(compact(values[i]), center, chartBottom + 35f * density, paint);
        }
    }

    private static String compact(long millis) {
        long minutes = Math.max(0L, millis / 60_000L);
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        if (hours > 0L) return rest == 0L ? hours + "h" : hours + "h" + rest + "m";
        return minutes + "m";
    }
}
