package com.example.sleppify;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class EqCurveEditorView extends View {
    private static final int BAND_COUNT = 9;
    private static final float MIN_DB = AudioEffectsService.EQ_GAIN_MIN_DB;
    private static final float MAX_DB = AudioEffectsService.EQ_GAIN_MAX_DB;
    private static final float STEP_DB = 0.5f;
    private static final float CURVE_TENSION = 0.64f;
    private static final float CONTENT_HORIZONTAL_INSET_DP = 0f;
    private static final float CONTENT_VERTICAL_INSET_DP = 10f;
    private static final float GRID_CORNER_RADIUS_DP = 0f;
    private static final float MODAL_GUIDE_TOP_INSET_DP = 12f;
    private static final float MODAL_GUIDE_BOTTOM_INSET_DP = 8f;
    private static final float[] DB_GUIDE_VALUES = {
            MAX_DB,
            MAX_DB * 0.8f,
            MAX_DB * 0.6f,
            MAX_DB * 0.4f,
            MAX_DB * 0.2f,
            0f,
            MIN_DB * 0.2f,
            MIN_DB * 0.4f,
            MIN_DB * 0.6f,
            MIN_DB * 0.8f,
            MIN_DB
        };

    private final float[] bandValues = new float[BAND_COUNT];
    private final float[] bandX = new float[BAND_COUNT];
    private final float[] bandY = new float[BAND_COUNT];

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint modalGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zeroLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path curvePath = new Path();
    private final Path fillPath = new Path();

    private float contentLeft;
    private float contentTop;
    private float contentRight;
    private float contentBottom;
    private float handleRadiusPx;

    private int activeBand = -1;
    private boolean editingEnabled = true;
    private boolean handlesVisible = true;
    private boolean gridVisible = true;
    private boolean areaFillEnabled;
    private boolean axisDbLabelsEnabled;
    private boolean modalBandGuidesEnabled;
    private OnBandChangeListener onBandChangeListener;

    public EqCurveEditorView(Context context) {
        super(context);
        init();
    }

    public EqCurveEditorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EqCurveEditorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int curveColor = ContextCompat.getColor(getContext(), R.color.stitch_blue_light);
        int gridColor = ContextCompat.getColor(getContext(), R.color.surface_high);
        int pointColor = ContextCompat.getColor(getContext(), R.color.stitch_blue_light);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(adjustAlpha(gridColor, 0.46f));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(adjustAlpha(curveColor, 0.24f));

        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(dp(4f));
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);
        curvePaint.setColor(curveColor);

        pointFillPaint.setStyle(Paint.Style.FILL);
        pointFillPaint.setColor(ContextCompat.getColor(getContext(), R.color.surface_dark));

        pointStrokePaint.setStyle(Paint.Style.STROKE);
        pointStrokePaint.setStrokeWidth(dp(2.2f));
        pointStrokePaint.setColor(curveColor);

        axisLabelPaint.setStyle(Paint.Style.FILL);
        axisLabelPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        axisLabelPaint.setTextSize(dp(7f));

        modalGuidePaint.setStyle(Paint.Style.STROKE);
        modalGuidePaint.setStrokeWidth(dp(2.8f));
        modalGuidePaint.setStrokeCap(Paint.Cap.ROUND);
        modalGuidePaint.setColor(adjustAlpha(curveColor, 0.42f));

        zeroLinePaint.setStyle(Paint.Style.STROKE);
        zeroLinePaint.setStrokeWidth(dp(1.2f));
        zeroLinePaint.setColor(adjustAlpha(curveColor, 0.52f));

        handleRadiusPx = dp(6.4f);
        setClickable(true);
        setFocusable(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float horizontalInset = dp(CONTENT_HORIZONTAL_INSET_DP);
        float verticalInset = dp(CONTENT_VERTICAL_INSET_DP);

        contentLeft = getPaddingLeft() + horizontalInset;
        contentRight = w - getPaddingRight() - horizontalInset;
        contentTop = getPaddingTop() + verticalInset;
        contentBottom = h - getPaddingBottom() - verticalInset;

        if (contentRight <= contentLeft) {
            contentRight = contentLeft + 1f;
        }
        if (contentBottom <= contentTop) {
            contentBottom = contentTop + 1f;
        }

        float width = contentRight - contentLeft;
        for (int i = 0; i < BAND_COUNT; i++) {
            float fraction = BAND_COUNT == 1 ? 0f : (float) i / (BAND_COUNT - 1);
            bandX[i] = contentLeft + (fraction * width);
        }
        recalculateBandY();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (gridVisible) {
            drawGrid(canvas);
        }
        if (modalBandGuidesEnabled) {
            drawModalBandGuides(canvas);
        }
        drawCurve(canvas);
        if (handlesVisible) {
            drawHandles(canvas);
        }
        if (axisDbLabelsEnabled) {
            drawDbLabels(canvas);
        }
    }

    private void drawGrid(Canvas canvas) {
        for (float db : DB_GUIDE_VALUES) {
            float y = yForDb(db);
            Paint paint = Math.abs(db) < 0.01f ? zeroLinePaint : gridPaint;
            canvas.drawLine(contentLeft, y, contentRight, y, paint);
        }

        for (int i = 0; i < BAND_COUNT; i++) {
            canvas.drawLine(bandX[i], contentTop, bandX[i], contentBottom, gridPaint);
        }

        float cornerRadius = dp(GRID_CORNER_RADIUS_DP);
        canvas.drawRoundRect(contentLeft, contentTop, contentRight, contentBottom, cornerRadius, cornerRadius, gridPaint);
    }

    private void drawCurve(Canvas canvas) {
        curvePath.reset();
        fillPath.reset();

        curvePath.moveTo(bandX[0], bandY[0]);
        for (int i = 0; i < BAND_COUNT - 1; i++) {
            float p1x = bandX[i];
            float p1y = bandY[i];
            float p2x = bandX[i + 1];
            float p2y = bandY[i + 1];

            float p0x;
            float p0y;
            if (i == 0) {
                p0x = p1x - (p2x - p1x);
                p0y = p1y - (p2y - p1y);
            } else {
                p0x = bandX[i - 1];
                p0y = bandY[i - 1];
            }

            float p3x;
            float p3y;
            if (i + 2 >= BAND_COUNT) {
                p3x = p2x + (p2x - p1x);
                p3y = p2y + (p2y - p1y);
            } else {
                p3x = bandX[i + 2];
                p3y = bandY[i + 2];
            }

            float cp1x = p1x + ((p2x - p0x) * CURVE_TENSION / 6f);
            float cp1y = p1y + ((p2y - p0y) * CURVE_TENSION / 6f);
            float cp2x = p2x - ((p3x - p1x) * CURVE_TENSION / 6f);
            float cp2y = p2y - ((p3y - p1y) * CURVE_TENSION / 6f);

            curvePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y);
        }

        if (areaFillEnabled) {
            fillPath.addPath(curvePath);
            fillPath.lineTo(contentRight, contentBottom);
            fillPath.lineTo(contentLeft, contentBottom);
            fillPath.close();
            canvas.drawPath(fillPath, fillPaint);
        }

        canvas.drawPath(curvePath, curvePaint);
    }

    private void drawDbLabels(Canvas canvas) {
        float labelX = contentLeft + dp(4f);
        float ascentAbs = Math.abs(axisLabelPaint.ascent());
        float descentAbs = Math.abs(axisLabelPaint.descent());
        float minBaseline = contentTop + ascentAbs;
        float maxBaseline = contentBottom - descentAbs;

        for (float db : DB_GUIDE_VALUES) {
            float y = yForDb(db);
            float baseline = y + ((ascentAbs - descentAbs) / 2f);
            baseline = Math.max(minBaseline, Math.min(maxBaseline, baseline));
            canvas.drawText(formatDbLabel(db), labelX, baseline, axisLabelPaint);
        }
    }

    private void drawModalBandGuides(Canvas canvas) {
        float top = contentTop + dp(MODAL_GUIDE_TOP_INSET_DP);
        float bottom = contentBottom - dp(MODAL_GUIDE_BOTTOM_INSET_DP);
        for (int i = 0; i < BAND_COUNT; i++) {
            float x = bandX[i];
            canvas.drawLine(x, top, x, bottom, modalGuidePaint);
        }
    }

    private void drawHandles(Canvas canvas) {
        for (int i = 0; i < BAND_COUNT; i++) {
            float radius = i == activeBand ? handleRadiusPx + dp(1.5f) : handleRadiusPx;
            canvas.drawCircle(bandX[i], bandY[i], radius, pointFillPaint);
            canvas.drawCircle(bandX[i], bandY[i], radius, pointStrokePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || !editingEnabled) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeBand = findClosestBandByX(event.getX());
                if (activeBand < 0) {
                    return false;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                updateActiveBandFromTouch(event.getY(), true);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (activeBand >= 0) {
                    updateActiveBandFromTouch(event.getY(), true);
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeBand >= 0) {
                    updateActiveBandFromTouch(event.getY(), true);
                    activeBand = -1;
                    invalidate();
                    return true;
                }
                break;

            default:
                break;
        }

        return super.onTouchEvent(event);
    }

    public void setBandValues(float[] values) {
        if (values == null) {
            return;
        }
        int count = Math.min(values.length, BAND_COUNT);
        for (int i = 0; i < count; i++) {
            bandValues[i] = clampDb(values[i]);
        }
        recalculateBandY();
        invalidate();
    }

    public float getBandValue(int bandIndex) {
        if (bandIndex < 0 || bandIndex >= BAND_COUNT) {
            return 0f;
        }
        return bandValues[bandIndex];
    }

    public float[] getBandValuesSnapshot() {
        float[] snapshot = new float[BAND_COUNT];
        System.arraycopy(bandValues, 0, snapshot, 0, BAND_COUNT);
        return snapshot;
    }

    public void setOnBandChangeListener(@Nullable OnBandChangeListener listener) {
        onBandChangeListener = listener;
    }

    public void setEditingEnabled(boolean enabled) {
        editingEnabled = enabled;
        setClickable(enabled);
        setFocusable(enabled);
    }

    public void setHandlesVisible(boolean visible) {
        handlesVisible = visible;
        invalidate();
    }

    public void setGridVisible(boolean visible) {
        gridVisible = visible;
        invalidate();
    }

    public void setAreaFillEnabled(boolean enabled) {
        areaFillEnabled = enabled;
        invalidate();
    }

    public void setAxisDbLabelsEnabled(boolean enabled) {
        axisDbLabelsEnabled = enabled;
        invalidate();
    }

    public void setModalBandGuidesEnabled(boolean enabled) {
        modalBandGuidesEnabled = enabled;
        invalidate();
    }

    private int findClosestBandByX(float x) {
        int closest = -1;
        float minDistance = Float.MAX_VALUE;

        for (int i = 0; i < BAND_COUNT; i++) {
            float dx = x - bandX[i];
            float distance = Math.abs(dx);
            if (distance < minDistance) {
                minDistance = distance;
                closest = i;
            }
        }

        return closest;
    }

    private void updateActiveBandFromTouch(float touchY, boolean notify) {
        if (activeBand < 0 || activeBand >= BAND_COUNT) {
            return;
        }

        float clampedY = Math.max(contentTop, Math.min(contentBottom, touchY));
        float height = contentBottom - contentTop;
        float normalized = (contentBottom - clampedY) / height;
        float rawDb = MIN_DB + (normalized * (MAX_DB - MIN_DB));
        float quantizedDb = clampDb(rawDb);

        if (Math.abs(quantizedDb - bandValues[activeBand]) < 0.001f) {
            return;
        }

        bandValues[activeBand] = quantizedDb;
        recalculateBandY();

        if (notify && onBandChangeListener != null) {
            onBandChangeListener.onBandChanged(activeBand, quantizedDb);
        }
    }

    private void recalculateBandY() {
        float height = contentBottom - contentTop;
        if (height <= 0f) {
            return;
        }
        for (int i = 0; i < BAND_COUNT; i++) {
            bandY[i] = yForDb(bandValues[i]);
        }
    }

    private float yForDb(float dbValue) {
        float clampedDb = Math.max(MIN_DB, Math.min(MAX_DB, dbValue));
        float height = contentBottom - contentTop;
        float normalized = (clampedDb - MIN_DB) / (MAX_DB - MIN_DB);
        return contentBottom - (normalized * height);
    }

    private float clampDb(float value) {
        float stepped = Math.round(value / STEP_DB) * STEP_DB;
        return Math.max(MIN_DB, Math.min(MAX_DB, stepped));
    }

    private String formatDbLabel(float db) {
        if (Math.abs(db - Math.round(db)) < 0.001f) {
            return String.format("%.0f", db);
        }
        return String.format("%.1f", db);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int adjustAlpha(int color, float alphaFactor) {
        int alpha = Math.round(Color.alpha(color) * alphaFactor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public interface OnBandChangeListener {
        void onBandChanged(int bandIndex, float value);
    }
}