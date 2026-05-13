package com.lingua.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Lightweight bar chart used by the Statistics screen.
 *
 * Implemented locally so the app does not have to bundle MPAndroidChart
 * (~2 MB). Renders 7 vertical bars with day labels and a value on top.
 */
public class BarChartView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] values = new int[]{0, 0, 0, 0, 0, 0, 0};
    private String[] labels = new String[]{"T2", "T3", "T4", "T5", "T6", "T7", "CN"};

    public BarChartView(Context context) { this(context, null); }
    public BarChartView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }
    public BarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        barPaint.setColor(0xFF58CC02);
        barPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFF3C3C3C);
        textPaint.setTextSize(spToPx(12));
        textPaint.setTextAlign(Paint.Align.CENTER);
        baselinePaint.setColor(0xFFE0E0E0);
        baselinePaint.setStrokeWidth(2f);
    }

    public void setData(int[] values, String[] labels) {
        if (values != null && values.length > 0) this.values = values;
        if (labels != null && labels.length == this.values.length) this.labels = labels;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (w <= 0 || h <= 0 || values.length == 0) return;

        int maxVal = 1;
        for (int v : values) if (v > maxVal) maxVal = v;

        float labelHeight = spToPx(20);
        float valueHeight = spToPx(16);
        float chartHeight = h - labelHeight - valueHeight;
        float baseY = getPaddingTop() + valueHeight + chartHeight;

        // Baseline
        c.drawLine(getPaddingLeft(), baseY, getPaddingLeft() + w, baseY, baselinePaint);

        float slot = (float) w / values.length;
        float barWidth = slot * 0.55f;

        for (int i = 0; i < values.length; i++) {
            float ratio = (float) values[i] / maxVal;
            float barHeight = chartHeight * ratio;
            float left = getPaddingLeft() + i * slot + (slot - barWidth) / 2f;
            float top = baseY - barHeight;
            float right = left + barWidth;
            // Use a different color for today (last bar)
            barPaint.setColor(i == values.length - 1 ? 0xFFFF9600 : 0xFF58CC02);
            RectF rect = new RectF(left, top, right, baseY);
            c.drawRoundRect(rect, 8f, 8f, barPaint);

            // Value on top
            if (values[i] > 0) {
                textPaint.setColor(0xFF3C3C3C);
                textPaint.setTextSize(spToPx(11));
                c.drawText(String.valueOf(values[i]), left + barWidth / 2f, top - 6f, textPaint);
            }

            // Label below baseline
            textPaint.setColor(0xFFAFAFAF);
            textPaint.setTextSize(spToPx(12));
            c.drawText(labels[i], left + barWidth / 2f,
                    baseY + labelHeight - 4, textPaint);
        }
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
