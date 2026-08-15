package de.danoeh.antennapod.event.playback

class PlaybackPositionEvent {
    private val position: Int
    private val duration: Int

    constructor(position: Int, duration: Int) {
        this.position = position
        this.duration = duration
    }

    fun getPosition(): Int {
        return position
    }

    fun getDuration(): Int {
        return duration
    }
}
