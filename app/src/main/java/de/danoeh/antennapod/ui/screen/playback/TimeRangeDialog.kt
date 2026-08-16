package de.danoeh.antennapod.ui.screen.playback

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.common.ThemeUtils
import java.util.Locale

class TimeRangeDialog(context: Context, from: Int, to: Int) : MaterialAlertDialogBuilder(context) {
    private val view: TimeRangeView

    init {
        view = TimeRangeView(context, from, to)
        setView(view)
        setPositiveButton(android.R.string.ok, null)
    }

    fun getFrom(): Int {
        return view.from
    }

    fun getTo(): Int {
        return view.to
    }

    internal class TimeRangeView : View {
        internal var from: Int
        internal var to: Int
        private val paintDial = Paint()
        private val paintSelected = Paint()
        private val paintText = Paint()
        private val bounds = RectF()
        internal var touching = 0

        constructor(context: Context) : this(context, 0, 0) { // Used by Android tools
        }

        constructor(context: Context, from: Int, to: Int) : super(context) {
            this.from = from
            this.to = to
            setup()
        }

        private fun setup() {
            paintDial.setAntiAlias(true)
            paintDial.setStyle(Paint.Style.STROKE)
            paintDial.setStrokeCap(Paint.Cap.ROUND)
            paintDial.setColor(ThemeUtils.getColorFromAttr(getContext(), android.R.attr.textColorPrimary))
            paintDial.setAlpha(DIAL_ALPHA)

            paintSelected.setAntiAlias(true)
            paintSelected.setStyle(Paint.Style.STROKE)
            paintSelected.setStrokeCap(Paint.Cap.ROUND)
            paintSelected.setColor(ThemeUtils.getColorFromAttr(getContext(), R.attr.colorAccent))

            paintText.setAntiAlias(true)
            paintText.setStyle(Paint.Style.FILL)
            paintText.setColor(ThemeUtils.getColorFromAttr(getContext(), android.R.attr.textColorPrimary))
            paintText.setTextAlign(Paint.Align.CENTER)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                    && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            } else if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
                super.onMeasure(widthMeasureSpec, widthMeasureSpec)
            } else if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
                super.onMeasure(heightMeasureSpec, heightMeasureSpec)
            } else if (MeasureSpec.getSize(widthMeasureSpec) < MeasureSpec.getSize(heightMeasureSpec)) {
                super.onMeasure(widthMeasureSpec, widthMeasureSpec)
            } else {
                super.onMeasure(heightMeasureSpec, heightMeasureSpec)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val size = getHeight().toFloat() // square
            val padding = size * 0.1f
            paintDial.setStrokeWidth(size * 0.005f)
            bounds.set(padding, padding, size - padding, size - padding)

            paintText.setAlpha(DIAL_ALPHA)
            canvas.drawArc(bounds, 0f, 360f, false, paintDial)
            for (i in 0 until 24) {
                paintDial.setStrokeWidth(size * 0.005f)
                if (i % 6 == 0) {
                    paintDial.setStrokeWidth(size * 0.01f)
                    val textPos = radToPoint(i / 24.0f * 360f, size / 2 - 2.5f * padding)
                    paintText.setTextSize(0.4f * padding)
                    canvas.drawText(i.toString(), textPos.x.toFloat(),
                            textPos.y + (-paintText.descent() - paintText.ascent()) / 2, paintText)
                }
                val outer = radToPoint(i / 24.0f * 360f, size / 2 - 1.7f * padding)
                val inner = radToPoint(i / 24.0f * 360f, size / 2 - 1.9f * padding)
                canvas.drawLine(outer.x.toFloat(), outer.y.toFloat(), inner.x.toFloat(), inner.y.toFloat(), paintDial)
            }
            paintText.setAlpha(255)

            val angleFrom = from.toFloat() / 24 * 360 - 90
            val angleDistance = ((to - from + 24) % 24).toFloat() / 24 * 360
            paintSelected.setStrokeWidth(padding / 6)
            paintSelected.setStyle(Paint.Style.STROKE)
            if (from == to) {
                canvas.drawArc(bounds, 0f, 360f, false, paintSelected)
            } else {
                canvas.drawArc(bounds, angleFrom, angleDistance, false, paintSelected)
            }
            paintSelected.setStyle(Paint.Style.FILL)
            val p1 = radToPoint(angleFrom + 90, size / 2 - padding)
            canvas.drawCircle(p1.x.toFloat(), p1.y.toFloat(), padding / 2, paintSelected)
            val p2 = radToPoint(angleFrom + angleDistance + 90, size / 2 - padding)
            canvas.drawCircle(p2.x.toFloat(), p2.y.toFloat(), padding / 2, paintSelected)

            paintText.setTextSize(0.6f * padding)
            val textBaseY = (size - paintText.descent() - paintText.ascent()) / 2
            if (from == to) {
                val timeRange = getContext().getString(R.string.sleep_timer_always)
                canvas.drawText(timeRange, size / 2, textBaseY, paintText)
            } else if (DateFormat.is24HourFormat(getContext())) {
                val timeRange = String.format(Locale.getDefault(), "%02d:00 - %02d:00", from, to)
                canvas.drawText(timeRange, size / 2, textBaseY, paintText)
            } else {
                val timeFrom = String.format(Locale.getDefault(), "%02d:00 %s", from % 12, if (from >= 12) "PM" else "AM")
                val timeTo = String.format(Locale.getDefault(), "%02d:00 %s", to % 12, if (to >= 12) "PM" else "AM")
                canvas.drawText(timeFrom, size / 2, textBaseY - paintText.getTextSize(), paintText)
                canvas.drawText("-", size / 2, textBaseY, paintText)
                canvas.drawText(timeTo, size / 2, textBaseY + paintText.getTextSize(), paintText)
            }
        }

        protected fun radToPoint(angle: Float, radius: Float): Point {
            return Point((getWidth() / 2.0 + radius.toDouble() * Math.sin(-angle.toDouble() * Math.PI / 180.0 + Math.PI)).toInt(),
                    (getHeight() / 2.0 + radius.toDouble() * Math.cos(-angle.toDouble() * Math.PI / 180.0 + Math.PI)).toInt())
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            getParent().requestDisallowInterceptTouchEvent(true)
            val center = Point(getWidth() / 2, getHeight() / 2)
            val angleRad = Math.atan2((center.y - event.getY()).toDouble(), (center.x - event.getX()).toDouble())
            var angle = (angleRad * (180 / Math.PI)).toFloat()
            angle += 360 + 360 - 90
            angle %= 360

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                val fromDistance = Math.abs(angle - from.toFloat() / 24 * 360)
                val toDistance = Math.abs(angle - to.toFloat() / 24 * 360)
                if (fromDistance < 15 || fromDistance > 360 - 15) {
                    touching = 1
                    return true
                } else if (toDistance < 15 || toDistance > 360 - 15) {
                    touching = 2
                    return true
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                val newTime = (24 * (angle.toDouble() / 360.0)).toInt()
                if (from == to && touching != 0) {
                    // Switch which handle is focused such that selection is the longer arc
                    touching = if ((newTime - to + 24) % 24 < 12) 1 else 2
                }
                if (touching == 1) {
                    from = newTime
                    invalidate()
                    return true
                } else if (touching == 2) {
                    to = newTime
                    invalidate()
                    return true
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (touching != 0) {
                    touching = 0
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        companion object {
            private const val DIAL_ALPHA = 120
        }
    }
}
