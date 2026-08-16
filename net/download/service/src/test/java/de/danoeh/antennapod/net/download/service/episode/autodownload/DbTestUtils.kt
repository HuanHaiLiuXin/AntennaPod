package de.danoeh.antennapod.net.download.service.episode.autodownload

import java.util.ArrayList
import java.util.Date

import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.database.PodDBAdapter

import org.junit.Assert.assertTrue

/**
 * Utility methods for DB* tests.
 */
abstract class DbTestUtils {

    companion object {
        /**
         * Use this method when tests don't involve chapters.
         */
        @JvmStatic
        fun saveFeedlist(numFeeds: Int, numItems: Int, withMedia: Boolean): List<Feed> {
            return saveFeedlist(numFeeds, numItems, withMedia, false, 0)
        }

        /**
         * Use this method when tests involve chapters.
         */
        @JvmStatic
        fun saveFeedlist(numFeeds: Int, numItems: Int, withMedia: Boolean,
                         withChapters: Boolean, numChapters: Int): List<Feed> {
            if (numFeeds <= 0) {
                throw IllegalArgumentException("numFeeds<=0")
            }
            if (numItems < 0) {
                throw IllegalArgumentException("numItems<0")
            }

            val feeds = ArrayList<Feed>()
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            for (i in 0 until numFeeds) {
                val f = Feed(0L, null, "feed " + i, "link" + i, "descr", null, null,
                        null, null, "id" + i, null, null, "url" + i, System.currentTimeMillis())
                f.setItems(ArrayList())
                var itemDate = Date().getTime()
                for (j in 0 until numItems) {
                    val item = FeedItem(0L, "item " + j, "id" + j, "link" + j, Date(itemDate),
                            FeedItem.PLAYED, f, withChapters)
                    itemDate += 24L * 60 * 60 * 1000
                    if (withMedia) {
                        val media = FeedMedia(item, "url" + j, 1, "audio/mp3")
                        item.setMedia(media)
                    }
                    if (withChapters) {
                        val chapters = ArrayList<Chapter>()
                        item.setChapters(chapters)
                        for (k in 0 until numChapters) {
                            chapters.add(Chapter(k.toLong(), "item " + j + " chapter " + k,
                                    "http://example.com", "http://example.com/image.png"))
                        }
                    }
                    (f.getItems() as ArrayList<FeedItem>).add(item)
                }
                adapter.setCompleteFeed(f)
                assertTrue(f.getId() != 0L)
                for (item in f.getItems()!!) {
                    assertTrue(item.getId() != 0L)
                }
                feeds.add(f)
            }
            adapter.close()

            return feeds
        }
    }
}
