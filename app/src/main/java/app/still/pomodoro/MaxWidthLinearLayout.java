package app.still.pomodoro;

import android.content.Context;
import android.widget.LinearLayout;

final class MaxWidthLinearLayout extends LinearLayout {
    private final int maxWidth;

    MaxWidthLinearLayout(Context context, int maxWidthDp) {
        super(context);
        maxWidth = Ui.dp(context, maxWidthDp);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int requested = MeasureSpec.getSize(widthMeasureSpec);
        int mode = MeasureSpec.getMode(widthMeasureSpec);
        if (mode != MeasureSpec.UNSPECIFIED && requested > maxWidth) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
