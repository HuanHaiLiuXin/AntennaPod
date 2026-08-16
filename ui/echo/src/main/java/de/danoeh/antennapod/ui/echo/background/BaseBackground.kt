package de.danoeh.antennapod.ui.echo.background

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

import java.util.ArrayList
import de.danoeh.antennapod.ui.echo.R
import kotlin.math.abs
import kotlin.math.min

abstract class BaseBackground(context: Context) : Drawable() {
    private val paintBackground: Paint
    protected val paintParticles: Paint
    protected val particles: ArrayList<Particle> = ArrayList()
    private val colorBackgroundFrom: Int
    private val colorBackgroundTo: Int
    private var lastFrame = 0L

    init {
        colorBackgroundFrom = ContextCompat.getColor(context, R.color.gradient_000)
        colorBackgroundTo = ContextCompat.getColor(context, R.color.gradient_100)
        paintBackground = Paint()
        paintParticles = Paint()
        paintParticles.setColor(0xffffffff.toInt())
        paintParticles.setFlags(Paint.ANTI_ALIAS_FLAG)
        paintParticles.setStyle(Paint.Style.FILL)
        paintParticles.setAlpha(25)
    }

    override fun draw(canvas: Canvas) {
        val width = getBounds().width().toFloat()
        val height = getBounds().height().toFloat()
        paintBackground.setShader(LinearGradient(0f, 0f, 0f, height,
                colorBackgroundFrom, colorBackgroundTo, Shader.TileMode.CLAMP))
        canvas.drawRect(0f, 0f, width, height, paintBackground)

        var timeSinceLastFrame = System.currentTimeMillis() - lastFrame
        lastFrame = System.currentTimeMillis()
        if (timeSinceLastFrame > 500) {
            timeSinceLastFrame = 0
        }
        val innerBoxSize = if (abs(width - height) < 0.001f) // Square share version
            0.9f * width else 0.9f * min(width, 0.7f * height)
        val innerBoxX = (width - innerBoxSize) / 2
        val innerBoxY = (height - innerBoxSize) / 2

        for (p in particles) {
            drawParticle(canvas, p, width, height, innerBoxX, innerBoxY, innerBoxSize)
            particleTick(p, timeSinceLastFrame)
        }

        drawInner(canvas, innerBoxX, innerBoxY, innerBoxSize)
    }

    protected open fun drawInner(canvas: Canvas, innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float) {
    }

    protected abstract fun particleTick(p: Particle, timeSinceLastFrame: Long)

    protected abstract fun drawParticle(canvas: Canvas, p: Particle, width: Float, height: Float,
                                        innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float)

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setAlpha(alpha: Int) {
    }

    override fun setColorFilter(cf: ColorFilter?) {
    }

    protected class Particle(var positionX: Double, var positionY: Double,
                             var positionZ: Double, var speed: Double)
}
