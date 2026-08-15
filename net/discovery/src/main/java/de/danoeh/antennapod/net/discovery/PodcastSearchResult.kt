package de.danoeh.antennapod.net.discovery

class PodcastSearchResult(
    /**
     * The name of the podcast
     */
    @JvmField val title: String,
    /**
     * URL of the podcast image
     */
    @JvmField val imageUrl: String?,
    /**
     * URL of the podcast feed
     */
    @JvmField val feedUrl: String?,
    /**
     * artistName of the podcast feed
     */
    @JvmField val author: String?
) {
    companion object {
        @JvmStatic
        fun dummy(): PodcastSearchResult {
            return PodcastSearchResult("", "", "", "")
        }
    }
}
