package de.danoeh.antennapod.net.discovery

import java.io.IOException

class FeedUrlNotFoundException : IOException {
    private var artistName: String? = null
    private var trackName: String? = null

    constructor(url: String?, trackName: String?) : super() {
        this.artistName = url
        this.trackName = trackName
    }

    fun getArtistName(): String? {
        return artistName
    }

    fun getTrackName(): String? {
        return trackName
    }

    override val message: String
        get() = "Result does not specify a feed url"
}
