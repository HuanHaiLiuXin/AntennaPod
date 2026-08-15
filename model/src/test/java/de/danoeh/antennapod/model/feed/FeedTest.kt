package de.danoeh.antennapod.model.feed

import org.junit.Before
import org.junit.Test

import de.danoeh.antennapod.model.feed.FeedMother.anyFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows

class FeedTest {

    private lateinit var original: Feed
    private lateinit var changedFeed: Feed

    @Before
    fun setUp() {
        original = anyFeed()
        changedFeed = anyFeed()
    }

    @Test
    fun testUpdateFromOther_feedImageDownloadUrlChanged() {
        changedFeed.setImageUrl("http://example.com/new_picture")
        original.updateFromOther(changedFeed)
        assertEquals(original.getImageUrl(), changedFeed.getImageUrl())
    }

    @Test
    fun testUpdateFromOther_feedImageRemoved() {
        changedFeed.setImageUrl(null)
        original.updateFromOther(changedFeed)
        assertEquals(anyFeed().getImageUrl(), original.getImageUrl())
    }

    @Test
    fun testUpdateFromOther_feedImageAdded() {
        original.setImageUrl(null)
        changedFeed.setImageUrl("http://example.com/new_picture")
        original.updateFromOther(changedFeed)
        assertEquals(original.getImageUrl(), changedFeed.getImageUrl())
    }

    @Test
    fun testSetSortOrder_OnlyIntraFeedSortAllowed() {
        for (sortOrder in SortOrder.values()) {
            if (sortOrder.scope == SortOrder.Scope.INTRA_FEED) {
                original.setSortOrder(sortOrder) // should be okay
            } else {
                assertThrows(IllegalArgumentException::class.java) { original.setSortOrder(sortOrder) }
            }
        }
    }

    @Test
    fun testSetSortOrder_NullAllowed() {
        original.setSortOrder(null) // should be okay
    }
}
