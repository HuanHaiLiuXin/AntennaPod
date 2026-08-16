package de.danoeh.antennapod.playback.service

import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.util.Log
import android.webkit.URLUtil
import androidx.annotation.OptIn
import androidx.core.util.Pair
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.PlayerErrorEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.StreamingConfirmationEvent
import de.danoeh.antennapod.event.playback.BufferUpdateEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent
import de.danoeh.antennapod.event.playback.SpeedChangedEvent
import de.danoeh.antennapod.event.settings.VolumeAdaptionChangedEvent
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.playback.base.MediaItemAdapter
import de.danoeh.antennapod.playback.base.PlayerStatus
import de.danoeh.antennapod.playback.base.RewindAfterPauseUtils
import de.danoeh.antennapod.playback.cast.CastPlayerWrapper
import de.danoeh.antennapod.playback.service.internal.ClockSleepTimer
import de.danoeh.antennapod.playback.service.internal.EpisodeSleepTimer
import de.danoeh.antennapod.playback.service.internal.ExoPlayerUtils
import de.danoeh.antennapod.playback.service.internal.MediaLibrarySessionCallback
import de.danoeh.antennapod.playback.service.internal.PlayableUtils
import de.danoeh.antennapod.playback.service.internal.SkipUtils
import de.danoeh.antennapod.playback.service.internal.SleepTimer
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences
import de.danoeh.antennapod.storage.preferences.SleepTimerType
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.chapters.ChapterUtils
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils
import de.danoeh.antennapod.ui.notifications.NotificationUtils
import de.danoeh.antennapod.ui.widget.WidgetUpdater
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.Calendar
import java.util.concurrent.TimeUnit

class Media3PlaybackService : MediaLibraryService() {
    companion object {
        private const val TAG = "M3PlaybackService"
        private const val POSITION_SAVE_INTERVAL_MS = 5000L

        private fun needsStreaming(media: FeedMedia): Boolean {
            return !media.localFileAvailable() && !URLUtil.isContentUrl(media.getStreamUrl())
        }
    }

    private var exoPlayer: ExoPlayer? = null
    private var player: Player? = null
    private var mediaSession: MediaLibrarySession? = null
    private var currentPlayable: FeedMedia? = null
    private var pendingStreamMediaId: String? = null
    private var allowStreamingThisTime = false
    private var mediaLoaderDisposable: Disposable? = null
    private var positionObserverDisposable: Disposable? = null
    private var queueLoaderDisposable: Disposable? = null
    private var lastPositionSaveTime = 0L
    private var sleepTimer: SleepTimer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var volumeAdaptionFactor = 1.0f

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        EventBus.getDefault().register(this)
        val notificationProvider = DefaultMediaNotificationProvider(this,
                { R.id.notification_playing },
                NotificationUtils.CHANNEL_ID_PLAYING, R.string.notification_channel_playing)
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)

        exoPlayer = ExoPlayerUtils.buildPlayer(this)
        exoPlayer!!.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                initLoudnessEnhancer(audioSessionId)
            }
        })
        initLoudnessEnhancer(exoPlayer!!.getAudioSessionId())
        val maybeCastPlayer = CastPlayerWrapper.wrap(exoPlayer!!, this)
        player = object : ForwardingPlayer(maybeCastPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands()
                        .buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
            }

            override fun play() {
                if (handleStreamingConfirmation()) {
                    return
                } else if (shouldBlockForStreamingConfirmation()) {
                    showStreamingConfirmation(currentPlayable!!)
                    return
                }

                if (currentPlayable != null && !getPlayWhenReady()) {
                    val savedPosition = getCurrentPosition()
                    val startPosition = RewindAfterPauseUtils.calculatePositionWithRewind(
                            savedPosition.toInt(), currentPlayable!!.getLastPlayedTimeStatistics()).toLong()
                    if (startPosition != savedPosition) {
                        seekTo(startPosition)
                    }
                }
                super.play()
            }

            override fun setPlaybackSpeed(speed: Float) {
                super.setPlaybackSpeed(speed)
                PlaybackPreferences.setCurrentlyPlayingTemporaryPlaybackSpeed(speed)
                EventBus.getDefault().post(SpeedChangedEvent(speed))
            }

            override fun seekBack() {
                seekTo(Math.max(0, getCurrentPosition() - UserPreferences.getRewindSecs() * 1000L))
            }

            override fun seekForward() {
                val duration = getDuration()
                val target = getCurrentPosition() + UserPreferences.getFastForwardSecs() * 1000L

                if (duration > 0 && target >= duration) {
                    handlePlaybackEnded()
                    return
                }

                seekTo(target)
            }

            override fun seekToNextMediaItem() {
                if (currentPlayable != null) {
                    startNextInQueue(currentPlayable!!, true, false)
                }
            }

            override fun seekTo(positionMs: Long) {
                super.seekTo(positionMs)
                EventBus.getDefault().post(
                        PlaybackPositionEvent(positionMs.toInt(), player!!.getDuration().toInt()))
            }
        }
        player!!.addListener(playerListener)
        mediaSession = MediaLibraryService.MediaLibrarySession.Builder(this, player!!, sessionCallback)
                .setSessionActivity(MainActivityStarter(this).withOpenPlayer().getPendingIntent())
                .build()
        if (isCasting()) {
            keepServiceRunningWhileCasting()
            loadCurrentMediaWhileCasting()
        }
    }

    private fun loadCurrentMediaWhileCasting() {
        val mediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
        if (mediaId == PlaybackPreferences.NO_MEDIA_PLAYING) {
            return
        }
        PlaybackController.bindToMedia3Service(this) { controller ->
            if (player!!.getCurrentMediaItem() == null && isCasting()) {
                controller.setPlayWhenReady(false)
                controller.setMediaItem(MediaItemAdapter.fromMediaIdStub(mediaId))
                controller.prepare()
            }
        }
    }

    /**
     * When freshly binding to the service to start playback (while chromecast is already connected),
     * the service gets started but casting does not transition to PLAYING fast enough. So when unbinding,
     * the service gets destroyed again and releases the session, which stops casting again.
     * This method manually starts the service, to be used when connected to chromecast.
     */
    private fun keepServiceRunningWhileCasting() {
        try {
            startService(Intent(this, Media3PlaybackService::class.java))
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Unable to keep service running while casting", e)
        }
    }

    private val sessionCallback: MediaLibrarySessionCallback = object : MediaLibrarySessionCallback(this@Media3PlaybackService) {
        @UnstableApi
        override fun onCustomCommand(session: MediaSession,
                                     controller: MediaSession.ControllerInfo,
                                     customCommand: SessionCommand,
                                     args: Bundle): ListenableFuture<SessionResult> {
            if (customCommand.customAction == SESSION_COMMAND_PLAYBACK_SPEED.customAction) {
                setNextPlaybackSpeed()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else if (customCommand.customAction == SESSION_COMMAND_NEXT_CHAPTER.customAction) {
                seekToNextChapter()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else if (customCommand.customAction == SESSION_COMMAND_SKIP_SILENCE.customAction) {
                val enabled = MediaLibrarySessionCallback.getBoolean(args, false)
                PlaybackPreferences.setCurrentlyPlayingTemporarySkipSilence(enabled)
                exoPlayer!!.setSkipSilenceEnabled(enabled)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else if (customCommand.customAction == SESSION_COMMAND_SET_SLEEP_TIMER.customAction) {
                startSleepTimer(SleepTimerPreferences.timerMillisOrEpisodes())
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else if (customCommand.customAction == SESSION_COMMAND_DISABLE_SLEEP_TIMER.customAction) {
                disableSleepTimer()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else if (customCommand.customAction == SESSION_COMMAND_EXTEND_SLEEP_TIMER.customAction) {
                extendSleepTimer(MediaLibrarySessionCallback.getLong(args, 0))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    @UnstableApi
    private val playerListener: Player.Listener = object : Player.Listener {
        private var wasTemporarilySuspended = false

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (playbackSuppressionReason
                    == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                wasTemporarilySuspended = true
            } else if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
                    && wasTemporarilySuspended && currentPlayable != null) {
                wasTemporarilySuspended = false
                val savedPosition = player!!.getCurrentPosition()
                val startPosition = RewindAfterPauseUtils.calculatePositionWithRewind(
                        savedPosition.toInt(), currentPlayable!!.getLastPlayedTimeStatistics()).toLong()
                if (startPosition != savedPosition) {
                    player!!.seekTo(startPosition)
                }
            } else {
                wasTemporarilySuspended = false
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                EventBus.getDefault().post(BufferUpdateEvent.started())
                PlaybackService.isRunning = player!!.getPlayWhenReady() // Immediately show as playing
                updatePlaybackPreferences()
            } else {
                EventBus.getDefault().post(BufferUpdateEvent.ended())
            }
            if ((playbackState == Player.STATE_READY && player!!.getPlayWhenReady())
                    || playbackState == Player.STATE_ENDED) {
                saveCurrentPosition()
            }
            if (playbackState == Player.STATE_ENDED && currentPlayable != null) {
                handlePlaybackEnded()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            PlaybackService.isRunning = !Util.shouldShowPlayButton(player!!)
            if (PlaybackService.isRunning) {
                lastPositionSaveTime = System.currentTimeMillis()
                setupPositionObserver()
            } else {
                cancelPositionObserver()
                saveCurrentPosition()
                if (currentPlayable != null) {
                    SynchronizationQueue.getInstance()!!.enqueueEpisodePlayed(currentPlayable!!, false)
                }
            }
            val widgetState = WidgetUpdater.WidgetState(currentPlayable,
                    if (PlaybackService.isRunning) PlayerStatus.PLAYING else PlayerStatus.PAUSED,
                    player!!.getContentPosition().toInt(), player!!.getDuration().toInt(),
                    player!!.getPlaybackParameters().speed)
            Schedulers.io().scheduleDirect {
                WidgetUpdater.updateWidget(this@Media3PlaybackService, widgetState)
            }
            updatePlaybackPreferences()

            // Auto-enable sleep timer when playback starts
            if (PlaybackService.isRunning && sleepTimer == null && SleepTimerPreferences.autoEnable()) {
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                if (SleepTimerPreferences.isInTimeRange(
                                SleepTimerPreferences.autoEnableFrom(),
                                SleepTimerPreferences.autoEnableTo(),
                                currentHour)) {
                    startSleepTimer(SleepTimerPreferences.timerMillisOrEpisodes())
                }
            }
        }

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
            if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                keepServiceRunningWhileCasting()
                loadCurrentMediaWhileCasting()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) {
                if (currentPlayable != null
                        && CastPlayerWrapper.hasPlaybackJustFinished(this@Media3PlaybackService)) {
                    handlePlaybackEnded()
                } else {
                    currentPlayable = null
                }
            } else {
                ensureCurrentMediaLoaded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            PlaybackService.isRunning = false
            EventBus.getDefault().post(PlayerErrorEvent(
                    ExoPlayerUtils.translateErrorReason(error, this@Media3PlaybackService)))
            EventBus.getDefault().post(PlayerStatusEvent())
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    @UnstableApi
    override fun onDestroy() {
        PlaybackService.isRunning = false
        cancelPositionObserver()
        if (sleepTimer != null) {
            sleepTimer!!.stop()
            sleepTimer = null
        }
        EventBus.getDefault().unregister(this)
        if (mediaLoaderDisposable != null) {
            mediaLoaderDisposable!!.dispose()
            mediaLoaderDisposable = null
        }
        if (queueLoaderDisposable != null) {
            queueLoaderDisposable!!.dispose()
            queueLoaderDisposable = null
        }
        saveCurrentPosition()
        if (loudnessEnhancer != null) {
            loudnessEnhancer!!.release()
            loudnessEnhancer = null
        }
        if (player != null) {
            player!!.removeListener(playerListener)
            player!!.release()
        }
        ExoPlayerUtils.releaseCache()
        if (mediaSession != null) {
            mediaSession!!.release()
        }
        super.onDestroy()
    }

    private fun setupPositionObserver() {
        if (positionObserverDisposable != null) {
            positionObserverDisposable!!.dispose()
        }

        positionObserverDisposable = Observable.interval(1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            val media = currentPlayable
                            val p = this.player
                            if (media == null || p == null) {
                                return@subscribe
                            }
                            val position = p.getCurrentPosition()
                            val duration = p.getDuration()
                            val speed = p.getPlaybackParameters().speed
                            if (duration > 0) {
                                EventBus.getDefault().post(
                                        PlaybackPositionEvent(position.toInt(), duration.toInt()))
                                val widgetState = WidgetUpdater.WidgetState(media,
                                        if (Util.shouldShowPlayButton(p)) PlayerStatus.PAUSED else PlayerStatus.PLAYING,
                                        position.toInt(), duration.toInt(), speed)
                                Schedulers.io().scheduleDirect {
                                    WidgetUpdater.updateWidget(this@Media3PlaybackService, widgetState)
                                }
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastPositionSaveTime >= POSITION_SAVE_INTERVAL_MS) {
                                    saveCurrentPosition()
                                    lastPositionSaveTime = currentTime
                                }
                                if (SkipUtils.skipEndingIfNecessary(this@Media3PlaybackService,
                                                media, position, duration, speed)) {
                                    p.seekTo(p.getDuration())
                                }
                            }
                        }, { error -> Log.e(TAG, "Position observer error", error) })
    }

    private fun cancelPositionObserver() {
        if (positionObserverDisposable != null) {
            positionObserverDisposable!!.dispose()
            positionObserverDisposable = null
        }
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun ensureCurrentMediaLoaded() {
        val player = this.player ?: return
        val currentItem = player.getCurrentMediaItem() ?: return
        if (MediaItemAdapter.MEDIA_ID_CONFIRM_STREAMING == currentItem.mediaId) {
            return
        }
        pendingStreamMediaId = null
        try {
            val mediaId = java.lang.Long.parseLong(currentItem.mediaId)
            if (currentPlayable == null || currentPlayable!!.getId() != mediaId) {
                if (mediaLoaderDisposable != null) {
                    mediaLoaderDisposable!!.dispose()
                }
                mediaLoaderDisposable = Single.fromCallable<FeedMedia> {
                    val previousMediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
                    if (previousMediaId != PlaybackPreferences.NO_MEDIA_PLAYING && previousMediaId != mediaId) {
                        updateDatabaseAfterPlayback(DBReader.getFeedMedia(previousMediaId), false, false, true)
                    }
                    val media = DBReader.getFeedMedia(mediaId)!!
                    ChapterUtils.loadChapters(media, this@Media3PlaybackService, false)
                    media
                }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                { media ->
                                    val p = this.player
                                    if (p == null || confirmStreamingIfNeeded(media)) {
                                        return@subscribe
                                    }
                                    media.setPosition(p.getCurrentPosition().toInt())
                                    if (media.getItem() != null && !media.getItem()!!.isTagged(FeedItem.TAG_QUEUE)) {
                                        DBWriter.addQueueItem(this@Media3PlaybackService, media.getItem()!!)
                                    }
                                    switchToPlayable(media)
                                },
                                { error -> Log.e(TAG, "Failed to load current media", error) })

            }
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Invalid media ID: " + currentItem.mediaId, e)
        }
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun confirmStreamingIfNeeded(media: FeedMedia): Boolean {
        if (needsStreaming(media) && !NetworkUtils.isStreamingAllowed()
                && !allowStreamingThisTime && !isCasting()) {
            showStreamingConfirmation(media)
            return true
        }
        allowStreamingThisTime = false
        return false
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun switchToPlayable(media: FeedMedia) {
        currentPlayable = media
        media.onPlaybackStart()

        val speed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(media)
        player!!.setPlaybackSpeed(speed)
        val enabled = PlaybackSpeedUtils.getCurrentSkipSilencePreference(media) == FeedPreferences.SkipSilence.AGGRESSIVE
        PlaybackPreferences.setCurrentlyPlayingTemporarySkipSilence(enabled)
        exoPlayer!!.setSkipSilenceEnabled(enabled)
        if (media.getItem() != null && media.getItem()!!.getFeed() != null) {
            volumeAdaptionFactor = media.getItem()!!.getFeed()!!.getPreferences()!!
                    .getVolumeAdaptionSetting().getAdaptionFactor()
            applyVolumeAdaption(1.0f)
        }
        updatePlaybackPreferences()
    }

    private fun updatePlaybackPreferences() {
        val statusBefore = PlaybackPreferences.getCurrentPlayerStatus()
        val mediaBefore = PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
        if (currentPlayable != null) {
            PlaybackPreferences.writeMediaPlaying(currentPlayable)
        }
        val status = if (PlaybackService.isRunning) PlaybackPreferences.PLAYER_STATUS_PLAYING
                else PlaybackPreferences.PLAYER_STATUS_PAUSED
        PlaybackPreferences.setCurrentPlayerStatus(status)
        if (status != statusBefore || (currentPlayable != null && currentPlayable!!.getId() != mediaBefore)) {
            EventBus.getDefault().post(PlayerStatusEvent())
        }
    }

    private fun saveCurrentPosition() {
        val media = currentPlayable ?: return
        val player = this.player ?: return
        val currentItem = player.getCurrentMediaItem() ?: return
        try {
            if (media.getId() != java.lang.Long.parseLong(currentItem.mediaId)) {
                return
            }
        } catch (e: NumberFormatException) {
            return
        }
        val position = player.getCurrentPosition()
        val timestamp = System.currentTimeMillis()
        PlayableUtils.saveCurrentPosition(media, position.toInt(), timestamp)
    }

    private fun updateDatabaseAfterPlayback(media: FeedMedia?, ended: Boolean, skipped: Boolean, playingNext: Boolean) {
        if (media == null) {
            return
        }

        val item = media.getItem()
        val smartMarkAsPlayedSecs = UserPreferences.getSmartMarkAsPlayedSecs()
        val almostEnded = media.getDuration() > 0
                && media.getPosition() >= media.getDuration() - smartMarkAsPlayedSecs * 1000

        SynchronizationQueue.getInstance()!!.enqueueEpisodePlayed(media, ended || almostEnded)
        if (item != null) {
            if (ended || almostEnded) {
                DBWriter.markItemsPlayed(FeedItem.PLAYED, true, listOf(item))
            }
            if (ended || almostEnded || (skipped && !UserPreferences.shouldSkipKeepEpisode())) {
                DBWriter.removeQueueItem(this, ended, item)
                val action = item.getFeed()!!.getPreferences()!!.getCurrentAutoDelete()
                val autoDeleteEnabledGlobally = UserPreferences.isAutoDelete()
                        && (!item.getFeed()!!.isLocalFeed() || UserPreferences.isAutoDeleteLocal())
                val shouldAutoDelete = action == FeedPreferences.AutoDeleteAction.ALWAYS
                        || (action == FeedPreferences.AutoDeleteAction.GLOBAL && autoDeleteEnabledGlobally)
                if (shouldAutoDelete && (!item.isTagged(FeedItem.TAG_FAVORITE)
                                || !UserPreferences.shouldFavoriteKeepEpisode())) {
                    DBWriter.deleteFeedMediaOfItem(this, media)
                }
            }
        }
        if (ended || skipped || playingNext) {
            DBWriter.addItemToPlaybackHistory(media)
        }
    }

    private fun setNextPlaybackSpeed() {
        val p = this.player ?: return
        val selectedSpeeds = UserPreferences.getPlaybackSpeedArray()
        if (selectedSpeeds.size <= 1) {
            return
        }

        val currentSpeed = p.getPlaybackParameters().speed
        val speedPosition = selectedSpeeds.indexOf(currentSpeed)
        val newSpeed: Float

        if (speedPosition == selectedSpeeds.size - 1 || speedPosition == -1) {
            newSpeed = selectedSpeeds.get(0)
        } else {
            newSpeed = selectedSpeeds.get(speedPosition + 1)
        }

        p.setPlaybackSpeed(newSpeed)
    }

    @UnstableApi
    private fun seekToNextChapter() {
        val media = currentPlayable ?: return
        val p = this.player ?: return
        val chapters = media.getChapters()
        if (chapters == null) {
            if (media.getItem() != null) {
                startNextInQueue(media, true, false)
            }
            return
        }

        val nextChapter = Chapter.getAfterPosition(chapters, p.getCurrentPosition().toInt()) + 1

        if (chapters.size < nextChapter + 1) {
            if (media.getItem() != null) {
                startNextInQueue(media, true, false)
            }
            return
        }

        p.seekTo(chapters.get(nextChapter).getStart())
    }

    private fun shouldBlockForStreamingConfirmation(): Boolean {
        return currentPlayable != null
                && (player!!.getPlaybackState() == Player.STATE_READY
                || player!!.getPlaybackState() == Player.STATE_BUFFERING)
                && needsStreaming(currentPlayable!!)
                && !NetworkUtils.isStreamingAllowed()
                && !isCasting()
    }

    private fun handleStreamingConfirmation(): Boolean {
        if (pendingStreamMediaId != null && player!!.getCurrentMediaItem() != null
                && MediaItemAdapter.MEDIA_ID_CONFIRM_STREAMING == player!!.getCurrentMediaItem()!!.mediaId) {
            val mediaId = pendingStreamMediaId
            pendingStreamMediaId = null
            allowStreamingThisTime = true
            val mediaItem = MediaItem.Builder()
                    .setMediaId(mediaId!!)
                    .build()
            PlaybackController.bindToMedia3Service(
                    this) { controller ->
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            }
            return true
        }
        return false
    }

    @UnstableApi
    private fun showStreamingConfirmation(media: FeedMedia) {
        pendingStreamMediaId = media.getId().toString()
        currentPlayable = null
        val confirmItem = MediaItemAdapter.buildStreamingConfirmationItem(this,
                R.raw.no_streaming,
                getString(R.string.confirm_mobile_streaming_notification_title),
                getString(R.string.confirm_mobile_streaming_notification_message))
        player!!.setMediaItem(confirmItem)
        player!!.setPlayWhenReady(false)
        player!!.prepare()
        PlaybackService.isRunning = false
        EventBus.getDefault().post(StreamingConfirmationEvent())
        EventBus.getDefault().post(PlayerStatusEvent())
    }

    @UnstableApi
    private fun handlePlaybackEnded() {
        val media = currentPlayable
        currentPlayable = null // To avoid position updater saving position after we already reset it
        if (sleepTimer != null && sleepTimer!!.isActive()) {
            sleepTimer!!.episodeFinishedPlayback()
            if (!sleepTimer!!.shouldContinueToNextEpisode()) {
                updateDatabaseAfterPlayback(media, true, false, false)
                player!!.stop()
                player!!.clearMediaItems()
                PlaybackPreferences.writeNoMediaPlaying()
                EventBus.getDefault().post(
                        PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN))
                EventBus.getDefault().post(PlayerStatusEvent())
                return
            }
        }
        startNextInQueue(media, false, true)
    }

    private fun isCasting(): Boolean {
        return player!!.getDeviceInfo().playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
    }

    /**
     * Loads the next item, and starts it if continuous playback is enabled.
     */
    @UnstableApi
    private fun startNextInQueue(media: FeedMedia?, wasSkipped: Boolean, ended: Boolean) {
        if (queueLoaderDisposable != null) {
            queueLoaderDisposable!!.dispose()
        }
        if (media == null) {
            return
        }
        val item = media.getItem()
        if (item == null) {
            return
        }
        queueLoaderDisposable = Maybe.fromCallable<Pair<FeedMedia, MediaItem>> {
            val nextItem = DBReader.getNextInQueue(item)
            val hasNext = nextItem != null && nextItem.getMedia() != null
            updateDatabaseAfterPlayback(media, ended, wasSkipped, hasNext)
            if (hasNext) {
                Pair(nextItem!!.getMedia()!!,
                        MediaItemAdapter.fromPlayable(this@Media3PlaybackService, nextItem.getMedia()!!, false))
            } else {
                null
            }
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { pair ->
                            val nextMedia = pair!!.first
                            val nextMediaItem = pair.second
                            val p = this.player
                            if (p == null || confirmStreamingIfNeeded(nextMedia)) {
                                return@subscribe
                            }
                            switchToPlayable(nextMedia)
                            p.setPlayWhenReady(UserPreferences.isFollowQueue())
                            p.setMediaItem(nextMediaItem, SkipUtils.skipIntroIfNecessary(this@Media3PlaybackService, nextMedia))
                            p.prepare()
                        },
                        { error -> Log.e(TAG, "Failed to load next queue item", error) },
                        {
                            currentPlayable = null
                            player!!.stop()
                            player!!.clearMediaItems()
                            PlaybackPreferences.writeNoMediaPlaying()
                            EventBus.getDefault().post(PlayerStatusEvent())
                            EventBus.getDefault().post(
                                    PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN))
                            if (wasSkipped) {
                                EventBus.getDefault().post(MessageEvent(getString(R.string.no_following_in_queue)))
                            }
                        })
    }

    @UnstableApi
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSleepTimerUpdated(event: SleepTimerUpdatedEvent) {
        if (event.isOver()) {
            Log.d(TAG, "Sleep timer expired, pausing playback")
            if (player != null) {
                applyVolumeAdaption(1.0f)
                player!!.pause()
            }
            sleepTimer = null
            sessionCallback.refreshNotification(mediaSession!!)
        } else if (event.isCancelled()) {
            applyVolumeAdaption(1.0f)
            sessionCallback.refreshNotification(mediaSession!!)
        } else if (!event.wasJustEnabled()) {
            val millisLeft = event.getMillisTimeLeft()
            if (millisLeft < SleepTimer.NOTIFICATION_THRESHOLD && millisLeft > 0) {
                val volume = millisLeft.toFloat() / SleepTimer.NOTIFICATION_THRESHOLD
                applyVolumeAdaption(Math.max(0.1f, volume))
            } else {
                applyVolumeAdaption(1.0f)
            }
        }
    }

    @UnstableApi
    private fun startSleepTimer(timeOrEpisodes: Long) {
        if (sleepTimer != null) {
            sleepTimer!!.stop()
        }
        if (SleepTimerPreferences.getSleepTimerType() == SleepTimerType.EPISODES) {
            sleepTimer = EpisodeSleepTimer(this)
        } else {
            sleepTimer = ClockSleepTimer(this)
        }
        sleepTimer!!.start(timeOrEpisodes)
        sessionCallback.refreshNotification(mediaSession!!)
    }

    private fun disableSleepTimer() {
        if (sleepTimer != null) {
            sleepTimer!!.stop()
            sleepTimer = null
        } else if (!BuildConfig.DEBUG) {
            // Unblock user in case we somehow got into an inconsistent state
            EventBus.getDefault().postSticky(SleepTimerUpdatedEvent.cancelled())
        }
        if (player != null) {
            applyVolumeAdaption(1.0f)
        }
    }

    private fun extendSleepTimer(additionalTime: Long) {
        if (sleepTimer != null && sleepTimer!!.isActive()) {
            val currentLeft = sleepTimer!!.getTimeLeft().getDisplayValue()
            sleepTimer!!.updateRemainingTime(currentLeft + additionalTime)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun volumeAdaptionChanged(event: VolumeAdaptionChangedEvent) {
        if (currentPlayable != null && currentPlayable!!.getItem() != null
                && currentPlayable!!.getItem()!!.getFeed() != null
                && currentPlayable!!.getItem()!!.getFeed()!!.getId() == event.getFeedId()) {
            currentPlayable!!.getItem()!!.getFeed()!!.getPreferences()!!
                    .setVolumeAdaptionSetting(event.getVolumeAdaptionSetting())
            volumeAdaptionFactor = event.getVolumeAdaptionSetting().getAdaptionFactor()
            applyVolumeAdaption(1.0f)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun feedItemsUpdated(event: FeedItemEvent) {
        if (currentPlayable == null || !currentPlayable!!.localFileAvailable()) {
            return
        }
        val index = FeedItemEvent.indexOfItemWithId(event.items, currentPlayable!!.getItemId())
        if (index >= 0 && event.items.get(index).getMedia() != null
                && !event.items.get(index).getMedia()!!.localFileAvailable()) {
            player!!.stop()
            player!!.clearMediaItems()
            currentPlayable = null
            PlaybackPreferences.writeNoMediaPlaying()
            EventBus.getDefault().post(PlayerStatusEvent())
        }
    }

    private fun initLoudnessEnhancer(audioSessionId: Int) {
        if (!VolumeAdaptionSetting.isBoostSupported()) {
            return
        }
        val oldEnhancer = this.loudnessEnhancer
        try {
            val newEnhancer = LoudnessEnhancer(audioSessionId)
            if (oldEnhancer != null) {
                newEnhancer.setEnabled(oldEnhancer.getEnabled())
                if (oldEnhancer.getEnabled()) {
                    newEnhancer.setTargetGain(oldEnhancer.getTargetGain().toInt())
                }
                oldEnhancer.release()
            }
            this.loudnessEnhancer = newEnhancer
        } catch (e: Exception) {
            Log.d(TAG, e.toString())
            this.loudnessEnhancer = null
        }
    }

    private fun applyVolumeAdaption(baseVolume: Float) {
        val v = baseVolume * volumeAdaptionFactor
        if (v > 1) {
            player!!.setVolume(1.0f)
            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer!!.setEnabled(true)
                    loudnessEnhancer!!.setTargetGain((1000 * (v - 1)).toInt())
                }
            } catch (e: Exception) {
                Log.d(TAG, e.toString())
            }
        } else {
            player!!.setVolume(v)
            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer!!.setEnabled(false)
                }
            } catch (e: Exception) {
                Log.d(TAG, e.toString())
            }
        }
    }
}
