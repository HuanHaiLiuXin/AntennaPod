package de.danoeh.antennapod.playback.service.internal

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub
import de.danoeh.antennapod.playback.base.MediaItemAdapter
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter
import de.danoeh.antennapod.storage.database.PodDBAdapter
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class MediaLibrarySessionCallbackTest {
    companion object {
        private const val EPISODE_TITLE = "Episode Title"
    }

    private lateinit var context: Context
    private lateinit var callback: MediaLibrarySessionCallback
    private val session = mock(MediaSession::class.java)
    private val controllerInfo = mock(MediaSession.ControllerInfo::class.java)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        UserPreferences.init(context)
        PlaybackPreferences.init(context)
        PodDBAdapter.init(context)
        PodDBAdapter.deleteDatabase()
        SynchronizationQueue.setInstance(SynchronizationQueueStub())
        callback = MediaLibrarySessionCallback(context)
    }

    @After
    fun tearDown() {
        PodDBAdapter.tearDownTests()
    }

    @Test
    @Throws(Exception::class)
    fun onSetMediaItemsStub() {
        val mediaId = seedEpisode().getId()
        val browseItem = MediaItemAdapter.fromMediaIdStub(mediaId)
        val result = callback.onSetMediaItems(
                session, controllerInfo, Collections.singletonList(browseItem), C.INDEX_UNSET, C.TIME_UNSET)
                .get(5, TimeUnit.SECONDS)
        assertEquals(1, result.mediaItems.size)
        assertEquals(mediaId.toString(), result.mediaItems.get(0).mediaId)
        assertEquals(EPISODE_TITLE, result.mediaItems.get(0).mediaMetadata.title)
    }

    @Test
    @Throws(Exception::class)
    fun onPlaybackResumption() {
        val media = seedEpisode()
        PlaybackPreferences.writeMediaPlaying(media)
        val result = callback.onPlaybackResumption(session, controllerInfo)
                .get(5, TimeUnit.SECONDS)
        assertEquals(1, result.mediaItems.size)
        assertEquals(media.getId().toString(), result.mediaItems.get(0).mediaId)
    }

    @Test
    @Throws(Exception::class)
    fun onAndroidAutoVoiceSearchQuery() {
        val mediaId = seedEpisode().getId()
        var searchItem = MediaItem.EMPTY.buildUpon()
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery(EPISODE_TITLE).build())
                .build()
        var result = callback.onSetMediaItems(session, controllerInfo,
                Collections.singletonList(searchItem), C.INDEX_UNSET, C.TIME_UNSET).get(5, TimeUnit.SECONDS)
        assertEquals(1, result.mediaItems.size)
        assertEquals(mediaId.toString(), result.mediaItems.get(0).mediaId)

        // No match: nothing to play
        searchItem = MediaItem.EMPTY.buildUpon()
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("Unrelated").build())
                .build()
        result = callback.onSetMediaItems(session, controllerInfo,
                Collections.singletonList(searchItem), C.INDEX_UNSET, C.TIME_UNSET).get(5, TimeUnit.SECONDS)
        assertEquals(0, result.mediaItems.size)

        // Empty query ("play something"): fall back to playing something rather than nothing, per
        // Android Auto/Assistant voice action guidelines.
        searchItem = MediaItem.EMPTY.buildUpon()
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("").build())
                .build()
        result = callback.onSetMediaItems(session, controllerInfo,
                Collections.singletonList(searchItem), C.INDEX_UNSET, C.TIME_UNSET).get(5, TimeUnit.SECONDS)
        assertEquals(1, result.mediaItems.size)
        assertEquals(mediaId.toString(), result.mediaItems.get(0).mediaId)
    }

    private fun seedEpisode(): FeedMedia {
        val feed = Feed("url", null, null)
        val items = ArrayList<FeedItem>()
        feed.setItems(items)
        val item = FeedItem()
        item.setItemIdentifier("id")
        item.setTitle(EPISODE_TITLE)
        item.setMedia(FeedMedia(item, "http://example.com", 2, "mime"))
        item.setFeed(feed)
        items.add(item)
        return FeedDatabaseWriter.updateFeed(context, feed, false)!!.getItems()!!.get(0).getMedia()!!
    }
}
