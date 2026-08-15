package de.danoeh.antennapod.model.playback

class TimerValue {
    private val displayValue: Long // Value shown to user (milliseconds or number of episodes)
    private val millisValue: Long

    constructor(displayValue: Long, millisValue: Long) {
        this.displayValue = displayValue
        this.millisValue = millisValue
    }

    fun getDisplayValue(): Long {
        return displayValue
    }

    fun getMillisValue(): Long {
        return millisValue
    }
}
