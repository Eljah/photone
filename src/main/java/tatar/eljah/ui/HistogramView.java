package tatar.eljah.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class HistogramView extends View {
    private static final int DEFAULT_COLOR = Color.parseColor("#4CAF50");
    private static final int AXIS_COLOR = Color.WHITE;
    private static final float AXIS_STROKE = 2f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] histogram = new float[0];

    public HistogramView(Context context) {
        super(context);
        init();
    }

    public HistogramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HistogramView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setColor(DEFAULT_COLOR);
        axisPaint.setColor(AXIS_COLOR);
        axisPaint.setStrokeWidth(AXIS_STROKE);
    }

    public void setHistogram(float[] histogram) {
        if (histogram == null) {
            this.histogram = new float[0];
        } else {
            this.histogram = histogram.clone();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        int leftPadding = getPaddingLeft() + 16;
        int rightPadding = getPaddingRight() + 16;
        int topPadding = getPaddingTop() + 8;
        int bottomPadding = getPaddingBottom() + 16;

        int plotWidth = width - leftPadding - rightPadding;
        int plotHeight = height - topPadding - bottomPadding;
        if (plotWidth <= 0 || plotHeight <= 0) {
            return;
        }

        int left = leftPadding;
        int bottom = topPadding + plotHeight;
        canvas.drawLine(left, topPadding, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, left + plotWidth, bottom, axisPaint);

        if (histogram.length == 0) {
            return;
        }

        float barWidth = plotWidth / (float) histogram.length;
        for (int i = 0; i < histogram.length; i++) {
            float value = Math.max(0f, Math.min(1f, histogram[i]));
            float barHeight = value * plotHeight;
            float barLeft = left + i * barWidth;
            float barTop = bottom - barHeight;
            canvas.drawRect(barLeft, barTop, barLeft + barWidth * 0.9f, bottom, barPaint);
        }
    }
}
