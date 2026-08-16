package de.danoeh.antennapod.playback.service.internal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.core.util.Pair
import androidx.media.utils.MediaConstants
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.playback.base.MediaItemAdapter
import de.danoeh.antennapod.playback.base.RewindAfterPauseUtils
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter
import de.danoeh.antennapod.playback.service.R
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent
import org.greenrobot.eventbus.EventBus
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

import java.util.Collections

open class MediaLibrarySessionCallback : MediaLibraryService.MediaLibrarySession.Callback {
    companion object {
        private const val TAG = "M3SessionCallback"
        private const val MEDIA_ID_ROOT = "root"
        private const val MEDIA_ID_QUEUE = "queue"
        private const val MEDIA_ID_DOWNLOADS = "downloads"
        private const val MEDIA_ID_EPISODES = "episodes"
        private const val MEDIA_ID_SUBSCRIPTIONS = "subscriptions"
        private const val MEDIA_ID_CONTINUE_LISTENING = "continue_listening"
        private const val CONTINUE_LISTENING_NUM_EPISODES = 8
        private val BROWSABLE_MEDIA_IDS: ImmutableList<String> = ImmutableList.of(
                MEDIA_ID_ROOT, MEDIA_ID_QUEUE, MEDIA_ID_DOWNLOADS, MEDIA_ID_EPISODES,
                MEDIA_ID_SUBSCRIPTIONS, MEDIA_ID_CONTINUE_LISTENING)

        @JvmStatic
        protected val SESSION_COMMAND_REWIND
                = SessionCommand("rewind", Bundle.EMPTY)
        @JvmStatic
        protected val SESSION_COMMAND_FAST_FORWARD
                = SessionCommand("fast_forward", Bundle.EMPTY)
        @JvmStatic
        protected val SESSION_COMMAND_PLAYBACK_SPEED
                = SessionCommand("playback_speed", Bundle.EMPTY)
        @JvmStatic
        protected val SESSION_COMMAND_NEXT_CHAPTER
                = SessionCommand("next_chapter", Bundle.EMPTY)
        @JvmField
        val SESSION_COMMAND_SKIP_SILENCE
                = SessionCommand("skip_silence", Bundle.EMPTY)
        @JvmField
        val SESSION_COMMAND_SET_SLEEP_TIMER
                = SessionCommand("set_sleep_timer", Bundle.EMPTY)
        @JvmField
        val SESSION_COMMAND_DISABLE_SLEEP_TIMER
                = SessionCommand("disable_sleep_timer", Bundle.EMPTY)
        @JvmField
        val SESSION_COMMAND_EXTEND_SLEEP_TIMER
                = SessionCommand("extend_sleep_timer", Bundle.EMPTY)

        private const val EXTRA_VALUE = "value"

        @JvmStatic
        fun createBundle(value: Boolean): Bundle {
            val args = Bundle()
            args.putBoolean(EXTRA_VALUE, value)
            return args
        }

        @JvmStatic
        fun createBundle(value: Long): Bundle {
            val args = Bundle()
            args.putLong(EXTRA_VALUE, value)
            return args
        }

        @JvmStatic
        fun getBoolean(args: Bundle?, defaultValue: Boolean): Boolean {
            return if (args != null) args.getBoolean(EXTRA_VALUE, defaultValue) else defaultValue
        }

        @JvmStatic
        fun getLong(args: Bundle?, defaultValue: Long): Long {
            return if (args != null) args.getLong(EXTRA_VALUE, defaultValue) else defaultValue
        }
    }

    private val context: Context

    constructor(context: Context) {
        this.context = context
    }

    @UnstableApi
    override fun onConnect(session: MediaSession,
                           controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SESSION_COMMAND_REWIND)
                .add(SESSION_COMMAND_FAST_FORWARD)
                .add(SESSION_COMMAND_PLAYBACK_SPEED)
                .add(SESSION_COMMAND_NEXT_CHAPTER)
                .add(SESSION_COMMAND_SKIP_SILENCE)
                .add(SESSION_COMMAND_SET_SLEEP_TIMER)
                .add(SESSION_COMMAND_DISABLE_SLEEP_TIMER)
                .add(SESSION_COMMAND_EXTEND_SLEEP_TIMER)
                .build()
        val playerCommands = Player.Commands.Builder()
                .addAllCommands()
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setMediaButtonPreferences(buildCustomLayout())
                .setAvailablePlayerCommands(playerCommands)
                .build()
    }

    @UnstableApi
    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        session.setMediaButtonPreferences(buildCustomLayout())
    }

    @UnstableApi
    fun refreshNotification(session: MediaLibraryService.MediaLibrarySession) {
        session.setMediaButtonPreferences(buildCustomLayout())
    }

    @UnstableApi
    private fun buildCustomLayout(): ImmutableList<CommandButton> {
        val buttons = ImmutableList.builder<CommandButton>()

        buttons.add(CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(SESSION_COMMAND_REWIND)
                .setDisplayName(context.getString(R.string.rewind_label))
                .build())

        buttons.add(CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
                .setSessionCommand(SESSION_COMMAND_FAST_FORWARD)
                .setDisplayName(context.getString(R.string.fast_forward_label))
                .build())

        if (UserPreferences.showPlaybackSpeedOnFullNotification()) {
            buttons.add(CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setSessionCommand(SESSION_COMMAND_PLAYBACK_SPEED)
                    .setCustomIconResId(R.drawable.ic_notification_playback_speed)
                    .setDisplayName(context.getString(R.string.playback_speed))
                    .build())
        }

        if (UserPreferences.showNextChapterOnFullNotification()) {
            buttons.add(CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setSessionCommand(SESSION_COMMAND_NEXT_CHAPTER)
                    .setCustomIconResId(R.drawable.ic_notification_next_chapter)
                    .setDisplayName(context.getString(R.string.next_chapter))
                    .build())
        }

        if (UserPreferences.showSkipOnFullNotification()) {
            buttons.add(CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setSlots(CommandButton.SLOT_OVERFLOW)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .setDisplayName(context.getString(R.string.skip_episode_label))
                    .build())
        }

        if (UserPreferences.showSleepTimerOnFullNotification()) {
            val sleepEvent = EventBus.getDefault().getStickyEvent(SleepTimerUpdatedEvent::class.java)
            val sleepTimerActive = sleepEvent != null && !sleepEvent.isCancelled() && !sleepEvent.isOver()
            val sleepIcon = if (sleepTimerActive) R.drawable.ic_notification_sleep_off else R.drawable.ic_notification_sleep
            val sleepCommand = if (sleepTimerActive)
                SESSION_COMMAND_DISABLE_SLEEP_TIMER else SESSION_COMMAND_SET_SLEEP_TIMER
            buttons.add(CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setSessionCommand(sleepCommand)
                    .setCustomIconResId(sleepIcon)
                    .setDisplayName(context.getString(R.string.sleep_timer_label))
                    .build())
        }

        return buttons.build()
    }

    @UnstableApi
    override fun onMediaButtonEvent(session: MediaSession,
                                    controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN
                && keyEvent.getRepeatCount() == 0) {
            val fromWidget = MediaButtonStarter.MEDIA_BUTTON_SOURCE_WIDGET ==
                    intent.getStringExtra(MediaButtonStarter.EXTRA_MEDIA_BUTTON_SOURCE)
            val keyCode = keyEvent.getKeyCode()
            if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                session.getPlayer().seekForward()
                return true
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                session.getPlayer().seekBack()
                return true
            } else if (fromWidget && keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                session.getPlayer().seekToNextMediaItem()
                return true
            } else if (!fromWidget && keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                // Media3 translates HEADSETHOOK double-tap to MEDIA_NEXT.
                // Instead of skipping to the next episode, do a fast-forward.
                session.getPlayer().seekForward()
                return true
            } else if (!fromWidget && keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                // Media3 translates HEADSETHOOK triple-tap to MEDIA_PREVIOUS.
                // Instead of going to the previous episode, do a rewind.
                session.getPlayer().seekBack()
                return true
            }
        }
        return false
    }

    @UnstableApi
    override fun onCustomCommand(session: MediaSession,
                                 controller: MediaSession.ControllerInfo,
                                 customCommand: SessionCommand,
                                 args: Bundle): ListenableFuture<SessionResult> {
        if (customCommand.customAction == SESSION_COMMAND_REWIND.customAction) {
            session.getPlayer().seekBack()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        } else if (customCommand.customAction == SESSION_COMMAND_FAST_FORWARD.customAction) {
            session.getPlayer().seekForward()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
    }

    @UnstableApi
    override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        if (mediaItems.isEmpty()) {
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(
                    mediaItems, index, startPositionMs))
        }
        val searchQuery = mediaItems[index].requestMetadata.searchQuery
        if (searchQuery != null) {
            if ("" == searchQuery) {
                return onPlaybackResumption(mediaSession, controller) // "Play something" voice action
            }
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            Maybe.fromCallable<FeedMedia> {
                val results = DBReader.searchFeedItems(0, searchQuery, FeedItemFilter.unfiltered())
                for (result in results) {
                    if (result.getMedia() != null) {
                        return@fromCallable result.getMedia()!!
                    }
                }
                null
            }
                    .subscribeOn(Schedulers.io())
                    .subscribe({ media ->
                        var startPosition = SkipUtils.skipIntroIfNecessary(context, media)
                        startPosition = RewindAfterPauseUtils.calculatePositionWithRewind(
                                startPosition.toInt(), media.getLastPlayedTimeStatistics()).toLong()
                        future.set(MediaSession.MediaItemsWithStartPosition(
                                Collections.singletonList(MediaItemAdapter.fromPlayable(context, media, false)),
                                0, startPosition))
                    }, { error ->
                        Log.e(TAG, "Voice search failed", error)
                        future.set(MediaSession.MediaItemsWithStartPosition(
                                Collections.emptyList(), index, startPositionMs))
                    },
                        { future.set(MediaSession.MediaItemsWithStartPosition(
                                Collections.emptyList(), index, startPositionMs)) })
            return future
        }
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        Single.fromCallable<Pair<List<MediaItem>, FeedMedia>>(
                {
                    val updatedItems = onAddMediaItems(mediaSession, controller, mediaItems).get()
                    val mediaId = java.lang.Long.parseLong(updatedItems[index].mediaId)
                    val mediaDetails = DBReader.getFeedMedia(mediaId)
                    return@fromCallable Pair(updatedItems, mediaDetails!!)
                })
                .subscribeOn(Schedulers.io())
                .subscribe({ result ->
                    var startPosition = SkipUtils.skipIntroIfNecessary(context, result.second)
                    startPosition = RewindAfterPauseUtils.calculatePositionWithRewind(
                            startPosition.toInt(), result.second.getLastPlayedTimeStatistics()).toLong()
                    future.set(MediaSession.MediaItemsWithStartPosition(result.first, index, startPosition))
                }, { error ->
                    Log.e(TAG, "Failed to load media", error)
                    future.set(MediaSession.MediaItemsWithStartPosition(
                            Collections.emptyList(), index, startPositionMs))
                })
        return future
    }

    override fun onAddMediaItems(mediaSession: MediaSession,
                                 controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {

        if (mediaItems.isEmpty()) {
            return Futures.immediateFuture(Collections.emptyList())
        }

        val future = SettableFuture.create<List<MediaItem>>()
        Single.fromCallable { enrichMediaItems(mediaItems) }
                .subscribeOn(Schedulers.io())
                .subscribe(
                        { items -> future.set(if (items.isEmpty()) Collections.emptyList() else items) },
                        { error ->
                            Log.e(TAG, "Failed to load media items", error)
                            future.set(Collections.emptyList())
                        }
                )
        return future
    }

    @UnstableApi
    override fun onPlaybackResumption(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        Single.fromCallable<FeedMedia> {
            var media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
            // If there is no media to resume, media3 crashes. So instead of crashing, just play something random.
            if (media == null) {
                val recentQueue = DBReader.getPausedQueue(1)
                if (!recentQueue.isEmpty()) {
                    media = recentQueue[0].getMedia()
                }
            }
            if (media == null) {
                val items = DBReader.getEpisodes(0, 1, FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD)
                if (!items.isEmpty()) {
                    media = items[0].getMedia()
                }
            }
            media!!
        }
                .subscribeOn(Schedulers.io())
                .subscribe(
                        { media ->
                            var startPosition = SkipUtils.skipIntroIfNecessary(context, media)
                            startPosition = RewindAfterPauseUtils.calculatePositionWithRewind(
                                    startPosition.toInt(), media.getLastPlayedTimeStatistics()).toLong()
                            val result =
                                    MediaSession.MediaItemsWithStartPosition(
                                            Collections.singletonList(
                                                    MediaItemAdapter.fromPlayable(context, media, false)),
                                            0, startPosition)
                            future.set(result)
                        },
                        { future.setException(it) }
                )
        return future
    }

    @UnstableApi
    override fun onGetLibraryRoot(
            session: MediaLibraryService.MediaLibrarySession, browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        val rootExtras = Bundle()
        rootExtras.putBoolean(MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true)
        rootExtras.putBoolean(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, true)
        val libraryParams = MediaLibraryService.LibraryParams.Builder()
                .setExtras(rootExtras).build()
        if ("com.google.android.googlequicksearchbox" == browser.getPackageName()) {
            // Android Auto "for you" screen
            return Futures.immediateFuture(LibraryResult.ofItem(
                    createBrowsableMediaItem(MEDIA_ID_CONTINUE_LISTENING), libraryParams))
        }
        return Futures.immediateFuture(LibraryResult.ofItem(createBrowsableMediaItem(MEDIA_ID_ROOT), libraryParams))
    }

    override fun onGetItem(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
        if (BROWSABLE_MEDIA_IDS.contains(mediaId) || mediaId.startsWith(MediaItemAdapter.MEDIA_ID_FEED_PREFIX)) {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            Single.fromCallable<MediaItem> { createBrowsableMediaItem(mediaId) }
                    .subscribeOn(Schedulers.io())
                    .subscribe({ item -> future.set(LibraryResult.ofItem(item, null)) },
                            { future.setException(it) })
            return future
        }
        return super.onGetItem(session, browser, mediaId)
    }

    override fun onGetChildren(
            session: MediaLibraryService.MediaLibrarySession, browser: MediaSession.ControllerInfo,
            parentId: String, page: Int, pageSizeRequest: Int,
            params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val pageSize = Math.min(100, pageSizeRequest) // Safety limit when calling application wants too much
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()

        when (parentId) {
            MEDIA_ID_ROOT -> {
                Single.fromCallable<ImmutableList<MediaItem>> { ImmutableList.of(
                                createBrowsableMediaItem(MEDIA_ID_CONTINUE_LISTENING),
                                createBrowsableMediaItem(MEDIA_ID_QUEUE),
                                createBrowsableMediaItem(MEDIA_ID_DOWNLOADS),
                                createBrowsableMediaItem(MEDIA_ID_EPISODES),
                                createBrowsableMediaItem(MEDIA_ID_SUBSCRIPTIONS)) }
                        .subscribeOn(Schedulers.io())
                        .subscribe({ items -> future.set(LibraryResult.ofItemList(items, params)) },
                                { future.setException(it) })
                return future
            }
            MEDIA_ID_SUBSCRIPTIONS -> {
                Single.fromCallable<List<Feed>> { DBReader.getFeedList() }
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                                { items ->
                                    val builder = ImmutableList.builder<MediaItem>()
                                    for (feed in items) {
                                        if (feed.getState() == Feed.STATE_SUBSCRIBED) {
                                            builder.add(MediaItemAdapter.fromFeed(context, feed))
                                        }
                                    }
                                    future.set(LibraryResult.ofItemList(builder.build(), params))
                                },
                                { future.setException(it) })
                return future
            }
            MEDIA_ID_CONTINUE_LISTENING -> {
                Single.fromCallable<List<FeedItem>>(
                                { DBReader.getPausedQueue(CONTINUE_LISTENING_NUM_EPISODES) })
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                                { items -> future.set(LibraryResult.ofItemList(
                                        MediaItemAdapter.fromItemList(context, items), params)) },
                                { error ->
                                    Log.e(TAG, "Failed to load continue listening", error)
                                    future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                                }
                        )
                return future
            }
            else -> { // Episodes lists
                Single.fromCallable<List<FeedItem>> {
                    if (parentId.startsWith(MediaItemAdapter.MEDIA_ID_FEED_PREFIX)) {
                        val feedId = java.lang.Long.parseLong(parentId.split(":")[1])
                        return@fromCallable DBReader.getFeed(feedId, true, page * pageSize, pageSize)!!.getItems()!!
                    }
                    when (parentId) {
                        MEDIA_ID_QUEUE -> DBReader.getQueue()
                        MEDIA_ID_DOWNLOADS -> DBReader.getEpisodes(page * pageSize, pageSize,
                                FeedItemFilter(FeedItemFilter.DOWNLOADED),
                                UserPreferences.getDownloadsSortedOrder())
                        MEDIA_ID_EPISODES -> DBReader.getEpisodes(page * pageSize, pageSize,
                                FeedItemFilter(UserPreferences.getPrefFilterAllEpisodes()),
                                UserPreferences.getAllEpisodesSortOrder())
                        else -> throw IllegalArgumentException("Unknown parentId: " + parentId)
                    }
                }
                        .subscribeOn(Schedulers.io())
                        .subscribe({ items -> future.set(LibraryResult.ofItemList(
                                        MediaItemAdapter.fromItemList(context, items), params)) },
                                { future.setException(it) })
                return future
            }
        }
    }

    override fun onGetSearchResult(
            session: MediaLibraryService.MediaLibrarySession, browser: MediaSession.ControllerInfo,
            query: String, page: Int, pageSize: Int, params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        Single.fromCallable<List<FeedItem>> {
                    DBReader.searchFeedItems(0, query, FeedItemFilter.unfiltered()) }
                .subscribeOn(Schedulers.io())
                .subscribe({ items -> future.set(LibraryResult.ofItemList(
                                MediaItemAdapter.fromItemList(context, items), params)) },
                        { future.setException(it) })
        return future
    }

    override fun onSearch(session: MediaLibraryService.MediaLibrarySession,
                          browser: MediaSession.ControllerInfo, query: String,
                          params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<Void>> {
        if (query.isEmpty()) {
            session.notifySearchResultChanged(browser, query, 0, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }
        Single.fromCallable<List<FeedItem>> { DBReader.searchFeedItems(0, query, FeedItemFilter.unfiltered()) }
                .subscribeOn(Schedulers.io())
                .subscribe(
                        { items -> session.notifySearchResultChanged(browser, query, items.size, params) },
                        { error ->
                            Log.e(TAG, "Search failed", error)
                            session.notifySearchResultChanged(browser, query, 0, params)
                        })
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    private fun enrichMediaItems(mediaItems: List<MediaItem>): List<MediaItem> {
        val builder = ImmutableList.builder<MediaItem>()
        for (item in mediaItems) {
            try {
                val mediaId = java.lang.Long.parseLong(item.mediaId)
                val media = DBReader.getFeedMedia(mediaId)
                if (media == null) {
                    Log.e(TAG, "Media not found for ID: " + mediaId)
                    continue
                }
                builder.add(MediaItemAdapter.fromPlayable(context, media, false))
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Invalid media ID: " + item.mediaId, e)
            }
        }
        return builder.build()
    }

    private fun createBrowsableMediaItem(id: String): MediaItem {
        if (id.startsWith(MediaItemAdapter.MEDIA_ID_FEED_PREFIX)) {
            val feedId = java.lang.Long.parseLong(id.split(":")[1])
            val feed = DBReader.getFeed(feedId, false, 0, 0)!!
            return MediaItemAdapter.fromFeed(context, feed)
        }
        when (id) {
            MEDIA_ID_ROOT ->
                return MediaItemAdapter.from(context, MEDIA_ID_ROOT,
                        context.getString(R.string.app_name), R.drawable.ic_notification, null)
            MEDIA_ID_QUEUE -> {
                val numEpisodes = DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.QUEUED))
                return MediaItemAdapter.from(context, MEDIA_ID_QUEUE,
                        context.getString(R.string.queue_label), R.drawable.ic_playlist_play_black,
                        context.getResources().getQuantityString(R.plurals.num_episodes, numEpisodes, numEpisodes))
            }
            MEDIA_ID_DOWNLOADS -> {
                val numEpisodes = DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.DOWNLOADED))
                return MediaItemAdapter.from(context, MEDIA_ID_DOWNLOADS,
                        context.getString(R.string.downloads_label), R.drawable.ic_download_black,
                        context.getResources().getQuantityString(R.plurals.num_episodes, numEpisodes, numEpisodes))
            }
            MEDIA_ID_EPISODES -> {
                val numEpisodes = DBReader.getTotalEpisodeCount(FeedItemFilter())
                return MediaItemAdapter.from(context, MEDIA_ID_EPISODES,
                        context.getString(R.string.episodes_label), R.drawable.ic_feed_black,
                        context.getResources().getQuantityString(R.plurals.num_episodes, numEpisodes, numEpisodes))
            }
            MEDIA_ID_SUBSCRIPTIONS ->
                return MediaItemAdapter.from(context, MEDIA_ID_SUBSCRIPTIONS,
                        context.getString(R.string.subscriptions_label), R.drawable.ic_subscriptions_black, null)
            MEDIA_ID_CONTINUE_LISTENING ->
                return MediaItemAdapter.from(context, MEDIA_ID_CONTINUE_LISTENING,
                        context.getString(R.string.current_playing_episode), R.drawable.ic_play_48dp_black, null)
            else ->
                throw IllegalArgumentException("ID not known: " + id)
        }
    }
}
