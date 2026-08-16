package de.danoeh.antennapod.model.feed

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

import java.util.Arrays
import java.util.Collection
import java.util.Collections

import org.junit.Assert.assertEquals

@RunWith(Parameterized::class)
class FeedItemFallbackLinkTest(
    private val msg: String?,
    private val feedLink: String?,
    private val itemLink: String?,
    private val expected: String?
) {

    @Test
    fun testLinkWithFallback() {
        val actual = createFeedItem(feedLink, itemLink).getLinkWithFallback()
        assertEquals(msg, expected, actual)
    }

    companion object {
        private const val FEED_LINK = "http://example.com"
        private const val ITEM_LINK = "http://example.com/feedItem1"

        @JvmStatic
        @Parameters
        fun data(): Collection<Array<Any?>> {
            return Arrays.asList(arrayOf("average", FEED_LINK, ITEM_LINK, ITEM_LINK),
                    arrayOf("null item link - fallback to feed", FEED_LINK, null, FEED_LINK),
                    arrayOf("empty item link - same as null", FEED_LINK, "", FEED_LINK),
                    arrayOf("blank item link - same as null", FEED_LINK, "  ", FEED_LINK),
                    arrayOf("fallback, but feed link is null too", null, null, null),
                    arrayOf("fallback - but empty feed link - same as null", "", null, null),
                    arrayOf("fallback - but blank feed link - same as null", "  ", null, null))
        }

        private fun createFeedItem(feedLink: String?, itemLink: String?): FeedItem {
            val feed = Feed("http://example.com/feed", null)
            feed.setLink(feedLink)
            val feedItem = FeedItem()
            feedItem.setLink(itemLink)
            feedItem.setFeed(feed)
            feed.setItems(Collections.singletonList(feedItem))
            return feedItem
        }
    }
}
