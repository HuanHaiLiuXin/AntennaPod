package de.danoeh.antennapod.ui.common

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PathEffect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

import kotlin.math.abs
import kotlin.math.min

class CircularProgressBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        View(context, attrs, defStyleAttr) {
    companion object {
        const val MINIMUM_PERCENTAGE = 0.005f
        const val MAXIMUM_PERCENTAGE = 1 - MINIMUM_PERCENTAGE
        private val DASHED: PathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }

    private val paintBackground = Paint()
    private val paintProgress = Paint()
    private var percentage = 0f
    private var targetPercentage = 0f
    private var isIndeterminate = false
    private var lastTag: Any? = null
    private val bounds = RectF()

    init {
        setup(attrs)
    }

    private fun setup(attrs: AttributeSet?) {
        paintBackground.setAntiAlias(true)
        paintBackground.setStyle(Paint.Style.STROKE)

        paintProgress.setAntiAlias(true)
        paintProgress.setStyle(Paint.Style.STROKE)
        paintProgress.setStrokeCap(Paint.Cap.ROUND)

        val typedArray: TypedArray = getContext().obtainStyledAttributes(attrs, R.styleable.CircularProgressBar)
        val color = typedArray.getColor(R.styleable.CircularProgressBar_foregroundColor, Color.GREEN)
        typedArray.recycle()
        paintProgress.setColor(color)
        paintBackground.setColor(color)
    }

    /**
     * Sets the percentage to be displayed.
     * @param percentage Number from 0 to 1
     * @param tag When the tag is the same as last time calling setPercentage, the update is animated
     */
    fun setPercentage(percentage: Float, tag: Any?) {
        targetPercentage = percentage

        if (tag == null || tag != lastTag) {
            // Do not animate
            this.percentage = percentage
            this.lastTag = tag
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = getHeight() * 0.08f
        paintBackground.setStrokeWidth(getHeight() * 0.03f)
        paintBackground.setPathEffect(if (isIndeterminate) DASHED else null)
        paintProgress.setStrokeWidth(padding)
        bounds.set(padding, padding, getWidth() - padding, getHeight() - padding)
        canvas.drawArc(bounds, -90f, 360f, false, paintBackground)

        if (MINIMUM_PERCENTAGE <= percentage && percentage <= MAXIMUM_PERCENTAGE) {
            canvas.drawArc(bounds, -90f, percentage * 360, false, paintProgress)
        }

        if (abs(percentage - targetPercentage) > MINIMUM_PERCENTAGE) {
            var speed = 0.02f
            if (abs(targetPercentage - percentage) < 0.1 && targetPercentage > percentage) {
                speed = 0.006f
            }
            val delta = min(speed, abs(targetPercentage - percentage))
            val direction = if ((targetPercentage - percentage) > 0) 1f else -1f
            percentage += delta * direction
            invalidate()
        }
    }

    fun setIndeterminate(indeterminate: Boolean) {
        isIndeterminate = indeterminate
    }
}
