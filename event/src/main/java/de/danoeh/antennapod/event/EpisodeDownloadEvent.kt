package de.danoeh.antennapod.event

import de.danoeh.antennapod.model.download.DownloadStatus
import de.danoeh.antennapod.model.feed.FeedItem

class EpisodeDownloadEvent(private val map: Map<String, DownloadStatus>) {

    fun getUrls(): Set<String> {
        return map.keys
    }

    companion object {
        @JvmStatic
        fun indexOfItemWithDownloadUrl(items: List<FeedItem>, downloadUrl: String?): Int {
            for (i in items.indices) {
                val item = items[i]
                if (item != null && item.getMedia() != null
                        && item.getMedia()!!.getDownloadUrl()!!.equals(downloadUrl)) {
                    return i
                }
            }
            return -1
        }
    }
}
