package de.danoeh.antennapod.playback.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.util.Pair
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent
import de.danoeh.antennapod.event.playback.SpeedChangedEvent
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.model.playback.TimerValue
import de.danoeh.antennapod.playback.base.BuildConfig
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer
import de.danoeh.antennapod.playback.base.PlayerStatus
import de.danoeh.antennapod.playback.service.internal.PlayableUtils
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.concurrent.ExecutionException

/**
 * Communicates with the playback service. GUI classes should use this class to
 * control playback instead of communicating with the PlaybackService directly.
 */
abstract class PlaybackController(private val activity: Activity) {
    companion object {
        private const val TAG = "PlaybackController"

        @JvmStatic
        fun bindToService(activity: Activity, consumer: Consumer<PlaybackService>) {
            if (!PlaybackService.isRunning) {
                return
            }
            activity.bindService(Intent(activity, PlaybackService::class.java), object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, service: IBinder) {
                    if (service is PlaybackService.LocalBinder) {
                        consumer.accept(service.getService())
                    }
                    try {
                        activity.unbindService(this)
                    } catch (e: IllegalArgumentException) {
                        // Ignore
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                }
            }, 0)
        }

        @JvmStatic
        fun bindToMedia3Service(context: Context, consumer: Consumer<MediaController>) {
            val sessionToken = SessionToken(context,
                    ComponentName(context, Media3PlaybackService::class.java))
            val controllerFuture: ListenableFuture<MediaController> =
                    MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture.addListener({
                var controller: MediaController? = null
                try {
                    controller = controllerFuture.get()
                    consumer.accept(controller)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (controller != null) {
                        controller.release()
                    }
                }
            }, MoreExecutors.directExecutor())
        }
    }

    private var playbackService: PlaybackService? = null
    private var media: Playable? = null
    private var status: PlayerStatus = PlayerStatus.STOPPED

    private var mediaInfoLoaded = false
    private var released = false
    private var initialized = false
    private var eventsRegistered = false
    private var loadedFeedMedia = -1L

    /**
     * Creates a new connection to the playbackService.
     */
    @Synchronized
    fun init() {
        if (!eventsRegistered) {
            EventBus.getDefault().register(this)
            eventsRegistered = true
        }
        if (PlaybackService.isRunning) {
            initServiceRunning()
        } else {
            updatePlayButtonShowsPlay(true)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackServiceEvent) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_STARTED) {
            init()
        }
    }

    @Synchronized
    private fun initServiceRunning() {
        if (initialized || BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
            return
        }
        initialized = true

        ContextCompat.registerReceiver(activity, statusUpdate, IntentFilter(
                PlaybackService.ACTION_PLAYER_STATUS_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(activity, notificationReceiver, IntentFilter(
                PlaybackServiceInterface.ACTION_PLAYER_NOTIFICATION), ContextCompat.RECEIVER_NOT_EXPORTED)

        if (!released) {
            bindToService()
        } else {
            throw IllegalStateException("Can't call init() after release() has been called")
        }
        checkMediaInfoLoaded()
    }

    /**
     * Should be called if the PlaybackController is no longer needed, for
     * example in the activity's onStop() method.
     */
    fun release() {
        Log.d(TAG, "Releasing PlaybackController")

        try {
            activity.unregisterReceiver(statusUpdate)
        } catch (e: IllegalArgumentException) {
            // ignore
        }

        try {
            activity.unregisterReceiver(notificationReceiver)
        } catch (e: IllegalArgumentException) {
            // ignore
        }
        unbind()
        media = null
        released = true

        if (eventsRegistered) {
            EventBus.getDefault().unregister(this)
            eventsRegistered = false
        }
    }

    private fun unbind() {
        try {
            activity.unbindService(mConnection)
        } catch (e: IllegalArgumentException) {
            // ignore
        }
        initialized = false
    }

    /**
     * Should be called in the activity's onPause() method.
     */
    fun pause() {
        mediaInfoLoaded = false
    }

    /**
     * Tries to establish a connection to the PlaybackService. If it isn't
     * running, the PlaybackService will be started with the last played media
     * as the arguments of the launch intent.
     */
    private fun bindToService() {
        if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
            return
        }
        Log.d(TAG, "Trying to connect to service")
        if (!PlaybackService.isRunning) {
            throw IllegalStateException("Trying to bind but service is not running")
        }
        val bound = activity.bindService(Intent(activity, PlaybackService::class.java), mConnection, 0)
        Log.d(TAG, "Result for service binding: $bound")
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            if (service is PlaybackService.LocalBinder) {
                playbackService = service.getService()
                if (!released) {
                    queryService()
                    Log.d(TAG, "Connection to Service established")
                } else {
                    Log.i(TAG, "Connection to playback service has been established, " +
                            "but controller has already been released")
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            playbackService = null
            initialized = false
            Log.d(TAG, "Disconnected from Service")
        }
    }

    private val statusUpdate: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received statusUpdate Intent.")
            val service = playbackService
            if (service != null) {
                val info: PlaybackServiceMediaPlayer.PSMPInfo = service.getPSMPInfo()
                status = info.getPlayerStatus()
                media = info.getPlayable()
                handleStatus()
            } else {
                Log.w(TAG, "Couldn't receive status update: playbackService was null")
                if (PlaybackService.isRunning) {
                    bindToService()
                } else {
                    status = PlayerStatus.STOPPED
                    handleStatus()
                }
            }
        }
    }

    private val notificationReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val type = intent.getIntExtra(PlaybackServiceInterface.EXTRA_NOTIFICATION_TYPE, -1)
            val code = intent.getIntExtra(PlaybackServiceInterface.EXTRA_NOTIFICATION_CODE, -1)
            if (code == -1 || type == -1) {
                Log.d(TAG, "Bad arguments. Won't handle intent")
                return
            }
            if (type == PlaybackServiceInterface.NOTIFICATION_TYPE_RELOAD) {
                if (playbackService == null && PlaybackService.isRunning) {
                    bindToService()
                    return
                }
                mediaInfoLoaded = false
                queryService()
            } else if (type == PlaybackServiceInterface.NOTIFICATION_TYPE_PLAYBACK_END) {
                onPlaybackEnd()
            }
        }

    }

    open fun onPlaybackEnd() {
    }

    /**
     * Is called whenever the PlaybackService changes its status. This method
     * should be used to update the GUI or start/cancel background threads.
     */
    private fun handleStatus() {
        Log.d(TAG, "status: $status")
        checkMediaInfoLoaded()
        when (status) {
            PlayerStatus.PLAYING -> updatePlayButtonShowsPlay(false)
            PlayerStatus.PREPARING -> if (playbackService != null) {
                updatePlayButtonShowsPlay(!playbackService!!.isStartWhenPrepared())
            }
            PlayerStatus.PAUSED, PlayerStatus.PREPARED, PlayerStatus.STOPPED, PlayerStatus.INITIALIZED ->
                updatePlayButtonShowsPlay(true)
            else -> Unit
        }
    }

    private fun checkMediaInfoLoaded() {
        if (!mediaInfoLoaded || loadedFeedMedia != PlaybackPreferences.getCurrentlyPlayingFeedMediaId()) {
            loadedFeedMedia = PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
            loadMediaInfo()
        }
        mediaInfoLoaded = true
    }

    protected open fun updatePlayButtonShowsPlay(showPlay: Boolean) {

    }

    abstract fun loadMediaInfo()

    /**
     * Called when connection to playback service has been established or
     * information has to be refreshed
     */
    private fun queryService() {
        Log.d(TAG, "Querying service info")
        val service = playbackService
        if (service != null) {
            val info: PlaybackServiceMediaPlayer.PSMPInfo = service.getPSMPInfo()
            status = info.getPlayerStatus()
            media = info.getPlayable()

            // make sure that new media is loaded if it's available
            mediaInfoLoaded = false
            handleStatus()

        } else {
            Log.e(TAG,
                    "queryService() was called without an existing connection to playbackservice")
        }
    }

    fun playPause() {
        val service = playbackService
        if (service == null) {
            PlaybackServiceStarter(activity, media).start()
            Log.w(TAG, "Play/Pause button was pressed, but playbackservice was null!")
            return
        }
        when (status) {
            PlayerStatus.PLAYING -> service.pause(true, false)
            PlayerStatus.PAUSED, PlayerStatus.PREPARED -> service.resume()
            PlayerStatus.PREPARING ->
                service.setStartWhenPrepared(!service.isStartWhenPrepared())
            PlayerStatus.INITIALIZED -> {
                service.setStartWhenPrepared(true)
                service.prepare()
            }
            else -> {
                PlaybackServiceStarter(activity, media)
                        .callEvenIfRunning(true)
                        .start()
                Log.w(TAG, "Play/Pause button was pressed and PlaybackService state was unknown")
            }
        }
    }

    fun getPosition(): Int {
        val service = playbackService
        return if (service != null) {
            service.getCurrentPosition()
        } else if (getMedia() != null) {
            getMedia()!!.getPosition()
        } else {
            Playable.INVALID_TIME
        }
    }

    fun getDuration(): Int {
        val service = playbackService
        return if (service != null) {
            service.getDuration()
        } else if (getMedia() != null) {
            getMedia()!!.getDuration()
        } else {
            Playable.INVALID_TIME
        }
    }

    fun getMedia(): Playable? {
        if (media == null) {
            media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
        }
        return media
    }

    fun sleepTimerActive(): Boolean {
        val service = playbackService
        return service != null && service.sleepTimerActive()
    }

    fun disableSleepTimer() {
        playbackService?.disableSleepTimer()
    }

    fun getSleepTimerTimeLeft(): TimerValue {
        val service = playbackService
        return if (service != null) {
            service.getSleepTimerTimeLeft()
        } else {
            TimerValue(Playable.INVALID_TIME.toLong(), Playable.INVALID_TIME.toLong())
        }
    }

    fun extendSleepTimer(extendTime: Long) {
        val timeLeft = getSleepTimerTimeLeft()
        if (playbackService != null && timeLeft.getMillisValue() != Playable.INVALID_TIME.toLong()) {
            setSleepTimer(timeLeft.getDisplayValue() + extendTime)
        }
    }

    fun setSleepTimer(time: Long) {
        playbackService?.setSleepTimer(time)
    }

    fun seekTo(time: Int) {
        val playable = getMedia()
        val service = playbackService
        if (service != null) {
            if (playable != null) {
                val timestamp = playable.getLastPlayedTimeStatistics()
                PlayableUtils.saveCurrentPosition(playable, time, timestamp)
            }
            service.seekTo(time)
        } else if (playable is FeedMedia) {
            playable.setPosition(time)
            DBWriter.setFeedItem(playable.getItem()!!, false)
            EventBus.getDefault().post(PlaybackPositionEvent(time, playable.getDuration()))
        }
    }

    fun setVideoSurface(holder: SurfaceHolder) {
        playbackService?.setVideoSurface(holder)
    }

    fun getStatus(): PlayerStatus {
        return status
    }

    fun setPlaybackSpeed(speed: Float) {
        val service = playbackService
        if (service != null) {
            service.setSpeed(speed)
        } else {
            EventBus.getDefault().post(SpeedChangedEvent(speed))
        }
    }

    fun setSkipSilence(skipSilence: Boolean) {
        playbackService?.setSkipSilence(skipSilence)
    }

    fun getCurrentPlaybackSpeedMultiplier(): Float {
        val service = playbackService
        return if (service != null) {
            service.getCurrentPlaybackSpeed()
        } else {
            PlaybackSpeedUtils.getCurrentPlaybackSpeed(getMedia())
        }
    }

    fun getCurrentPlaybackSkipSilence(): Boolean {
        val service = playbackService
        return if (service != null) {
            service.getCurrentSkipSilence()
        } else {
            PlaybackSpeedUtils.getCurrentSkipSilencePreference(getMedia()) == FeedPreferences.SkipSilence.AGGRESSIVE
        }
    }

    fun getAudioTracks(): List<String> {
        val service = playbackService ?: return emptyList()
        return service.getAudioTracks()
    }

    fun getSelectedAudioTrack(): Int {
        val service = playbackService ?: return -1
        return service.getSelectedAudioTrack()
    }

    fun setAudioTrack(track: Int) {
        playbackService?.setAudioTrack(track)
    }

    fun isPlayingVideoLocally(): Boolean {
        if (PlaybackService.isCasting()) {
            return false
        } else if (playbackService != null) {
            return PlaybackService.getCurrentMediaType() == MediaType.VIDEO
        } else {
            return getMedia() != null && getMedia()!!.getMediaType() == MediaType.VIDEO
        }
    }

    fun getVideoSize(): Pair<Int, Int>? {
        val service = playbackService
        return if (service != null) {
            service.getVideoSize()
        } else {
            null
        }
    }

    fun notifyVideoSurfaceAbandoned() {
        playbackService?.notifyVideoSurfaceAbandoned()
    }

    fun isStreaming(): Boolean {
        val service = playbackService
        return service != null && service.isStreaming()
    }
}
