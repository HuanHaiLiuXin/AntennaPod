package de.danoeh.antennapod.event

class PlayerErrorEvent {
    private val message: String?

    constructor(message: String?) {
        this.message = message
    }

    fun getMessage(): String? {
        return message
    }
}
