package de.danoeh.antennapod.net.download.service.feed.remote

/**
 * Thrown if a feed has invalid attribute values.
 */
class InvalidFeedException : Exception {
    private val serialVersionUID = 1L

    constructor(message: String) : super(message) {
    }
}
