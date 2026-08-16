package de.danoeh.antennapod.ui.statistics

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView

class PieChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        AppCompatImageView(context, attrs, defStyleAttr) {
    companion object {
        private const val ANIMATION_DURATION = 400L
        private const val ANIMATION_START_DELAY = 200L
    }

    private lateinit var drawable: PieChartDrawable
    private var animator: ValueAnimator? = null

    init {
        setup()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setup() {
        drawable = PieChartDrawable()
        setImageDrawable(drawable)
    }

    /**
     * Set of data values to display.
     */
    fun setData(data: PieChartData) {
        drawable.data = data
        if (drawable.animationProgress == 0f) {
            if (animator != null) {
                animator!!.cancel()
            }
            animator = ValueAnimator.ofFloat(0f, 1f)
            animator!!.setDuration(ANIMATION_DURATION)
            animator!!.setStartDelay(ANIMATION_START_DELAY)
            animator!!.setInterpolator(DecelerateInterpolator())
            animator!!.addUpdateListener { animation ->
                drawable.animationProgress = animation.getAnimatedValue() as Float
                drawable.invalidateSelf()
            }
            animator!!.start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = getMeasuredWidth()
        setMeasuredDimension(width, width / 2)
    }

    class PieChartData(values: FloatArray) {
        companion object {
            private val COLOR_VALUES = intArrayOf(0xFF3775E6.toInt(), 0xffe51c23.toInt(), 0xffff9800.toInt(), 0xff259b24.toInt(), 0xff9c27b0.toInt(),
                    0xff0099c6.toInt(), 0xffdd4477.toInt(), 0xff66aa00.toInt(), 0xffb82e2e.toInt(), 0xff316395.toInt(),
                    0xff994499.toInt(), 0xff22aa99.toInt(), 0xffaaaa11.toInt(), 0xff6633cc.toInt(), 0xff0073e6.toInt())
        }

        private val valueSum: Float
        internal val values: FloatArray = values

        init {
            var valueSum = 0f
            for (datum in values) {
                valueSum += datum
            }
            this.valueSum = valueSum
        }

        fun getSum(): Float {
            return valueSum
        }

        fun getPercentageOfItem(index: Int): Float {
            if (valueSum == 0f) {
                return 0f
            }
            return values[index] / valueSum
        }

        fun isLargeEnoughToDisplay(index: Int): Boolean {
            return getPercentageOfItem(index) > 0.04
        }

        fun getColorOfItem(index: Int): Int {
            if (!isLargeEnoughToDisplay(index)) {
                return Color.GRAY
            }
            return COLOR_VALUES[index % COLOR_VALUES.size]
        }
    }

    private class PieChartDrawable : Drawable() {
        companion object {
            private const val PADDING_DEGREES = 3f
        }

        var data: PieChartData? = null
        var animationProgress = 0f
        private val paint: Paint

        init {
            paint = Paint()
            paint.setFlags(Paint.ANTI_ALIAS_FLAG)
            paint.setStyle(Paint.Style.STROKE)
            paint.setStrokeJoin(Paint.Join.ROUND)
            paint.setStrokeCap(Paint.Cap.ROUND)
        }

        override fun draw(canvas: Canvas) {
            val strokeSize = getBounds().height() / 30f
            paint.setStrokeWidth(strokeSize)

            val radius = getBounds().height() - strokeSize
            val center = getBounds().width() / 2f
            val arcBounds = RectF(center - radius, strokeSize, center + radius, strokeSize + radius * 2)

            var startAngle = 180f
            for (i in data!!.values.indices) {
                if (!data!!.isLargeEnoughToDisplay(i)) {
                    break
                }
                paint.setColor(data!!.getColorOfItem(i))
                val padding = if (i == 0) PADDING_DEGREES / 2 else PADDING_DEGREES
                val sweepAngle = (180f - PADDING_DEGREES) * data!!.getPercentageOfItem(i)
                val drawnSweepAngle = (sweepAngle - padding) * animationProgress
                val sliceCenter = startAngle + padding + (sweepAngle - padding) / 2f
                canvas.drawArc(arcBounds, sliceCenter - drawnSweepAngle / 2f, drawnSweepAngle, false, paint)
                startAngle = startAngle + sweepAngle
            }

            paint.setColor(Color.GRAY)
            val sweepAngle = 360 - startAngle - PADDING_DEGREES / 2
            if (sweepAngle > PADDING_DEGREES) {
                val drawnSweepAngle = (sweepAngle - PADDING_DEGREES) * animationProgress
                val sliceCenter = startAngle + PADDING_DEGREES + (sweepAngle - PADDING_DEGREES) / 2f
                canvas.drawArc(arcBounds, sliceCenter - drawnSweepAngle / 2f, drawnSweepAngle, false, paint)
            }
        }

        override fun getOpacity(): Int {
            return PixelFormat.TRANSLUCENT
        }

        override fun setAlpha(alpha: Int) {
        }

        override fun setColorFilter(cf: ColorFilter?) {
        }
    }
}
