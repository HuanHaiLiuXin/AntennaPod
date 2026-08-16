package de.danoeh.antennapod.ui.echo.background

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint

class WavesBackground(context: Context) : BaseBackground(context) {
    companion object {
        const val NUM_PARTICLES = 10
    }

    init {
        paintParticles.setStyle(Paint.Style.STROKE)
        for (i in 0 until NUM_PARTICLES) {
            particles.add(Particle(0.0, 0.0, 1.0 * i / NUM_PARTICLES, 0.0))
        }
    }

    override fun draw(canvas: Canvas) {
        paintParticles.setStrokeWidth(0.05f * getBounds().height())
        super.draw(canvas)
    }

    override fun drawParticle(canvas: Canvas, p: Particle, width: Float, height: Float,
                              innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float) {
        canvas.drawCircle(width / 2, 1.1f * height, (p.positionZ * 1.2f * height).toFloat(), paintParticles)
    }

    override fun particleTick(p: Particle, timeSinceLastFrame: Long) {
        p.positionZ += 0.00005 * timeSinceLastFrame
        if (p.positionZ > 1.0) {
            p.positionZ -= 1.0
        }
    }
}
