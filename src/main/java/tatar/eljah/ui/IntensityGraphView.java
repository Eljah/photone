package tatar.eljah.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class IntensityGraphView extends View {
    public interface OnThresholdChangedListener {
        void onThresholdChanged(float threshold);
    }

    private static final int MAX_POINTS = 180;

    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<Float> intensities = new ArrayList<>();
    private float threshold = 0.2f;
    private OnThresholdChangedListener onThresholdChangedListener;

    public IntensityGraphView(Context context) {
        super(context);
        init();
    }

    public IntensityGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public IntensityGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        axisPaint.setColor(Color.WHITE);
        axisPaint.setStrokeWidth(2f);

        linePaint.setColor(Color.parseColor("#00E5FF"));
        linePaint.setStrokeWidth(3f);
        linePaint.setStyle(Paint.Style.STROKE);

        thresholdPaint.setColor(Color.parseColor("#B000FF"));
        thresholdPaint.setStrokeWidth(4f);
    }

    public void addIntensity(float value) {
        float normalized = clamp01(value);
        if (intensities.size() >= MAX_POINTS) {
            intensities.remove(0);
        }
        intensities.add(normalized);
        postInvalidateOnAnimation();
    }

    public void clear() {
        intensities.clear();
        invalidate();
    }

    public void setThreshold(float threshold) {
        this.threshold = clamp01(threshold);
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

        int left = getPaddingLeft() + 16;
        int top = getPaddingTop() + 16;
        int right = width - getPaddingRight() - 16;
        int bottom = height - getPaddingBottom() - 16;
        if (right <= left || bottom <= top) {
            return;
        }

        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);

        float thresholdY = bottom - threshold * (bottom - top);
        canvas.drawLine(left, thresholdY, right, thresholdY, thresholdPaint);

        if (intensities.size() < 2) {
            return;
        }

        float stepX = (right - left) / (float) (MAX_POINTS - 1);
        float prevX = left + Math.max(0, MAX_POINTS - intensities.size()) * stepX;
        float prevY = bottom - intensities.get(0) * (bottom - top);

        for (int i = 1; i < intensities.size(); i++) {
            float x = prevX + stepX;
            float y = bottom - intensities.get(i) * (bottom - top);
            canvas.drawLine(prevX, prevY, x, y, linePaint);
            prevX = x;
            prevY = y;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
            return super.onTouchEvent(event);
        }
        int top = getPaddingTop() + 16;
        int bottom = getHeight() - getPaddingBottom() - 16;
        if (bottom <= top) {
            return true;
        }
        float normalized = (bottom - event.getY()) / (float) (bottom - top);
        threshold = clamp01(normalized);
        if (onThresholdChangedListener != null) {
            onThresholdChangedListener.onThresholdChanged(threshold);
        }
        invalidate();
        return true;
    }

    public void setOnThresholdChangedListener(OnThresholdChangedListener listener) {
        onThresholdChangedListener = listener;
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
