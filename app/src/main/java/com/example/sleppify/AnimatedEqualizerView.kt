package com.example.sleppify

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import java.util.Random

class AnimatedEqualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barHeights = floatArrayOf(0.2f, 0.2f, 0.2f)
    private val targetHeights = floatArrayOf(0.2f, 0.2f, 0.2f)
    private val random = Random()
    private var animating = false
    private val handler = Handler(Looper.getMainLooper())
    private var barColor = -0x1

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!animating) return

            var changed = false
            for (i in 0..2) {
                if (Math.abs(barHeights[i] - targetHeights[i]) < 0.05f) {
                    targetHeights[i] = 0.25f + random.nextFloat() * 0.45f
                }

                if (barHeights[i] < targetHeights[i]) {
                    barHeights[i] += 0.04f
                } else {
                    barHeights[i] -= 0.04f
                }
                changed = true
            }

            if (changed) {
                invalidate()
            }
            handler.postDelayed(this, 60)
        }
    }

    init {
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.AnimatedEqualizerView)
            barColor = a.getColor(R.styleable.AnimatedEqualizerView_barColor, -0x1)
            a.recycle()
        }
        paint.color = barColor
        paint.style = Paint.Style.FILL
    }

    fun setBarColor(color: Int) {
        this.barColor = color
        paint.color = color
        invalidate()
    }

    fun setAnimating(animating: Boolean) {
        if (this.animating == animating) return
        this.animating = animating
        if (animating) {
            handler.post(animationRunnable)
        } else {
            handler.removeCallbacks(animationRunnable)
            // Reset to low height
            for (i in 0..2) {
                barHeights[i] = 0.2f
                targetHeights[i] = 0.2f
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val barWidth = width / 5f
        val spacing = width / 10f

        for (i in 0..2) {
            val barHeight = height * barHeights[i]
            val left = spacing + i * (barWidth + spacing)
            val top = height - barHeight
            val right = left + barWidth
            val bottom = height

            canvas.drawRect(left, top, right, bottom, paint)
        }
    }
}
