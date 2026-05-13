package com.lingua.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Compact 30-day streak calendar similar to GitHub's contribution graph.
 *
 * Shows the last 30 days as a 5x6 grid of cells. Each cell is shaded by the
 * activity level on that day (0–4):
 *   0 = no activity (gray), 1+ = various greens.
 */
public class StreakCalendarView extends View {
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** activity[i] = activity level for day (today - i). 0..4 scale. */
    private int[] activity = new int[30];

    public StreakCalendarView(Context context) { this(context, null); }
    public StreakCalendarView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }
    public StreakCalendarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        cellPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFFAFAFAF);
        textPaint.setTextSize(spToPx(10));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Sets the 30-day activity. activity[0] = today, activity[29] = 30 days ago. */
    public void setActivity(int[] activity) {
        if (activity != null && activity.length > 0) {
            this.activity = new int[Math.max(30, activity.length)];
            int copyLen = Math.min(activity.length, this.activity.length);
            System.arraycopy(activity, 0, this.activity, 0, copyLen);
        }
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        // 6 cols, square cells with margins
        int cell = (w - getPaddingLeft() - getPaddingRight()) / 6;
        int rows = 5;
        int desiredHeight = cell * rows + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(w, desiredHeight);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int cell = w / 6;
        int margin = (int) (cell * 0.10);
        int size = cell - margin * 2;

        // Render newest day at top-left (row 0, col 0). Iterate 30 cells.
        for (int i = 0; i < 30; i++) {
            int row = i / 6;
            int col = i % 6;
            int level = i < activity.length ? activity[i] : 0;
            cellPaint.setColor(colorForLevel(level));
            float left = getPaddingLeft() + col * cell + margin;
            float top = getPaddingTop() + row * cell + margin;
            RectF r = new RectF(left, top, left + size, top + size);
            c.drawRoundRect(r, 6f, 6f, cellPaint);

            // Today indicator (small flame in cell 0)
            if (i == 0 && level > 0) {
                textPaint.setTextSize(spToPx(14));
                textPaint.setColor(0xFFFFFFFF);
                c.drawText("🔥", left + size / 2f, top + size / 2f + spToPx(5), textPaint);
            }
        }
    }

    private int colorForLevel(int level) {
        switch (Math.max(0, Math.min(4, level))) {
            case 0: return 0xFFEBEDF0;
            case 1: return 0xFF9BE9A8;
            case 2: return 0xFF40C463;
            case 3: return 0xFF30A14E;
            default: return 0xFF216E39;
        }
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
