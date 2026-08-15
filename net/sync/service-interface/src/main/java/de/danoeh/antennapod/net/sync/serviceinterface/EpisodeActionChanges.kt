package de.danoeh.antennapod.net.sync.serviceinterface


import java.util.List

class EpisodeActionChanges {

    private val episodeActions: List<EpisodeAction>
    private val timestamp: Long

    constructor(episodeActions: List<EpisodeAction>, timestamp: Long) {
        this.episodeActions = episodeActions
        this.timestamp = timestamp
    }

    fun getEpisodeActions(): List<EpisodeAction> {
        return this.episodeActions
    }

    fun getTimestamp(): Long {
        return this.timestamp
    }

    override fun toString(): String {
        return "EpisodeActionGetResponse{" +
                "episodeActions=" + episodeActions +
                ", timestamp=" + timestamp +
                '}'
    }
}
