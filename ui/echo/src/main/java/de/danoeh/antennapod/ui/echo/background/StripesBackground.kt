package de.danoeh.antennapod.ui.echo.background

import android.content.Context
import android.graphics.Canvas

class StripesBackground(context: Context) : BaseBackground(context) {
    companion object {
        const val NUM_PARTICLES = 15
    }

    init {
        for (i in 0 until NUM_PARTICLES) {
            particles.add(Particle(2.0 * i / NUM_PARTICLES - 1.0, 0.0, 0.0, 0.0))
        }
    }

    override fun draw(canvas: Canvas) {
        paintParticles.setStrokeWidth(0.05f * getBounds().width())
        super.draw(canvas)
    }

    override fun drawParticle(canvas: Canvas, p: Particle, width: Float, height: Float,
                              innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float) {
        val strokeWidth = 0.05f * width
        val x = (width * p.positionX).toFloat()
        canvas.drawLine(x, -strokeWidth, x + width, height + strokeWidth, paintParticles)
    }

    override fun particleTick(p: Particle, timeSinceLastFrame: Long) {
        p.positionX += 0.00005 * timeSinceLastFrame
        if (p.positionX > 1.0) {
            p.positionX -= 2.0
        }
    }
}
