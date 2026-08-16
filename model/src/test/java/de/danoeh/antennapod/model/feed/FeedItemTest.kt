package de.danoeh.antennapod.model.feed

import org.junit.Before
import org.junit.Test

import java.text.SimpleDateFormat
import java.util.Date

import de.danoeh.antennapod.model.feed.FeedItemMother.anyFeedItemWithImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class FeedItemTest {

    private lateinit var original: FeedItem
    private lateinit var changedFeedItem: FeedItem

    @Before
    fun setUp() {
        original = anyFeedItemWithImage()
        changedFeedItem = anyFeedItemWithImage()
    }

    @Test
    fun testUpdateFromOther_feedItemImageDownloadUrlChanged() {
        setNewFeedItemImageDownloadUrl()
        original.updateFromOther(changedFeedItem)
        assertFeedItemImageWasUpdated()
    }

    @Test
    fun testUpdateFromOther_feedItemImageRemoved() {
        feedItemImageRemoved()
        original.updateFromOther(changedFeedItem)
        assertFeedItemImageWasNotUpdated()
    }

    @Test
    fun testUpdateFromOther_feedItemImageAdded() {
        original.setImageUrl(null)
        setNewFeedItemImageDownloadUrl()
        original.updateFromOther(changedFeedItem)
        assertFeedItemImageWasUpdated()
    }

    @Test
    fun testUpdateFromOther_dateChanged() {
        val originalDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1952-03-11 00:00:00")
        val changedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1952-03-11 00:42:42")
        original.setPubDate(originalDate)
        changedFeedItem.setPubDate(changedDate)
        original.updateFromOther(changedFeedItem)
        assertEquals(changedDate.time, original.getPubDate()!!.time)
    }

    /**
     * Test that a played item loses that state after being marked as new.
     */
    @Test
    fun testMarkPlayedItemAsNew_itemNotPlayed() {
        original.setPlayed(true)
        original.setNew()

        assertFalse(original.isPlayed())
    }

    /**
     * Test that a new item loses that state after being marked as played.
     */
    @Test
    fun testMarkNewItemAsPlayed_itemNotNew() {
        original.setNew()
        original.setPlayed(true)

        assertFalse(original.isNew())
    }

    /**
     * Test that a new item loses that state after being marked as not played.
     */
    @Test
    fun testMarkNewItemAsNotPlayed_itemNotNew() {
        original.setNew()
        original.setPlayed(false)

        assertFalse(original.isNew())
    }

    private fun setNewFeedItemImageDownloadUrl() {
        changedFeedItem.setImageUrl("http://example.com/new_picture")
    }

    private fun feedItemImageRemoved() {
        changedFeedItem.setImageUrl(null)
    }

    private fun assertFeedItemImageWasUpdated() {
        assertEquals(original.getImageUrl(), changedFeedItem.getImageUrl())
    }

    private fun assertFeedItemImageWasNotUpdated() {
        assertEquals(anyFeedItemWithImage().getImageUrl(), original.getImageUrl())
    }

    /**
     * If one of `description` or `content:encoded` is null, use the other one.
     */
    @Test
    fun testShownotesNullValues() {
        testShownotes(null, TEXT_LONG)
        testShownotes(TEXT_LONG, null)
    }

    /**
     * If `description` is reasonably longer than `content:encoded`, use `description`.
     */
    @Test
    fun testShownotesLength() {
        testShownotes(TEXT_SHORT, TEXT_LONG)
        testShownotes(TEXT_LONG, TEXT_SHORT)
    }

    /**
     * Checks if the shownotes equal TEXT_LONG, using the given `description` and `content:encoded`.
     *
     * @param description Description of the feed item
     * @param contentEncoded `content:encoded` of the feed item
     */
    private fun testShownotes(description: String?, contentEncoded: String?) {
        val item = FeedItem()
        item.setDescriptionIfLonger(description)
        item.setDescriptionIfLonger(contentEncoded)
        assertEquals(TEXT_LONG, item.getDescription())
    }

    companion object {
        private const val TEXT_LONG = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
        private const val TEXT_SHORT = "Lorem ipsum"
    }
}
