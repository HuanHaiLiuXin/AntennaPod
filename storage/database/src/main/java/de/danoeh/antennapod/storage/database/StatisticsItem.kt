package de.danoeh.antennapod.storage.database

import de.danoeh.antennapod.model.feed.Feed

class StatisticsItem {
    @JvmField
    val feed: Feed
    @JvmField
    val time: Long

    /**
     * Respects speed, listening twice, ...
     */
    @JvmField
    val timePlayed: Long

    /**
     * Number of episodes.
     */
    @JvmField
    val episodes: Long

    /**
     * Episodes that are actually played.
     */
    @JvmField
    val episodesStarted: Long

    /**
     * Simply sums up the size of download podcasts.
     */
    @JvmField
    val totalDownloadSize: Long

    /**
     * Stores the number of episodes downloaded.
     */
    @JvmField
    val episodesDownloadCount: Long

    @JvmField
    val hasRecentUnplayed: Boolean

    constructor(feed: Feed, time: Long, timePlayed: Long,
                episodes: Long, episodesStarted: Long,
                totalDownloadSize: Long, episodesDownloadCount: Long, hasRecentUnplayed: Boolean) {
        this.feed = feed
        this.time = time
        this.timePlayed = timePlayed
        this.episodes = episodes
        this.episodesStarted = episodesStarted
        this.totalDownloadSize = totalDownloadSize
        this.episodesDownloadCount = episodesDownloadCount
        this.hasRecentUnplayed = hasRecentUnplayed
    }
}
