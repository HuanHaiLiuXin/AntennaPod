package de.danoeh.antennapod.storage.database

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.playback.RemoteMedia
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters

import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Date

import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation
import de.danoeh.antennapod.model.playback.Playable

import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation.AFTER_CURRENTLY_PLAYING
import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation.BACK
import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation.FRONT

class ItemEnqueuePositionCalculatorTest {

    @RunWith(Parameterized::class)
    open class BasicTest {
        companion object {
            @Parameters(name = "{index}: case<{0}>, expected:{1}")
            @JvmStatic
            fun data(): Iterable<Array<Any>> {
                return Arrays.asList(arrayOf(
                        arrayOf("case default, i.e., add to the end",
                                CollectionTestUtil.concat(QUEUE_DEFAULT_IDS, TFI_ID),
                                BACK, QUEUE_DEFAULT),
                        arrayOf("case option enqueue at front",
                                CollectionTestUtil.concat(TFI_ID, QUEUE_DEFAULT_IDS),
                                FRONT, QUEUE_DEFAULT),
                        arrayOf("case empty queue, option default",
                                CollectionTestUtil.list(TFI_ID),
                                BACK, QUEUE_EMPTY),
                        arrayOf("case empty queue, option enqueue at front",
                                CollectionTestUtil.list(TFI_ID),
                                FRONT, QUEUE_EMPTY)))
            }

            @JvmField
            val TFI_ID: Long = 101
        }

        @Parameter
        @JvmField
        var message: String? = null

        @Parameter(1)
        @JvmField
        var idsExpected: List<Long>? = null

        @Parameter(2)
        @JvmField
        var options: EnqueueLocation? = null

        @Parameter(3)
        @JvmField
        var curQueue: List<FeedItem>? = null

        /**
         * Add a FeedItem with ID [TFI_ID] with the setup
         */
        @Test
        fun test() {
            DownloadServiceInterface.setImpl(DownloadServiceInterfaceStub())
            val calculator = ItemEnqueuePositionCalculator(options!!)

            // shallow copy to which the test will add items
            val queue = ArrayList(curQueue)
            val tFI = createFeedItem(TFI_ID)
            doAddToQueueAndAssertResult(message!!,
                    calculator, tFI, queue, getCurrentlyPlaying(),
                    idsExpected!!)
        }

        open fun getCurrentlyPlaying(): Playable? {
            return null
        }
    }

    @RunWith(Parameterized::class)
    class AfterCurrentlyPlayingTest : BasicTest() {
        companion object {
            @Parameters(name = "{index}: case<{0}>, expected:{1}")
            @JvmStatic
            fun data(): Iterable<Array<Any>> {
                return Arrays.asList(arrayOf(
                        arrayOf("case option after currently playing",
                                CollectionTestUtil.list(11L, BasicTest.TFI_ID, 12L, 13L, 14L),
                                AFTER_CURRENTLY_PLAYING, QUEUE_DEFAULT, 11L),
                        arrayOf("case option after currently playing, currently playing in the middle of the queue",
                                CollectionTestUtil.list(11L, 12L, 13L, BasicTest.TFI_ID, 14L),
                                AFTER_CURRENTLY_PLAYING, QUEUE_DEFAULT, 13L),
                        arrayOf("case option after currently playing, currently playing is not in queue",
                                CollectionTestUtil.concat(BasicTest.TFI_ID, QUEUE_DEFAULT_IDS),
                                AFTER_CURRENTLY_PLAYING, QUEUE_DEFAULT, 99L),
                        arrayOf("case option after currently playing, no currentlyPlaying is null",
                                CollectionTestUtil.concat(BasicTest.TFI_ID, QUEUE_DEFAULT_IDS),
                                AFTER_CURRENTLY_PLAYING, QUEUE_DEFAULT, ID_CURRENTLY_PLAYING_NULL),
                        arrayOf("case option after currently playing, currentlyPlaying is not a feedMedia",
                                CollectionTestUtil.concat(BasicTest.TFI_ID, QUEUE_DEFAULT_IDS),
                                AFTER_CURRENTLY_PLAYING, QUEUE_DEFAULT, ID_CURRENTLY_PLAYING_NOT_FEEDMEDIA),
                        arrayOf("case empty queue, option after currently playing",
                                CollectionTestUtil.list(BasicTest.TFI_ID),
                                AFTER_CURRENTLY_PLAYING, QUEUE_EMPTY, ID_CURRENTLY_PLAYING_NULL)))
            }

            private val ID_CURRENTLY_PLAYING_NULL = -1L
            private val ID_CURRENTLY_PLAYING_NOT_FEEDMEDIA = -9999L
        }

        @Parameter(4)
        @JvmField
        var idCurrentlyPlaying: Long = 0

        override fun getCurrentlyPlaying(): Playable? {
            return ItemEnqueuePositionCalculatorTest.getCurrentlyPlaying(idCurrentlyPlaying)
        }
    }

    companion object {
        fun doAddToQueueAndAssertResult(message: String,
                                        calculator: ItemEnqueuePositionCalculator,
                                        itemToAdd: FeedItem,
                                        queue: MutableList<FeedItem>,
                                        currentlyPlaying: Playable?,
                                        idsExpected: List<Long>) {
            val posActual = calculator.calcPosition(queue, currentlyPlaying)
            queue.add(posActual, itemToAdd)
            assertEquals(message, idsExpected.size, queue.size)
            for (i in idsExpected.indices) {
                assertEquals(message, idsExpected[i], queue[i].getId())
            }
        }

        @JvmField
        val QUEUE_EMPTY: List<FeedItem> = Collections.unmodifiableList(Collections.emptyList())

        @JvmField
        val QUEUE_DEFAULT: List<FeedItem> =
                Collections.unmodifiableList(Arrays.asList(
                        createFeedItem(11L), createFeedItem(12L), createFeedItem(13L), createFeedItem(14L)))
        @JvmField
        val QUEUE_DEFAULT_IDS: List<Long> =
                QUEUE_DEFAULT.map { it.getId() }


        fun getCurrentlyPlaying(idCurrentlyPlaying: Long): Playable? {
            if (ID_CURRENTLY_PLAYING_NOT_FEEDMEDIA == idCurrentlyPlaying) {
                return externalMedia()
            }
            if (ID_CURRENTLY_PLAYING_NULL == idCurrentlyPlaying) {
                return null
            }
            return createFeedItem(idCurrentlyPlaying).getMedia()
        }

        fun externalMedia(): Playable {
            return RemoteMedia(createFeedItem(0L))
        }

        @JvmField
        val ID_CURRENTLY_PLAYING_NULL = -1L
        @JvmField
        val ID_CURRENTLY_PLAYING_NOT_FEEDMEDIA = -9999L


        fun createFeedItem(id: Long): FeedItem {
            val feed = Feed(0L, null, "title", "http://example.com", "This is the description",
                    "http://example.com/payment", "Daniel", "en", null, "http://example.com/feed",
                    "http://example.com/image", null, "http://example.com/feed", System.currentTimeMillis())
            val item = FeedItem(id, "Item" + id, "ItemId" + id, "url",
                    Date(), FeedItem.PLAYED, feed)
            val media = FeedMedia(item, "http://download.url.net/" + id, 1234567L, "audio/mpeg")
            media.setId(item.getId())
            item.setMedia(media)
            return item
        }
    }

}
