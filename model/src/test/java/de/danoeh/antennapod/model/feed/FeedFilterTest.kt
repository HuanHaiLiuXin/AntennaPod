package de.danoeh.antennapod.model.feed

import org.junit.Test

import java.util.concurrent.TimeUnit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class FeedFilterTest {

    @Test
    fun testNullFilter() {
        val filter = FeedFilter()
        val item = FeedItem()
        item.setTitle("Hello world")

        assertFalse(filter.excludeOnly())
        assertFalse(filter.includeOnly())
        assertEquals("", filter.getExcludeFilterRaw())
        assertEquals("", filter.getIncludeFilterRaw())
        assertTrue(filter.shouldAutoDownload(item))
    }

    @Test
    fun testBasicIncludeFilter() {
        val includeFilter = "Hello"
        val filter = FeedFilter(includeFilter, "")
        val item = FeedItem()
        item.setTitle("Hello world")

        val item2 = FeedItem()
        item2.setTitle("Don't include me")

        assertFalse(filter.excludeOnly())
        assertTrue(filter.includeOnly())
        assertEquals("", filter.getExcludeFilterRaw())
        assertEquals(includeFilter, filter.getIncludeFilterRaw())
        assertTrue(filter.shouldAutoDownload(item))
        assertFalse(filter.shouldAutoDownload(item2))
    }

    @Test
    fun testBasicExcludeFilter() {
        val excludeFilter = "Hello"
        val filter = FeedFilter("", excludeFilter)
        val item = FeedItem()
        item.setTitle("Hello world")

        val item2 = FeedItem()
        item2.setTitle("Item2")

        assertTrue(filter.excludeOnly())
        assertFalse(filter.includeOnly())
        assertEquals(excludeFilter, filter.getExcludeFilterRaw())
        assertEquals("", filter.getIncludeFilterRaw())
        assertFalse(filter.shouldAutoDownload(item))
        assertTrue(filter.shouldAutoDownload(item2))
    }

    @Test
    fun testComplexIncludeFilter() {
        val includeFilter = "Hello \n\"Two words\""
        val filter = FeedFilter(includeFilter, "")
        val item = FeedItem()
        item.setTitle("hello world")

        val item2 = FeedItem()
        item2.setTitle("Two three words")

        val item3 = FeedItem()
        item3.setTitle("One two words")

        assertFalse(filter.excludeOnly())
        assertTrue(filter.includeOnly())
        assertEquals("", filter.getExcludeFilterRaw())
        assertEquals(includeFilter, filter.getIncludeFilterRaw())
        assertTrue(filter.shouldAutoDownload(item))
        assertFalse(filter.shouldAutoDownload(item2))
        assertTrue(filter.shouldAutoDownload(item3))
    }

    @Test
    fun testComplexExcludeFilter() {
        val excludeFilter = "Hello \"Two words\""
        val filter = FeedFilter("", excludeFilter)
        val item = FeedItem()
        item.setTitle("hello world")

        val item2 = FeedItem()
        item2.setTitle("One three words")

        val item3 = FeedItem()
        item3.setTitle("One two words")

        assertTrue(filter.excludeOnly())
        assertFalse(filter.includeOnly())
        assertEquals(excludeFilter, filter.getExcludeFilterRaw())
        assertEquals("", filter.getIncludeFilterRaw())
        assertFalse(filter.shouldAutoDownload(item))
        assertTrue(filter.shouldAutoDownload(item2))
        assertFalse(filter.shouldAutoDownload(item3))
    }

    @Test
    fun testComboFilter() {
        val includeFilter = "Hello world"
        val excludeFilter = "dislike"
        val filter = FeedFilter(includeFilter, excludeFilter)

        val download = FeedItem()
        download.setTitle("Hello everyone!")
        // because, while it has words from the include filter it also has exclude words
        val doNotDownload = FeedItem()
        doNotDownload.setTitle("I dislike the world")
        // because it has no words from the include filter
        val doNotDownload2 = FeedItem()
        doNotDownload2.setTitle("no words to include")

        assertTrue(filter.hasExcludeFilter())
        assertTrue(filter.hasIncludeFilter())
        assertTrue(filter.shouldAutoDownload(download))
        assertFalse(filter.shouldAutoDownload(doNotDownload))
        assertFalse(filter.shouldAutoDownload(doNotDownload2))
    }

    @Test
    fun testMinimalDurationFilter() {
        val download = FeedItem()
        download.setTitle("Hello friend!")
        val downloadMedia = FeedMediaMother.anyFeedMedia()
        downloadMedia.setDuration(TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES).toInt())
        download.setMedia(downloadMedia)
        // because duration of the media in unknown
        val download2 = FeedItem()
        download2.setTitle("Hello friend!")
        val unknownDurationMedia = FeedMediaMother.anyFeedMedia()
        download2.setMedia(unknownDurationMedia)
        // because it is not long enough
        val doNotDownload = FeedItem()
        doNotDownload.setTitle("Hello friend!")
        val doNotDownloadMedia = FeedMediaMother.anyFeedMedia()
        doNotDownloadMedia.setDuration(TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES).toInt())
        doNotDownload.setMedia(doNotDownloadMedia)

        val minimalDurationFilter = 3 * 60
        val filter = FeedFilter("", "", minimalDurationFilter)

        assertTrue(filter.hasMinimalDurationFilter())
        assertTrue(filter.shouldAutoDownload(download))
        assertFalse(filter.shouldAutoDownload(doNotDownload))
        assertTrue(filter.shouldAutoDownload(download2))
    }

}
