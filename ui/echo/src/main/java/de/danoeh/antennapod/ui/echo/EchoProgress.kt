package de.danoeh.antennapod.ui.echo

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

import kotlin.math.floor

class EchoProgress(private val numScreens: Int) : Drawable() {
    private val paint: Paint
    private var progress = 0f

    init {
        paint = Paint()
        paint.setFlags(Paint.ANTI_ALIAS_FLAG)
        paint.setStyle(Paint.Style.STROKE)
        paint.setStrokeJoin(Paint.Join.ROUND)
        paint.setStrokeCap(Paint.Cap.ROUND)
        paint.setColor(0xffffffff.toInt())
    }

    fun setProgress(progress: Float) {
        this.progress = progress
    }

    override fun draw(canvas: Canvas) {
        paint.setStrokeWidth(0.5f * getBounds().height())

        val y = 0.5f * getBounds().height()
        val sectionWidth = 1.0f * getBounds().width() / numScreens
        val sectionPadding = 0.03f * sectionWidth

        for (i in 0 until numScreens) {
            if (i + 1 < progress) {
                paint.setAlpha(255)
            } else {
                paint.setAlpha(100)
            }
            canvas.drawLine(i * sectionWidth + sectionPadding, y, (i + 1) * sectionWidth - sectionPadding, y, paint)
            if (floor(1.0 * i) == floor(progress.toDouble())) {
                paint.setAlpha(255)
                canvas.drawLine(i * sectionWidth + sectionPadding, y, i * sectionWidth + sectionPadding
                        + (progress - i) * (sectionWidth - 2 * sectionPadding), y, paint)
            }
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
