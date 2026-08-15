package de.danoeh.antennapod.model.feed

import org.junit.Before
import org.junit.Test

import de.danoeh.antennapod.model.feed.FeedMediaMother.anyFeedMedia
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class FeedMediaTest {

    private lateinit var media: FeedMedia

    @Before
    fun setUp() {
        media = anyFeedMedia()
    }

    /**
     * Downloading a media from a not new and not played item should not change the item state.
     */
    @Test
    fun testDownloadMediaOfNotNewAndNotPlayedItem_unchangedItemState() {
        val item = mock(FeedItem::class.java)
        `when`(item.isNew()).thenReturn(false)
        `when`(item.isPlayed()).thenReturn(false)

        media.setItem(item)
        media.setDownloaded(true, System.currentTimeMillis())

        verify(item, never()).setNew()
        verify(item, never()).setPlayed(true)
        verify(item, never()).setPlayed(false)
    }

    /**
     * Downloading a media from a played item (thus not new) should not change the item state.
     */
    @Test
    fun testDownloadMediaOfPlayedItem_unchangedItemState() {
        val item = mock(FeedItem::class.java)
        `when`(item.isNew()).thenReturn(false)
        `when`(item.isPlayed()).thenReturn(true)

        media.setItem(item)
        media.setDownloaded(true, System.currentTimeMillis())

        verify(item, never()).setNew()
        verify(item, never()).setPlayed(true)
        verify(item, never()).setPlayed(false)
    }

    /**
     * Downloading a media from a new item (thus not played) should change the item to not played.
     */
    @Test
    fun testDownloadMediaOfNewItem_changedToNotPlayedItem() {
        val item = mock(FeedItem::class.java)
        `when`(item.isNew()).thenReturn(true)
        `when`(item.isPlayed()).thenReturn(false)

        media.setItem(item)
        media.setDownloaded(true, System.currentTimeMillis())

        verify(item).setPlayed(false)
        verify(item, never()).setNew()
        verify(item, never()).setPlayed(true)
    }

}
