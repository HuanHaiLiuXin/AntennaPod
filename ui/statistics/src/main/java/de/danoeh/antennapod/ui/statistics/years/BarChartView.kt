package de.danoeh.antennapod.ui.statistics.years

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.common.ThemeUtils

import kotlin.math.floor

class BarChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        AppCompatImageView(context, attrs, defStyleAttr) {
    companion object {
        private const val ANIMATION_DURATION = 400L
        private const val ANIMATION_START_DELAY = 200L
    }

    private lateinit var drawable: BarChartDrawable
    private var animator: ValueAnimator? = null

    init {
        setup()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setup() {
        drawable = BarChartDrawable()
        setImageDrawable(drawable)
    }

    /**
     * Set of data values to display.
     */
    fun setData(data: List<DBReader.MonthlyStatisticsItem>) {
        drawable.data = data
        drawable.maxValue = 1
        for (item in data) {
            drawable.maxValue = Math.max(drawable.maxValue, item.getTimePlayed())
        }
        if (drawable.animationProgress == 0f) {
            if (animator != null) {
                animator!!.cancel()
            }
            animator = ValueAnimator.ofFloat(0f, 1f)
            animator!!.setDuration(ANIMATION_DURATION)
            animator!!.setStartDelay(ANIMATION_START_DELAY)
            animator!!.setInterpolator(LinearInterpolator())
            animator!!.addUpdateListener { animation ->
                drawable.animationProgress = animation.getAnimatedValue() as Float
                drawable.invalidateSelf()
            }
            animator!!.start()
        }
    }

    private inner class BarChartDrawable : Drawable() {
        private val ONE_HOUR = 3600000L

        var data: List<DBReader.MonthlyStatisticsItem>? = null
        var maxValue = 1L
        var animationProgress = 0f
        private val paintBars: Paint
        private val paintGridLines: Paint
        private val paintGridText: Paint
        private val colors = intArrayOf(0xff3775e6.toInt(), 0xff9c27b0.toInt())

        init {
            paintBars = Paint()
            paintBars.setStyle(Paint.Style.FILL)
            paintBars.setAntiAlias(true)
            paintGridLines = Paint()
            paintGridLines.setStyle(Paint.Style.STROKE)
            paintGridLines.setPathEffect(DashPathEffect(floatArrayOf(10f, 10f), 0f))
            paintGridLines.setColor(ThemeUtils.getColorFromAttr(getContext(), android.R.attr.textColorSecondary))
            paintGridText = Paint()
            paintGridText.setAntiAlias(true)
            paintGridText.setColor(ThemeUtils.getColorFromAttr(getContext(), android.R.attr.textColorSecondary))
        }

        override fun draw(canvas: Canvas) {
            val width = getBounds().width().toFloat()
            val height = getBounds().height().toFloat()
            val barHeight = height * 0.9f
            val textPadding = width * 0.05f
            val stepSize = (width - textPadding) / (data!!.size + 2)
            val textSize = height * 0.06f
            paintGridText.setTextSize(textSize)

            paintBars.setStrokeWidth(height * 0.015f)
            paintBars.setColor(colors[0])
            var colorIndex = 0
            var prevYear = if (data!!.isEmpty()) 0 else data!!.get(0).getYear()
            var monthsInFirstYear = 0
            while (monthsInFirstYear < data!!.size && data!!.get(monthsInFirstYear).getYear() == data!!.get(0).getYear()) {
                monthsInFirstYear++
            }

            for (i in data!!.indices) {
                val x = textPadding + (i + 1) * stepSize
                if (prevYear != data!!.get(i).getYear() || (i == 0 && monthsInFirstYear > 4)) {
                    prevYear = data!!.get(i).getYear()
                    colorIndex++
                    paintBars.setColor(colors[colorIndex % 2])
                    if (i < data!!.size - 4) {
                        canvas.drawText(data!!.get(i).getYear().toString(), x + stepSize,
                                barHeight + (height - barHeight + textSize) / 2, paintGridText)
                    }
                    if (data!!.get(i).getMonth() == 1) {
                        canvas.drawLine(x, height, x, barHeight, paintGridText)
                    }
                }

                val valuePercentage = Math.max(0.005, data!!.get(i).getTimePlayed().toDouble() / maxValue).toFloat()
                val barStart = 0.6f * i / Math.max(1, data!!.size - 1)
                val barProgress = Math.min(1f, Math.max(0f, (animationProgress - barStart) / 0.4f))
                val y = (1 - valuePercentage * barProgress) * barHeight
                canvas.drawRect(x, y, x + stepSize * 0.95f, barHeight, paintBars)
            }

            val maxLine = (floor(maxValue / (10.0 * ONE_HOUR)) * 10 * ONE_HOUR).toFloat()
            var y = (1 - (maxLine / maxValue)) * barHeight
            canvas.drawLine(0f, y, width, y, paintGridLines)
            canvas.drawText(((maxLine / ONE_HOUR).toLong()).toString(), 0f, y + 1.2f * textSize, paintGridText)

            val midLine = maxLine / 2
            y = (1 - (midLine / maxValue)) * barHeight
            canvas.drawLine(0f, y, width, y, paintGridLines)
            canvas.drawText(((midLine / ONE_HOUR).toLong()).toString(), 0f, y + 1.2f * textSize, paintGridText)
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
