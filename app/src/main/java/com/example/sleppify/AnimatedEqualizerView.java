package com.example.sleppify;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

import java.util.Random;

public class AnimatedEqualizerView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] barHeights = {0.2f, 0.2f, 0.2f};
    private final float[] targetHeights = {0.2f, 0.2f, 0.2f};
    private final Random random = new Random();
    private boolean animating = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int barColor = 0xFFFFFFFF;

    private final Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!animating) return;

            boolean changed = false;
            for (int i = 0; i < 3; i++) {
                if (Math.abs(barHeights[i] - targetHeights[i]) < 0.05f) {
                    targetHeights[i] = 0.3f + random.nextFloat() * 0.7f;
                }
                
                if (barHeights[i] < targetHeights[i]) {
                    barHeights[i] += 0.1f;
                } else {
                    barHeights[i] -= 0.1f;
                }
                changed = true;
            }

            if (changed) {
                invalidate();
            }
            handler.postDelayed(this, 30);
        }
    };

    public AnimatedEqualizerView(Context context) {
        super(context);
        init(null);
    }

    public AnimatedEqualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public AnimatedEqualizerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.AnimatedEqualizerView);
            barColor = a.getColor(R.styleable.AnimatedEqualizerView_barColor, 0xFFFFFFFF);
            a.recycle();
        }
        paint.setColor(barColor);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setBarColor(int color) {
        this.barColor = color;
        paint.setColor(color);
        invalidate();
    }

    public void setAnimating(boolean animating) {
        if (this.animating == animating) return;
        this.animating = animating;
        if (animating) {
            handler.post(animationRunnable);
        } else {
            handler.removeCallbacks(animationRunnable);
            // Reset to low height
            for (int i = 0; i < 3; i++) {
                barHeights[i] = 0.2f;
                targetHeights[i] = 0.2f;
            }
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float barWidth = width / 5f;
        float spacing = width / 10f;

        for (int i = 0; i < 3; i++) {
            float barHeight = height * barHeights[i];
            float left = spacing + i * (barWidth + spacing);
            float top = height - barHeight;
            float right = left + barWidth;
            float bottom = height;
            
            canvas.drawRect(left, top, right, bottom, paint);
        }
    }
}
