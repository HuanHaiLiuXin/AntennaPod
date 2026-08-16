package de.danoeh.antennapod.ui.echo.background

import android.content.Context
import android.graphics.Canvas

open class BubbleBackground(context: Context) : BaseBackground(context) {
    companion object {
        const val PARTICLE_SPEED = 0.00002
        const val NUM_PARTICLES = 15
    }

    init {
        for (i in 0 until NUM_PARTICLES) {
            particles.add(Particle(Math.random(), 2.0 * Math.random() - 0.5, // Could already be off-screen
                    0.0, PARTICLE_SPEED + 2 * PARTICLE_SPEED * Math.random()))
        }
    }

    override fun drawParticle(canvas: Canvas, p: Particle, width: Float, height: Float,
                              innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float) {
        canvas.drawCircle((width * p.positionX).toFloat(), (p.positionY * height).toFloat(),
                innerBoxSize / 5, paintParticles)
    }

    override fun particleTick(p: Particle, timeSinceLastFrame: Long) {
        p.positionY -= p.speed * timeSinceLastFrame
        if (p.positionY < -0.5) {
            p.positionX = Math.random()
            p.positionY = 1.5
            p.speed = PARTICLE_SPEED + 2 * PARTICLE_SPEED * Math.random()
        }
    }
}
