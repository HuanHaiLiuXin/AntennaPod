package de.danoeh.antennapod.event.playback

class SpeedChangedEvent {
    private val newSpeed: Float

    constructor(newSpeed: Float) {
        this.newSpeed = newSpeed
    }

    fun getNewSpeed(): Float {
        return newSpeed
    }
}
