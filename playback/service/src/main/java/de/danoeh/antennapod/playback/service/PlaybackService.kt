package de.danoeh.antennapod.playback.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.UiModeManager
import android.bluetooth.BluetoothA2dp
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import android.service.quicksettings.TileService
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import android.util.Pair
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.ViewConfiguration
import android.webkit.URLUtil
import android.widget.Toast

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants

import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.PlayerErrorEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.playback.BufferUpdateEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent
import de.danoeh.antennapod.event.settings.SkipIntroEndingChangedEvent
import de.danoeh.antennapod.event.settings.SpeedPresetChangedEvent
import de.danoeh.antennapod.event.settings.VolumeAdaptionChangedEvent
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.model.playback.TimerValue
import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.playback.base.BuildConfig
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer
import de.danoeh.antennapod.playback.base.PlayerStatus
import de.danoeh.antennapod.playback.cast.CastPsmp
import de.danoeh.antennapod.playback.cast.CastStateListener
import de.danoeh.antennapod.playback.service.internal.ClockSleepTimer
import de.danoeh.antennapod.playback.service.internal.EpisodeSleepTimer
import de.danoeh.antennapod.playback.service.internal.LocalPSMP
import de.danoeh.antennapod.playback.service.internal.PlayableUtils
import de.danoeh.antennapod.playback.service.internal.PlaybackServiceNotificationBuilder
import de.danoeh.antennapod.playback.service.internal.PlaybackServiceStateManager
import de.danoeh.antennapod.playback.service.internal.PlaybackServiceTaskManager
import de.danoeh.antennapod.playback.service.internal.PlaybackVolumeUpdater
import de.danoeh.antennapod.playback.service.internal.SleepTimer
import de.danoeh.antennapod.playback.service.internal.WearMediaSession
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences
import de.danoeh.antennapod.storage.preferences.SleepTimerType
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.appstartintent.VideoPlayerActivityStarter
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.ui.notifications.NotificationUtils
import de.danoeh.antennapod.ui.widget.WidgetUpdater
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.Calendar
import java.util.Collections
import java.util.GregorianCalendar
import java.util.concurrent.TimeUnit

/**
 * Controls the MediaPlayer that plays a FeedMedia-file
 */
class PlaybackService : MediaBrowserServiceCompat() {
    companion object {
        /**
         * Logging tag
         */
        private const val TAG = "PlaybackService"

        const val ACTION_PLAYER_STATUS_CHANGED = "action.de.danoeh.antennapod.core.service.playerStatusChanged"
        private const val AVRCP_ACTION_PLAYER_STATUS_CHANGED = "com.android.music.playstatechanged"
        private const val AVRCP_ACTION_META_CHANGED = "com.android.music.metachanged"

        /**
         * Custom actions used by Android Wear, Android Auto, and Android (API 33+ only)
         */
        private const val CUSTOM_ACTION_SKIP_TO_NEXT = "action.de.danoeh.antennapod.core.service.skipToNext"
        private const val CUSTOM_ACTION_FAST_FORWARD = "action.de.danoeh.antennapod.core.service.fastForward"
        private const val CUSTOM_ACTION_REWIND = "action.de.danoeh.antennapod.core.service.rewind"
        private const val CUSTOM_ACTION_CHANGE_PLAYBACK_SPEED =
                "action.de.danoeh.antennapod.core.service.changePlaybackSpeed"
        private const val CUSTOM_ACTION_TOGGLE_SLEEP_TIMER =
                "action.de.danoeh.antennapod.core.service.toggleSleepTimer"
        const val CUSTOM_ACTION_NEXT_CHAPTER = "action.de.danoeh.antennapod.core.service.next_chapter"

        /**
         * Set a max number of episodes to load for Android Auto, otherwise there could be performance issues
         */
        const val MAX_ANDROID_AUTO_EPISODES_PER_FEED = 100

        /**
         * Is true if service is running.
         */
        @JvmField
        var isRunning = false
        /**
         * Is true if the service was running, but paused due to headphone disconnect
         */
        private var transientPause = false
        /**
         * Is true if a Cast Device is connected to the service.
         */
        @Volatile
        private var isCasting = false

        /**
         * Used for Lollipop notifications, Android Wear, and Android Auto.
         */
        @Volatile
        private var currentMediaType: MediaType = MediaType.UNKNOWN

        /**
         * Returns an intent which starts an audio- or videoplayer, depending on the
         * type of media that is being played. If the playbackservice is not
         * running, the type of the last played media will be looked up.
         */
        @JvmStatic
        fun getPlayerActivityIntent(context: Context): Intent {
            val showVideoPlayer: Boolean

            if (isRunning) {
                showVideoPlayer = currentMediaType == MediaType.VIDEO && !isCasting
            } else {
                showVideoPlayer = PlaybackPreferences.getCurrentEpisodeIsVideo()
            }

            if (showVideoPlayer) {
                return VideoPlayerActivityStarter(context).getIntent()
            } else {
                return MainActivityStarter(context).withClearBackStack().withOpenPlayer().getIntent()
            }
        }

        /**
         * Same as [getPlayerActivityIntent], but here the type of activity
         * depends on the FeedMedia that is provided as an argument.
         */
        @JvmStatic
        fun getPlayerActivityIntent(context: Context, media: Playable): Intent {
            if (media.getMediaType() == MediaType.VIDEO && !isCasting) {
                return VideoPlayerActivityStarter(context).getIntent()
            } else {
                return MainActivityStarter(context).withClearBackStack().withOpenPlayer().getIntent()
            }
        }

        @JvmStatic
        fun getCurrentMediaType(): MediaType {
            return currentMediaType
        }

        @JvmStatic
        fun isCasting(): Boolean {
            return isCasting
        }
    }

    private var mediaPlayer: PlaybackServiceMediaPlayer? = null
    private lateinit var taskManager: PlaybackServiceTaskManager
    private var sleepTimer: SleepTimer? = null
    private lateinit var stateManager: PlaybackServiceStateManager
    private var positionEventTimer: Disposable? = null
    private lateinit var notificationBuilder: PlaybackServiceNotificationBuilder
    private var castStateListener: CastStateListener? = null
    private val singleShotDisposables = CompositeDisposable()

    private var autoSkippedFeedMediaId: String? = null
    private var positionJustResetAfterPlayback: String? = null
    private var clickCount = 0
    private val clickHandler = Handler(Looper.getMainLooper())

    /**
     * Used for Lollipop notifications, Android Wear, and Android Auto.
     */
    private var mediaSession: MediaSessionCompat? = null

    private var androidAutoConnectionState: LiveData<Int>? = null
    private var androidAutoConnected = false
    private var androidAutoConnectionObserver: Observer<Int>? = null

    private val mBinder: IBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService {
            return this@PlaybackService
        }
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.d(TAG, "Received onUnbind event")
        return super.onUnbind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created.")
        if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
            throw IllegalStateException("Media3PlaybackService should be used instead of PlaybackService")
        }
        isRunning = true

        stateManager = PlaybackServiceStateManager(this)
        notificationBuilder = PlaybackServiceNotificationBuilder(this)
        androidAutoConnectionState = CarConnection(this).getType()
        androidAutoConnectionObserver = { connectionState ->
            androidAutoConnected = connectionState == CarConnection.CONNECTION_TYPE_PROJECTION
        }
        androidAutoConnectionState!!.observeForever(androidAutoConnectionObserver!!)

        ContextCompat.registerReceiver(this, shutdownReceiver,
                IntentFilter(PlaybackServiceInterface.ACTION_SHUTDOWN_PLAYBACK_SERVICE),
                ContextCompat.RECEIVER_NOT_EXPORTED)
        registerReceiver(headsetDisconnected, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        registerReceiver(bluetoothStateUpdated, IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED))
        registerReceiver(audioBecomingNoisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        EventBus.getDefault().register(this)
        taskManager = PlaybackServiceTaskManager(this, taskManagerCallback)

        recreateMediaSessionIfNeeded()
        castStateListener = object : CastStateListener(this) {
            override fun onSessionStartedOrEnded() {
                recreateMediaPlayer()
            }
        }
        EventBus.getDefault().post(PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_STARTED))
    }

    fun recreateMediaSessionIfNeeded() {
        if (mediaSession != null) {
            // Media session was not destroyed, so we can re-use it.
            if (!mediaSession!!.isActive()) {
                mediaSession!!.setActive(true)
            }
            return
        }
        val eventReceiver = ComponentName(getApplicationContext(), MediaButtonReceiver::class.java)
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.setComponent(eventReceiver)
        val buttonReceiverIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))

        mediaSession = MediaSessionCompat(getApplicationContext(), TAG, eventReceiver, buttonReceiverIntent)
        mediaSession!!.setCallback(sessionCallback)
        mediaSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        recreateMediaPlayer()
        mediaSession!!.setActive(true)
        setSessionToken(mediaSession!!.getSessionToken())
    }

    fun recreateMediaPlayer() {
        var media: Playable? = null
        var wasPlaying = false
        if (mediaPlayer != null) {
            media = mediaPlayer!!.getPlayable()
            wasPlaying = mediaPlayer!!.getPlayerStatus() == PlayerStatus.PLAYING
            mediaPlayer!!.pause(true, false)
            mediaPlayer!!.shutdown()
        }
        mediaPlayer = CastPsmp.getInstanceIfConnected(this, mediaPlayerCallback)
        if (mediaPlayer == null) {
            mediaPlayer = LocalPSMP(this, mediaPlayerCallback) // Cast not supported or not connected
        }
        if (media != null) {
            mediaPlayer!!.playMediaObject(media, !media.localFileAvailable(), wasPlaying, true)
        }
        isCasting = mediaPlayer!!.isCasting()
        updateMediaSession(mediaPlayer!!.getPlayerStatus())
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service is about to be destroyed")
        disableSleepTimer()

        if (notificationBuilder.getPlayerStatus() == PlayerStatus.PLAYING) {
            notificationBuilder.setPlayerStatus(PlayerStatus.STOPPED)
            val notificationManager = NotificationManagerCompat.from(this)
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(R.id.notification_playing, notificationBuilder.build())
            }
        }
        singleShotDisposables.clear()
        WidgetUpdater.updateWidget(getApplicationContext(), WidgetUpdater.WidgetState(getPlayable(), getStatus(),
                    getCurrentPosition(), getDuration(), getCurrentPlaybackSpeed()))
        stateManager.stopForeground(!UserPreferences.isPersistNotify())
        isRunning = false
        currentMediaType = MediaType.UNKNOWN
        castStateListener!!.destroy()

        androidAutoConnectionState!!.removeObserver(androidAutoConnectionObserver!!)
        cancelPositionObserver()
        if (mediaSession != null) {
            mediaSession!!.release()
            mediaSession = null
        }
        unregisterReceiver(headsetDisconnected)
        unregisterReceiver(shutdownReceiver)
        unregisterReceiver(bluetoothStateUpdated)
        unregisterReceiver(audioBecomingNoisy)
        mediaPlayer!!.shutdown()
        taskManager.shutdown()
        disableSleepTimer()
        EventBus.getDefault().unregister(this)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot? {
        Log.d(TAG, "OnGetRoot: clientPackageName=" + clientPackageName +
                "; clientUid=" + clientUid + " ; rootHints=" + rootHints)
        if (rootHints != null && rootHints.getBoolean(MediaBrowserServiceCompat.BrowserRoot.EXTRA_RECENT)) {
            val extras = Bundle()
            extras.putBoolean(MediaBrowserServiceCompat.BrowserRoot.EXTRA_RECENT, true)
            Log.d(TAG, "OnGetRoot: Returning BrowserRoot " + R.string.current_playing_episode)
            return MediaBrowserServiceCompat.BrowserRoot(getResources().getString(R.string.current_playing_episode), extras)
        }

        // Name visible in Android Auto
        return MediaBrowserServiceCompat.BrowserRoot(getResources().getString(R.string.app_name), null)
    }

    private fun loadQueueForMediaSession() {
        val d = Single.create<List<MediaSessionCompat.QueueItem>> { emitter ->
            val queueItems: MutableList<MediaSessionCompat.QueueItem> = ArrayList()
            for (feedItem in DBReader.getQueue()) {
                if (feedItem.getMedia() != null) {
                    val mediaDescription = feedItem.getMedia()!!.getMediaItem()!!.getDescription()
                    queueItems.add(MediaSessionCompat.QueueItem(mediaDescription, feedItem.getId()))
                }
            }
            emitter.onSuccess(queueItems)
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ queueItems -> mediaSession!!.setQueue(queueItems) }, { it.printStackTrace() })
        singleShotDisposables.add(d)
    }

    private fun createBrowsableMediaItem(
            @StringRes title: Int, @DrawableRes icon: Int, numEpisodes: Int, grid: Boolean): MediaBrowserCompat.MediaItem {
        val uri = Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(getResources().getResourcePackageName(icon))
                .appendPath(getResources().getResourceTypeName(icon))
                .appendPath(getResources().getResourceEntryName(icon))
                .build()

        val extras = Bundle()
        if (grid) {
            extras.putInt(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
            extras.putInt(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
        }
        val description = MediaDescriptionCompat.Builder()
                .setIconUri(uri)
                .setMediaId(getResources().getString(title))
                .setTitle(getResources().getString(title))
                .setSubtitle(getResources().getQuantityString(R.plurals.num_episodes, numEpisodes, numEpisodes))
                .setExtras(extras)
                .build()
        return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createBrowsableMediaItemForFeed(feed: Feed): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
                .setMediaId("FeedId:" + feed.getId())
                .setTitle(feed.getTitle())
                .setDescription(feed.getDescription())
        if (feed.getImageUrl() != null) {
            builder.setIconUri(Uri.parse(feed.getImageUrl()))
        }
        if (feed.getLink() != null) {
            builder.setMediaUri(Uri.parse(feed.getLink()))
        }
        val description = builder.build()
        return MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    override fun onLoadChildren(parentId: String,
                                result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        Log.d(TAG, "OnLoadChildren: parentMediaId=" + parentId)
        result.detach()

        val d = Completable.create { emitter ->
            result.sendResult(loadChildrenSynchronous(parentId))
            emitter.onComplete()
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { },
                    { e ->
                        e.printStackTrace()
                        result.sendResult(null)
                    })
        singleShotDisposables.add(d)
    }

    private fun loadChildrenSynchronous(parentId: String): MutableList<MediaBrowserCompat.MediaItem>? {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        if (parentId == getResources().getString(R.string.app_name)) {
            val playable = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
            if (playable != null) {
                mediaItems.add(createBrowsableMediaItem(R.string.current_playing_episode, R.drawable.ic_play_48dp_black, 1, false))
            }
            mediaItems.add(createBrowsableMediaItem(R.string.queue_label, R.drawable.ic_playlist_play_black,
                    DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.QUEUED)), false))
            mediaItems.add(createBrowsableMediaItem(R.string.downloads_label, R.drawable.ic_download_black,
                    DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.DOWNLOADED)), false))
            mediaItems.add(createBrowsableMediaItem(R.string.episodes_label, R.drawable.ic_feed_black,
                    DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.UNPLAYED)), false))
            mediaItems.add(createBrowsableMediaItem(R.string.subscriptions_label, R.drawable.ic_subscriptions_black,
                    DBReader.getFeedList().size, true))
            return mediaItems
        }

        if (parentId == getResources().getString(R.string.subscriptions_label)) {
            val feeds = DBReader.getFeedList()
            for (feed in feeds) {
                if (feed.getState() == Feed.STATE_SUBSCRIBED) {
                    mediaItems.add(createBrowsableMediaItemForFeed(feed))
                }
            }
            return mediaItems
        }

        val feedItems: List<FeedItem?>
        if (parentId == getResources().getString(R.string.queue_label)) {
            feedItems = DBReader.getQueue()
        } else if (parentId == getResources().getString(R.string.downloads_label)) {
            feedItems = DBReader.getEpisodes(0, MAX_ANDROID_AUTO_EPISODES_PER_FEED,
                    FeedItemFilter(FeedItemFilter.DOWNLOADED), UserPreferences.getDownloadsSortedOrder())
        } else if (parentId == getResources().getString(R.string.episodes_label)) {
            feedItems = DBReader.getEpisodes(0, MAX_ANDROID_AUTO_EPISODES_PER_FEED,
                    FeedItemFilter(UserPreferences.getPrefFilterAllEpisodes()),
                    UserPreferences.getAllEpisodesSortOrder())
        } else if (parentId.startsWith("FeedId:")) {
            val feedId = java.lang.Long.parseLong(parentId.split(":")[1])
            feedItems = DBReader.getFeed(feedId, true, 0, MAX_ANDROID_AUTO_EPISODES_PER_FEED)!!.getItems()!!
        } else if (parentId == getString(R.string.current_playing_episode)) {
            val playable = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
            if (playable != null) {
                feedItems = Collections.singletonList(playable.getItem())
            } else {
                return null
            }
        } else {
            Log.e(TAG, "Parent ID not found: " + parentId)
            return null
        }
        var count = 0
        for (feedItem in feedItems) {
            if (feedItem!!.getMedia() != null && feedItem.getMedia()!!.getMediaItem() != null) {
                mediaItems.add(feedItem.getMedia()!!.getMediaItem()!!)
                count += 1
                if (count >= MAX_ANDROID_AUTO_EPISODES_PER_FEED) {
                    break
                }
            }
        }
        return mediaItems
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "Received onBind event")
        if (intent.getAction() != null && TextUtils.equals(intent.getAction(), MediaBrowserServiceCompat.SERVICE_INTERFACE)) {
            return super.onBind(intent)
        } else {
            return mBinder
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "OnStartCommand called")

        stateManager.startForeground(R.id.notification_playing, notificationBuilder.build())
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.cancel(R.id.notification_streaming_confirmation)

        if ((flags and Service.START_FLAG_REDELIVERY) != 0 || intent == null) {
            Log.d(TAG, "onStartCommand is a redelivered intent, calling stopForeground now.")
            stateManager.stopForeground(true)
            return Service.START_NOT_STICKY
        }

        val keycode = intent.getIntExtra(MediaButtonReceiver.EXTRA_KEYCODE, -1)
        val customAction = intent.getStringExtra(MediaButtonReceiver.EXTRA_CUSTOM_ACTION)
        val hardwareButton = intent.getBooleanExtra(MediaButtonReceiver.EXTRA_HARDWAREBUTTON, false)
        val playable = intent.getParcelableExtra<Playable>(PlaybackServiceInterface.EXTRA_PLAYABLE)
        if (keycode == -1 && playable == null && customAction == null) {
            Log.e(TAG, "PlaybackService was started with no arguments")
            stateManager.stopService()
            return Service.START_NOT_STICKY
        }

        if (keycode != -1) {
            val notificationButton: Boolean
            if (hardwareButton) {
                Log.d(TAG, "Received hardware button event")
                notificationButton = false
            } else {
                Log.d(TAG, "Received media button event")
                notificationButton = true
            }
            val handled = handleKeycode(keycode, notificationButton)
            if (!handled && !stateManager.hasReceivedValidStartCommand()) {
                stateManager.stopService()
                return Service.START_NOT_STICKY
            }
        } else if (playable != null) {
            stateManager.validStartCommandWasReceived()
            val allowStreamThisTime = intent.getBooleanExtra(
                    PlaybackServiceInterface.EXTRA_ALLOW_STREAM_THIS_TIME, false)
            val allowStreamAlways = intent.getBooleanExtra(
                    PlaybackServiceInterface.EXTRA_ALLOW_STREAM_ALWAYS, false)
            sendNotificationBroadcast(PlaybackServiceInterface.NOTIFICATION_TYPE_RELOAD, 0)
            if (allowStreamAlways) {
                UserPreferences.setAllowMobileStreaming(true)
            }
            val d = Observable.fromCallable<Playable>(
                    {
                        if (playable is FeedMedia) {
                            return@fromCallable DBReader.getFeedMedia(playable.getId())!!
                        } else {
                            return@fromCallable playable
                        }
                    })
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { loadedPlayable -> startPlaying(loadedPlayable, allowStreamThisTime) },
                            { error ->
                                Log.d(TAG, "Playable was not found. Stopping service.")
                                error.printStackTrace()
                                stateManager.stopService()
                            })
            singleShotDisposables.add(d)
        } else {
            mediaSession!!.getController().getTransportControls().sendCustomAction(customAction, null)
        }

        return Service.START_NOT_STICKY
    }

    private fun skipIntro(playable: Playable) {
        if (playable !is FeedMedia) {
            return
        }

        val preferences = playable.getItem()!!.getFeed()!!.getPreferences()!!
        val skipIntro = preferences.getFeedSkipIntro()

        val context = getApplicationContext()
        if (skipIntro > 0 && playable.getPosition() < skipIntro * 1000) {
            val duration = getDuration()
            if (skipIntro * 1000 < duration || duration <= 0) {
                Log.d(TAG, "skipIntro " + playable.getEpisodeTitle())
                mediaPlayer!!.seekTo(skipIntro * 1000)
                val skipIntroMesg = context.getResources().getQuantityString(
                        R.plurals.pref_feed_skip_intro_snackbar, skipIntro, skipIntro)
                val toast = Toast.makeText(context, skipIntroMesg,
                        Toast.LENGTH_LONG)
                toast.show()
            }
        }
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun displayStreamingNotAllowedNotification(originalIntent: Intent) {
        if (EventBus.getDefault().hasSubscriberForEvent(MessageEvent::class.java)) {
            EventBus.getDefault().post(MessageEvent(
                    getString(R.string.confirm_mobile_streaming_notification_message)))
            return
        }

        val intentAllowThisTime = Intent(originalIntent)
        intentAllowThisTime.setAction(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_THIS_TIME)
        intentAllowThisTime.putExtra(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_THIS_TIME, true)
        val pendingIntentAllowThisTime: PendingIntent
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            pendingIntentAllowThisTime = PendingIntent.getForegroundService(this,
                    R.id.pending_intent_allow_stream_this_time, intentAllowThisTime,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            pendingIntentAllowThisTime = PendingIntent.getService(this,
                    R.id.pending_intent_allow_stream_this_time, intentAllowThisTime,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val intentAlwaysAllow = Intent(intentAllowThisTime)
        intentAlwaysAllow.setAction(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_ALWAYS)
        intentAlwaysAllow.putExtra(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_ALWAYS, true)
        val pendingIntentAlwaysAllow: PendingIntent
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            pendingIntentAlwaysAllow = PendingIntent.getForegroundService(this,
                    R.id.pending_intent_allow_stream_always, intentAlwaysAllow,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            pendingIntentAlwaysAllow = PendingIntent.getService(this,
                    R.id.pending_intent_allow_stream_always, intentAlwaysAllow,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(this,
                NotificationUtils.CHANNEL_ID_USER_ACTION)
                .setSmallIcon(R.drawable.ic_notification_stream)
                .setContentTitle(getString(R.string.confirm_mobile_streaming_notification_title))
                .setContentText(getString(R.string.confirm_mobile_streaming_notification_message))
                .setStyle(NotificationCompat.BigTextStyle()
                        .bigText(getString(R.string.confirm_mobile_streaming_notification_message)))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntentAllowThisTime)
                .addAction(R.drawable.ic_notification_stream,
                        getString(R.string.confirm_mobile_streaming_button_once),
                        pendingIntentAllowThisTime)
                .addAction(R.drawable.ic_notification_stream,
                        getString(R.string.confirm_mobile_streaming_button_always),
                        pendingIntentAlwaysAllow)
                .setAutoCancel(true)
        val notificationManager = NotificationManagerCompat.from(this)
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(R.id.notification_streaming_confirmation, builder.build())
        } else {
            Toast.makeText(getApplicationContext(),
                    R.string.confirm_mobile_streaming_notification_message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Handles media button events
     * return: keycode was handled
     */
    private fun handleKeycode(keycode: Int, notificationButton: Boolean): Boolean {
        Log.d(TAG, "Handling keycode: " + keycode)
        val info = mediaPlayer!!.getPSMPInfo()
        val status = info.getPlayerStatus()
        when (keycode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (status == PlayerStatus.PLAYING) {
                    mediaPlayer!!.pause(!UserPreferences.isPersistNotify(), false)
                } else if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
                    mediaPlayer!!.resume()
                } else if (status == PlayerStatus.PREPARING) {
                    mediaPlayer!!.setStartWhenPrepared(!mediaPlayer!!.isStartWhenPrepared())
                } else if (status == PlayerStatus.INITIALIZED) {
                    mediaPlayer!!.setStartWhenPrepared(true)
                    mediaPlayer!!.prepare()
                } else if (mediaPlayer!!.getPlayable() == null) {
                    startPlayingFromPreferences()
                } else {
                    return false
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
                    mediaPlayer!!.resume()
                } else if (status == PlayerStatus.INITIALIZED) {
                    mediaPlayer!!.setStartWhenPrepared(true)
                    mediaPlayer!!.prepare()
                } else if (mediaPlayer!!.getPlayable() == null) {
                    startPlayingFromPreferences()
                } else {
                    return false
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (status == PlayerStatus.PLAYING) {
                    mediaPlayer!!.pause(!UserPreferences.isPersistNotify(), false)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (!notificationButton) {
                    // Handle remapped button as notification button which is not remapped again.
                    return handleKeycode(UserPreferences.getHardwareForwardButton(), true)
                } else if (getStatus() == PlayerStatus.PLAYING || getStatus() == PlayerStatus.PAUSED) {
                    mediaPlayer!!.skip()
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (getStatus() == PlayerStatus.PLAYING || getStatus() == PlayerStatus.PAUSED) {
                    mediaPlayer!!.seekDelta(UserPreferences.getFastForwardSecs() * 1000)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (!notificationButton) {
                    // Handle remapped button as notification button which is not remapped again.
                    return handleKeycode(UserPreferences.getHardwarePreviousButton(), true)
                } else if (getStatus() == PlayerStatus.PLAYING || getStatus() == PlayerStatus.PAUSED) {
                    mediaPlayer!!.seekTo(0)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (getStatus() == PlayerStatus.PLAYING || getStatus() == PlayerStatus.PAUSED) {
                    mediaPlayer!!.seekDelta(-UserPreferences.getRewindSecs() * 1000)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (status == PlayerStatus.PLAYING) {
                    mediaPlayer!!.pause(true, true)
                }

                stateManager.stopForeground(true) // gets rid of persistent notification
                return true
            }
            else -> {
                Log.d(TAG, "Unhandled key code: " + keycode)
                // only notify the user about an unknown key event if it is actually doing something
                if (info.getPlayable() != null && info.getPlayerStatus() == PlayerStatus.PLAYING) {
                    val message = String.format(getResources().getString(R.string.unknown_media_key), keycode)
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return false
    }

    private fun startPlayingFromPreferences() {
        val d = Observable.fromCallable<FeedMedia> { DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())!! }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { playable -> startPlaying(playable, false) },
                        { error ->
                            Log.d(TAG, "Playable was not loaded from preferences. Stopping service.")
                            error.printStackTrace()
                            stateManager.stopService()
                        })
        singleShotDisposables.add(d)
    }

    private fun startPlaying(playable: Playable, allowStreamThisTime: Boolean) {
        val localFeed = URLUtil.isContentUrl(playable.getStreamUrl())
        val stream = !playable.localFileAvailable() || localFeed
        if (stream && !localFeed && !NetworkUtils.isStreamingAllowed() && !allowStreamThisTime) {
            displayStreamingNotAllowedNotification(
                    PlaybackServiceStarter(this, playable)
                            .getIntent())
            PlaybackPreferences.writeNoMediaPlaying()
            stateManager.stopService()
            return
        }

        if (playable.getIdentifier() != PlaybackPreferences.getCurrentlyPlayingFeedMediaId()) {
            PlaybackPreferences.clearCurrentlyPlayingTemporaryPlaybackSettings()
        }

        mediaPlayer!!.playMediaObject(playable, stream, true, true)
        stateManager.validStartCommandWasReceived()
        stateManager.startForeground(R.id.notification_playing, notificationBuilder.build())
        recreateMediaSessionIfNeeded()
        updateNotificationAndMediaSession(playable)
        addPlayableToQueue(playable)
    }

    /**
     * Called by a mediaplayer Activity as soon as it has prepared its
     * mediaplayer.
     */
    fun setVideoSurface(sh: SurfaceHolder) {
        Log.d(TAG, "Setting display")
        mediaPlayer!!.setVideoSurface(sh)
    }

    fun notifyVideoSurfaceAbandoned() {
        mediaPlayer!!.pause(true, false)
        mediaPlayer!!.resetVideoSurface()
        updateNotificationAndMediaSession(getPlayable())
        stateManager.stopForeground(!UserPreferences.isPersistNotify())
    }

    private val taskManagerCallback: PlaybackServiceTaskManager.PSTMCallback = object : PlaybackServiceTaskManager.PSTMCallback {
        override fun positionSaverTick() {
            saveCurrentPosition(true, null, Playable.INVALID_TIME)
        }

        override fun requestWidgetState(): WidgetUpdater.WidgetState {
            return WidgetUpdater.WidgetState(getPlayable(), getStatus(),
                    getCurrentPosition(), getDuration(), getCurrentPlaybackSpeed())
        }

        override fun onChapterLoaded(media: Playable) {
            sendNotificationBroadcast(PlaybackServiceInterface.NOTIFICATION_TYPE_RELOAD, 0)
            updateMediaSession(mediaPlayer!!.getPlayerStatus())
        }
    }

    private val mediaPlayerCallback: PlaybackServiceMediaPlayer.PSMPCallback = object : PlaybackServiceMediaPlayer.PSMPCallback {
        override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
            if (mediaPlayer != null) {
                currentMediaType = mediaPlayer!!.getCurrentMediaType()!!
            } else {
                currentMediaType = MediaType.UNKNOWN
            }

            updateMediaSession(newInfo.getPlayerStatus())
            when (newInfo.getPlayerStatus()) {
                PlayerStatus.INITIALIZED -> {
                    if (mediaPlayer!!.getPSMPInfo().getPlayable() != null) {
                        PlaybackPreferences.writeMediaPlaying(mediaPlayer!!.getPSMPInfo().getPlayable())
                    }
                    updateNotificationAndMediaSession(newInfo.getPlayable())
                }
                PlayerStatus.PREPARED -> {
                    if (mediaPlayer!!.getPSMPInfo().getPlayable() != null) {
                        PlaybackPreferences.writeMediaPlaying(mediaPlayer!!.getPSMPInfo().getPlayable())
                    }
                    taskManager.startChapterLoader(newInfo.getPlayable()!!)
                }
                PlayerStatus.PAUSED -> {
                    updateNotificationAndMediaSession(newInfo.getPlayable())
                    PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PAUSED)
                    if (!isCasting) {
                        stateManager.stopForeground(!UserPreferences.isPersistNotify())
                    }
                    cancelPositionObserver()
                }
                PlayerStatus.STOPPED -> {
                    //writePlaybackPreferencesNoMediaPlaying();
                    //stopService();
                }
                PlayerStatus.PLAYING -> {
                    PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PLAYING)
                    saveCurrentPosition(true, null, Playable.INVALID_TIME)
                    recreateMediaSessionIfNeeded()
                    updateNotificationAndMediaSession(newInfo.getPlayable())
                    setupPositionObserver()
                    stateManager.validStartCommandWasReceived()
                    stateManager.startForeground(R.id.notification_playing, notificationBuilder.build())
                    // set sleep timer if auto-enabled
                    var autoEnableByTime = true
                    val fromSetting = SleepTimerPreferences.autoEnableFrom()
                    val toSetting = SleepTimerPreferences.autoEnableTo()
                    if (fromSetting != toSetting) {
                        val now = GregorianCalendar()
                        now.setTimeInMillis(System.currentTimeMillis())
                        val currentHour = now.get(Calendar.HOUR_OF_DAY)
                        autoEnableByTime = SleepTimerPreferences.isInTimeRange(fromSetting, toSetting, currentHour)
                    }
                    if (androidAutoConnected) {
                        Log.i(TAG, "Android Auto is connected, sleep timer will not be auto-enabled")
                        autoEnableByTime = false
                    }

                    if (newInfo.getOldPlayerStatus() != null && newInfo.getOldPlayerStatus() != PlayerStatus.SEEKING
                            && SleepTimerPreferences.autoEnable() && autoEnableByTime && !sleepTimerActive()) {
                        setSleepTimer(SleepTimerPreferences.timerMillisOrEpisodes())
                        EventBus.getDefault().post(MessageEvent(getString(R.string.sleep_timer_enabled_label),
                                { disableSleepTimer() }, getString(R.string.undo)))
                    }
                    loadQueueForMediaSession()
                    positionJustResetAfterPlayback = null
                }
                PlayerStatus.ERROR -> {
                    PlaybackPreferences.writeNoMediaPlaying()
                    stateManager.stopService()
                }
                else -> Unit
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    TileService.requestListeningState(getApplicationContext(),
                            ComponentName(getApplicationContext(), QuickSettingsTileService::class.java))
                } catch (e: IllegalArgumentException) {
                    Log.d(TAG, "Skipping quick settings tile setup")
                }
            }

            IntentUtils.sendLocalBroadcast(getApplicationContext(), ACTION_PLAYER_STATUS_CHANGED)
            bluetoothNotifyChange(newInfo, AVRCP_ACTION_PLAYER_STATUS_CHANGED)
            bluetoothNotifyChange(newInfo, AVRCP_ACTION_META_CHANGED)
            taskManager.requestWidgetUpdate()
            EventBus.getDefault().post(PlayerStatusEvent())
        }

        override fun shouldStop() {
            stateManager.stopForeground(!UserPreferences.isPersistNotify())
        }

        override fun onMediaChanged(reloadUI: Boolean) {
            Log.d(TAG, "reloadUI callback reached")
            if (reloadUI) {
                sendNotificationBroadcast(PlaybackServiceInterface.NOTIFICATION_TYPE_RELOAD, 0)
            }
            updateNotificationAndMediaSession(getPlayable())
        }

        override fun onPostPlayback(media: Playable, ended: Boolean, skipped: Boolean,
                                    playingNext: Boolean) {
            this@PlaybackService.onPostPlayback(media, ended, skipped, playingNext)
        }

        override fun onPlaybackStart(playable: Playable, position: Int) {
            taskManager.startWidgetUpdater()
            if (position != Playable.INVALID_TIME) {
                playable.setPosition(position)
            } else {
                skipIntro(playable)
            }
            playable.onPlaybackStart()
            taskManager.startPositionSaver()
        }

        override fun onPlaybackPause(playable: Playable?, position: Int) {
            taskManager.cancelPositionSaver()
            cancelPositionObserver()
            taskManager.cancelWidgetUpdater()
            if (playable is FeedMedia) {
                if (playable.getItem()!!.getIdentifyingValue() != positionJustResetAfterPlayback) {
                    // Don't store position after position is already reset
                    saveCurrentPosition(position == Playable.INVALID_TIME, playable, position)
                }
                SynchronizationQueue.getInstance()!!.enqueueEpisodePlayed(playable, false)
            }
        }

        override fun getNextInQueue(currentMedia: Playable?): Playable? {
            return this@PlaybackService.getNextInQueue(currentMedia)
        }

        override fun shouldContinueToNextEpisode(): Boolean {
            return this@PlaybackService.shouldContinueToNextEpisode()
        }

        override fun episodeFinishedPlayback() {
            this@PlaybackService.episodeFinishedPlayback()
        }

        override fun findMedia(url: String): Playable? {
            val item = DBReader.getFeedItemByGuidOrEpisodeUrl(null, url)
            return if (item != null) item.getMedia() else null
        }

        override fun onPlaybackEnded(mediaType: MediaType?, stopPlaying: Boolean) {
            this@PlaybackService.onPlaybackEnded(mediaType, stopPlaying)
        }

        override fun ensureMediaInfoLoaded(media: Playable) {
            if (media is FeedMedia && media.getItem() == null) {
                media.setItem(DBReader.getFeedItem(media.getItemId()))
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun playerError(event: PlayerErrorEvent) {
        if (mediaPlayer!!.getPlayerStatus() == PlayerStatus.PLAYING) {
            mediaPlayer!!.pause(true, false)
        }
        stateManager.stopService()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun bufferUpdate(event: BufferUpdateEvent) {
        if (event.hasEnded()) {
            val playable = getPlayable()
            if (playable is FeedMedia
                    && playable.getDuration() <= 0 && mediaPlayer!!.getDuration() > 0) {
                // Playable is being streamed and does not have a duration specified in the feed
                playable.setDuration(mediaPlayer!!.getDuration())
                DBWriter.setFeedMedia(playable)
                updateNotificationAndMediaSession(playable)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun sleepTimerUpdate(event: SleepTimerUpdatedEvent) {
        if (event.isOver()) {
            updateMediaSession(mediaPlayer!!.getPlayerStatus())
            mediaPlayer!!.pause(true, true)
            mediaPlayer!!.setVolume(1.0f, 1.0f)
            var newPosition = mediaPlayer!!.getPosition() - (SleepTimer.NOTIFICATION_THRESHOLD / 2).toInt()
            newPosition = Math.max(newPosition, 0)
            seekTo(newPosition)
        } else if (event.getMillisTimeLeft() < SleepTimer.NOTIFICATION_THRESHOLD) {
            val multiplicators = floatArrayOf(0.1f, 0.2f, 0.3f, 0.3f, 0.3f, 0.4f, 0.4f, 0.4f, 0.6f, 0.8f)
            val multiplicator = multiplicators[Math.max(0, (event.getMillisTimeLeft() / 1000).toInt())]
            Log.d(TAG, "onSleepTimerAlmostExpired: " + multiplicator)
            mediaPlayer!!.setVolume(multiplicator, multiplicator)
        } else if (event.isCancelled()) {
            updateMediaSession(mediaPlayer!!.getPlayerStatus())
            mediaPlayer!!.setVolume(1.0f, 1.0f)
        } else if (event.wasJustEnabled()) {
            updateMediaSession(mediaPlayer!!.getPlayerStatus())
        }
    }

    private fun getNextInQueue(currentMedia: Playable?): Playable? {
        if (currentMedia !is FeedMedia) {
            Log.d(TAG, "getNextInQueue(), but playable not an instance of FeedMedia, so not proceeding")
            PlaybackPreferences.writeNoMediaPlaying()
            return null
        }
        Log.d(TAG, "getNextInQueue()")
        if (currentMedia.getItem() == null) {
            currentMedia.setItem(DBReader.getFeedItem(currentMedia.getItemId()))
        }
        val item = currentMedia.getItem()
        if (item == null) {
            Log.w(TAG, "getNextInQueue() with FeedMedia object whose FeedItem is null")
            PlaybackPreferences.writeNoMediaPlaying()
            return null
        }
        val nextItem = DBReader.getNextInQueue(item)

        if (nextItem == null || nextItem.getMedia() == null) {
            PlaybackPreferences.writeNoMediaPlaying()
            return null
        }

        // continue playback if user has enabled continuous playback
        // OR they enabled an episode sleep timer and there are still episodes left to play
        val continuousPlayback = UserPreferences.isFollowQueue() && shouldContinueToNextEpisode()

        if (!continuousPlayback) {
            Log.d(TAG, "getNextInQueue(), but follow queue is not enabled.")
            PlaybackPreferences.writeMediaPlaying(nextItem.getMedia())
            updateNotificationAndMediaSession(nextItem.getMedia())
            return null
        }

        if (!nextItem.getMedia()!!.localFileAvailable() && !NetworkUtils.isStreamingAllowed()
                && UserPreferences.isFollowQueue() && !nextItem.getFeed()!!.isLocalFeed()) {
            displayStreamingNotAllowedNotification(
                    PlaybackServiceStarter(this, nextItem.getMedia()!!)
                            .getIntent())
            PlaybackPreferences.writeNoMediaPlaying()
            stateManager.stopService()
            return null
        }
        return nextItem.getMedia()
    }

    private fun episodeFinishedPlayback() {
        if (sleepTimer != null) {
            sleepTimer!!.episodeFinishedPlayback()
        }
    }

    /**
     * Tells if we should continue to next episode
     * @return True if we can proceed to the next episode (might be blocked by other things), or not
     */
    private fun shouldContinueToNextEpisode(): Boolean {
        if (sleepTimer != null) {
            return sleepTimer!!.shouldContinueToNextEpisode()
        }
        return true // always allow when no sleep timer is active
    }

    /**
     * Set of instructions to be performed when playback ends.
     */
    private fun onPlaybackEnded(mediaType: MediaType?, stopPlaying: Boolean) {
        Log.d(TAG, "Playback ended")
        PlaybackPreferences.clearCurrentlyPlayingTemporaryPlaybackSettings()
        if (stopPlaying) {
            taskManager.cancelPositionSaver()
            cancelPositionObserver()
            disableSleepTimer()
            if (!isCasting) {
                stateManager.stopForeground(true)
                stateManager.stopService()
            }
        }
        if (mediaType == null) {
            EventBus.getDefault().post(PlayerStatusEvent())
            sendNotificationBroadcast(PlaybackServiceInterface.NOTIFICATION_TYPE_PLAYBACK_END, 0)
        } else {
            sendNotificationBroadcast(PlaybackServiceInterface.NOTIFICATION_TYPE_RELOAD,
                    if (isCasting) PlaybackServiceInterface.EXTRA_CODE_CAST
                    else if (mediaType == MediaType.VIDEO) PlaybackServiceInterface.EXTRA_CODE_VIDEO
                    else PlaybackServiceInterface.EXTRA_CODE_AUDIO)
        }
    }

    /**
     * This method processes the media object after its playback ended, either because it completed
     * or because a different media object was selected for playback.
     *
     *
     * Even though these tasks aren't supposed to be resource intensive, a good practice is to
     * usually call this method on a background thread.
     *
     * @param playable    the media object that was playing. It is assumed that its position
     * property was updated before this method was called.
     * @param ended       if true, it signals that {@param playable} was played until its end.
     * In such case, the position property of the media becomes irrelevant for
     * most of the tasks (although it's still a good practice to keep it
     * accurate).
     * @param skipped     if the user pressed a skip >| button.
     * @param playingNext if true, it means another media object is being loaded in place of this
     * one.
     * Instances when we'd set it to false would be when we're not following the
     * queue or when the queue has ended.
     */
    private fun onPostPlayback(playable: Playable, ended: Boolean, skipped: Boolean,
                               playingNext: Boolean) {
        Log.d(TAG, "onPostPlayback(): media=" + playable.getEpisodeTitle())

        if (playable !is FeedMedia) {
            Log.d(TAG, "Not doing post-playback processing: media not of type FeedMedia")
            return
        }
        val item = playable.getItem()
        val smartMarkAsPlayedSecs = UserPreferences.getSmartMarkAsPlayedSecs()
        val almostEnded = playable.getDuration() > 0
                && playable.getPosition() >= playable.getDuration() - smartMarkAsPlayedSecs * 1000
        if (!ended && almostEnded) {
            Log.d(TAG, "smart mark as played")
        }

        var autoSkipped = false
        if (autoSkippedFeedMediaId != null && autoSkippedFeedMediaId == item!!.getIdentifyingValue()) {
            autoSkippedFeedMediaId = null
            autoSkipped = true
        }

        SynchronizationQueue.getInstance()!!.enqueueEpisodePlayed(playable, ended || almostEnded)
        if (item != null) {
            if (ended || almostEnded
                    || autoSkipped
                    || (skipped && !UserPreferences.shouldSkipKeepEpisode())) {
                // only mark the item as played if we're not keeping it anyways
                positionJustResetAfterPlayback = item.getIdentifyingValue()
                DBWriter.markItemsPlayed(FeedItem.PLAYED, ended || (skipped && almostEnded),
                        listOf(item))
                // don't know if it actually matters to not autodownload when smart mark as played is triggered
                DBWriter.removeQueueItem(this@PlaybackService, ended, item)
                // Delete episode if enabled
                val action =
                        item.getFeed()!!.getPreferences()!!.getCurrentAutoDelete()
                val autoDeleteEnabledGlobally = UserPreferences.isAutoDelete()
                        && (!item.getFeed()!!.isLocalFeed() || UserPreferences.isAutoDeleteLocal())
                val shouldAutoDelete = action == FeedPreferences.AutoDeleteAction.ALWAYS
                        || (action == FeedPreferences.AutoDeleteAction.GLOBAL && autoDeleteEnabledGlobally)
                if (shouldAutoDelete && (!item.isTagged(FeedItem.TAG_FAVORITE)
                                || !UserPreferences.shouldFavoriteKeepEpisode())) {
                    DBWriter.deleteFeedMediaOfItem(this@PlaybackService, playable)
                    Log.d(TAG, "Episode Deleted")
                }
                notifyChildrenChanged(getString(R.string.queue_label))
            }
        }

        if (ended || skipped || playingNext) {
            DBWriter.addItemToPlaybackHistory(playable)
        }
    }

    fun setSleepTimer(waitingTime: Long) {
        Log.d(TAG, "Setting sleep timer to " + waitingTime + " milliseconds")
        if (waitingTime <= 0) {
            throw IllegalArgumentException("Waiting time <= 0")
        }

        Log.d(TAG, "Setting sleep timer to " + waitingTime + " milliseconds or episodes")
        if (sleepTimerActive()) {
            sleepTimer!!.updateRemainingTime(waitingTime)
        } else {
            sleepTimer = if (SleepTimerPreferences.getSleepTimerType() == SleepTimerType.CLOCK)
                ClockSleepTimer(getApplicationContext()) else EpisodeSleepTimer(getApplicationContext())
            sleepTimer!!.start(waitingTime)
        }
    }

    fun disableSleepTimer() {
        if (sleepTimer != null) {
            Log.d(TAG, "Disabling sleep timer")
            sleepTimer!!.stop()
        }
        sleepTimer = null
    }

    private fun sendNotificationBroadcast(type: Int, code: Int) {
        val intent = Intent(PlaybackServiceInterface.ACTION_PLAYER_NOTIFICATION)
        intent.putExtra(PlaybackServiceInterface.EXTRA_NOTIFICATION_TYPE, type)
        intent.putExtra(PlaybackServiceInterface.EXTRA_NOTIFICATION_CODE, code)
        intent.setPackage(getPackageName())
        sendBroadcast(intent)
    }

    private fun skipEndingIfNecessary() {
        val playable = mediaPlayer!!.getPlayable()
        if (playable !is FeedMedia) {
            return
        }

        val duration = getDuration()
        val remainingTime = duration - getCurrentPosition()

        val preferences = playable.getItem()!!.getFeed()!!.getPreferences()!!
        val skipEnd = preferences.getFeedSkipEnding()
        if (skipEnd > 0
                && skipEnd * 1000 < getDuration()
                && (remainingTime - (skipEnd * 1000) > 0)
                && ((remainingTime - skipEnd * 1000) < (getCurrentPlaybackSpeed() * 1000))) {
            Log.d(TAG, "skipEndingIfNecessary: Skipping the remaining " + remainingTime + " " + skipEnd * 1000 + " speed " + getCurrentPlaybackSpeed())
            val context = getApplicationContext()
            val skipMsg = context.getResources().getQuantityString(
                    R.plurals.pref_feed_skip_ending_snackbar, skipEnd, skipEnd)
            val toast = Toast.makeText(context, skipMsg, Toast.LENGTH_LONG)
            toast.show()

            this.autoSkippedFeedMediaId = playable.getItem()!!.getIdentifyingValue()
            mediaPlayer!!.skip()
        }
    }

    /**
     * Updates the Media Session for the corresponding status.
     *
     * @param playerStatus the current [PlayerStatus]
     */
    private fun updateMediaSession(playerStatus: PlayerStatus) {
        val sessionState = PlaybackStateCompat.Builder()

        val state: Int
        when (playerStatus) {
            PlayerStatus.PLAYING -> state = PlaybackStateCompat.STATE_PLAYING
            PlayerStatus.PREPARED,
            PlayerStatus.PAUSED -> state = PlaybackStateCompat.STATE_PAUSED
            PlayerStatus.STOPPED -> state = PlaybackStateCompat.STATE_STOPPED
            PlayerStatus.SEEKING -> state = PlaybackStateCompat.STATE_FAST_FORWARDING
            PlayerStatus.PREPARING,
            PlayerStatus.INITIALIZING -> state = PlaybackStateCompat.STATE_CONNECTING
            PlayerStatus.ERROR -> state = PlaybackStateCompat.STATE_ERROR
            PlayerStatus.INITIALIZED, // Deliberate fall-through
            PlayerStatus.INDETERMINATE -> state = PlaybackStateCompat.STATE_NONE
        }

        sessionState.setState(state, getCurrentPosition().toLong(), getCurrentPlaybackSpeed())
        val capabilities = (PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_REWIND
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_FAST_FORWARD
                or PlaybackStateCompat.ACTION_SEEK_TO
                or PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED).toLong()

        sessionState.setActions(capabilities)

        // On Android Auto, custom actions are added in the following order around the play button, if no default
        // actions are present: Near left, near right, far left, far right, additional actions panel
        val rewindBuilder = PlaybackStateCompat.CustomAction.Builder(
                CUSTOM_ACTION_REWIND,
                getString(R.string.rewind_label),
                R.drawable.ic_notification_fast_rewind
        )
        WearMediaSession.addWearExtrasToAction(rewindBuilder)
        sessionState.addCustomAction(rewindBuilder.build())

        val fastForwardBuilder = PlaybackStateCompat.CustomAction.Builder(
                CUSTOM_ACTION_FAST_FORWARD,
                getString(R.string.fast_forward_label),
                R.drawable.ic_notification_fast_forward
        )
        WearMediaSession.addWearExtrasToAction(fastForwardBuilder)
        sessionState.addCustomAction(fastForwardBuilder.build())

        if (UserPreferences.showPlaybackSpeedOnFullNotification()) {
            sessionState.addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                    CUSTOM_ACTION_CHANGE_PLAYBACK_SPEED,
                    getString(R.string.playback_speed),
                    R.drawable.ic_notification_playback_speed
                ).build()
            )
        }

        if (UserPreferences.showSleepTimerOnFullNotification()) {
            @DrawableRes var icon = R.drawable.ic_notification_sleep
            if (sleepTimerActive()) {
                icon = R.drawable.ic_notification_sleep_off
            }
            sessionState.addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_TOGGLE_SLEEP_TIMER,
                            getString(R.string.sleep_timer_label), icon).build())
        }

        if (UserPreferences.showNextChapterOnFullNotification()) {
            if (getPlayable() != null && getPlayable()!!.getChapters() != null) {
                sessionState.addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                        CUSTOM_ACTION_NEXT_CHAPTER,
                        getString(R.string.next_chapter), R.drawable.ic_notification_next_chapter)
                        .build())
            }
        }

        if (UserPreferences.showSkipOnFullNotification()) {
            sessionState.addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    CUSTOM_ACTION_SKIP_TO_NEXT,
                    getString(R.string.skip_episode_label),
                    R.drawable.ic_notification_skip
                ).build()
            )
        }

        WearMediaSession.mediaSessionSetExtraForWear(mediaSession!!)

        mediaSession!!.setPlaybackState(sessionState.build())
    }

    private fun updateNotificationAndMediaSession(p: Playable?) {
        setupNotification(p)
        updateMediaSessionMetadata(p)
    }

    private fun updateMediaSessionMetadata(p: Playable?) {
        if (p == null || mediaSession == null) {
            return
        }

        val builder = MediaMetadataCompat.Builder()
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, p.getFeedTitle())
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, p.getEpisodeTitle())
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, p.getFeedTitle())
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, p.getDuration().toLong())
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, p.getEpisodeTitle())
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, p.getFeedTitle())


        if (notificationBuilder.isIconCached()) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, notificationBuilder.getCachedIcon())
        } else {
            var iconUri = p.getImageLocation()
            if (p is FeedMedia) { // Don't use embedded cover etc, which Android can't load
                if (p.getItem() != null) {
                    val item = p.getItem()
                    if (item!!.getImageUrl() != null) {
                        iconUri = item.getImageUrl()
                    } else if (item.getFeed() != null) {
                        iconUri = item.getFeed()!!.getImageUrl()
                    }
                }
            }
            if (!TextUtils.isEmpty(iconUri)) {
                builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, iconUri)
            }
        }

        if (stateManager.hasReceivedValidStartCommand()) {
            mediaSession!!.setSessionActivity(PendingIntent.getActivity(this, R.id.pending_intent_player_activity,
                    getPlayerActivityIntent(this), PendingIntent.FLAG_UPDATE_CURRENT
                            or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)))
            try {
                mediaSession!!.setMetadata(builder.build())
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Setting media session metadata", e)
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, null)
                mediaSession!!.setMetadata(builder.build())
            }
        }
    }

    /**
     * Used by setupNotification to load notification data in another thread.
     */
    private var playableIconLoaderThread: Thread? = null

    /**
     * Prepares notification and starts the service in the foreground.
     */
    @Synchronized
    private fun setupNotification(playable: Playable?) {
        Log.d(TAG, "setupNotification")
        if (playableIconLoaderThread != null) {
            playableIconLoaderThread!!.interrupt()
        }
        if (playable == null || mediaPlayer == null) {
            Log.d(TAG, "setupNotification: playable=" + playable)
            Log.d(TAG, "setupNotification: mediaPlayer=" + mediaPlayer)
            if (!stateManager.hasReceivedValidStartCommand()) {
                stateManager.stopService()
            }
            return
        }

        val playerStatus = mediaPlayer!!.getPlayerStatus()
        notificationBuilder.setPlayable(playable)
        notificationBuilder.setMediaSessionToken(mediaSession!!.getSessionToken())
        notificationBuilder.setPlayerStatus(playerStatus)
        notificationBuilder.updatePosition(getCurrentPosition(), getCurrentPlaybackSpeed())

        val notificationManager = NotificationManagerCompat.from(this)
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(R.id.notification_playing, notificationBuilder.build())
        }

        if (!notificationBuilder.isIconCached()) {
            playableIconLoaderThread = Thread {
                Log.d(TAG, "Loading notification icon")
                notificationBuilder.loadIcon()
                if (!Thread.currentThread().isInterrupted()) {
                    if (ContextCompat.checkSelfPermission(getApplicationContext(),
                                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        notificationManager.notify(R.id.notification_playing, notificationBuilder.build())
                    }
                    updateMediaSessionMetadata(playable)
                }
            }
            playableIconLoaderThread!!.start()
        }
    }

    /**
     * Persists the current position and last played time of the media file.
     *
     * @param fromMediaPlayer if true, the information is gathered from the current Media Player
     * and {@param playable} and {@param position} become irrelevant.
     * @param playable        the playable for which the current position should be saved, unless
     * {@param fromMediaPlayer} is true.
     * @param position        the position that should be saved, unless {@param fromMediaPlayer} is true.
     */
    @Synchronized
    private fun saveCurrentPosition(fromMediaPlayer: Boolean, playable: Playable?, position: Int) {
        var newPosition = position
        var newPlayable = playable
        val duration: Int
        if (fromMediaPlayer) {
            newPosition = getCurrentPosition()
            duration = getDuration()
            newPlayable = mediaPlayer!!.getPlayable()
        } else {
            duration = playable!!.getDuration()
        }
        if (newPosition != Playable.INVALID_TIME && duration != Playable.INVALID_TIME && newPlayable != null) {
            Log.d(TAG, "Saving current position to " + newPosition)
            PlayableUtils.saveCurrentPosition(newPlayable, newPosition, System.currentTimeMillis())
        }
    }

    fun sleepTimerActive(): Boolean {
        return sleepTimer != null && sleepTimer!!.isActive()
    }

    fun getSleepTimerTimeLeft(): TimerValue {
        if (sleepTimerActive()) {
            return sleepTimer!!.getTimeLeft()
        } else {
            return TimerValue(0, 0)
        }
    }

    private fun bluetoothNotifyChange(info: PlaybackServiceMediaPlayer.PSMPInfo, whatChanged: String) {
        var isPlaying = false

        if (info.getPlayerStatus() == PlayerStatus.PLAYING) {
            isPlaying = true
        }

        if (info.getPlayable() != null) {
            val i = Intent(whatChanged)
            i.putExtra("id", 1L)
            i.putExtra("artist", "")
            i.putExtra("album", info.getPlayable()!!.getFeedTitle())
            i.putExtra("track", info.getPlayable()!!.getEpisodeTitle())
            i.putExtra("playing", isPlaying)
            i.putExtra("duration", info.getPlayable()!!.getDuration().toLong())
            i.putExtra("position", info.getPlayable()!!.getPosition().toLong())
            sendBroadcast(i)
        }
    }

    /**
     * Pauses playback when the headset is disconnected and the preference is
     * set
     */
    private val headsetDisconnected: BroadcastReceiver = object : BroadcastReceiver() {
        private val tag = "headsetDisconnected"
        private val unplugged = 0
        private val plugged = 1

        override fun onReceive(context: Context, intent: Intent) {
            if (isInitialStickyBroadcast()) {
                // Don't pause playback after we just started, just because the receiver
                // delivers the current headset state (instead of a change)
                return
            }

            if (TextUtils.equals(intent.getAction(), Intent.ACTION_HEADSET_PLUG)) {
                val state = intent.getIntExtra("state", -1)
                Log.d(tag, "Headset plug event. State is " + state)
                if (state != -1) {
                    if (state == unplugged) {
                        Log.d(tag, "Headset was unplugged during playback.")
                    } else if (state == plugged) {
                        Log.d(tag, "Headset was plugged in during playback.")
                        unpauseIfPauseOnDisconnect(false)
                    }
                } else {
                    Log.e(tag, "Received invalid ACTION_HEADSET_PLUG intent")
                }
            }
        }
    }

    private val bluetoothStateUpdated: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (TextUtils.equals(intent.getAction(), BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) {
                val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)
                if (state == BluetoothA2dp.STATE_CONNECTED) {
                    Log.d(TAG, "Received bluetooth connection intent")
                    unpauseIfPauseOnDisconnect(true)
                }
            }
        }
    }

    private val audioBecomingNoisy: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            // sound is about to change, eg. bluetooth -> speaker
            Log.d(TAG, "Pausing playback because audio is becoming noisy")
            pauseIfPauseOnDisconnect()
        }
    }

    /**
     * Pauses playback if PREF_PAUSE_ON_HEADSET_DISCONNECT was set to true.
     */
    private fun pauseIfPauseOnDisconnect() {
        Log.d(TAG, "pauseIfPauseOnDisconnect()")
        transientPause = (mediaPlayer!!.getPlayerStatus() == PlayerStatus.PLAYING)
        if (UserPreferences.isPauseOnHeadsetDisconnect() && !isCasting) {
            mediaPlayer!!.pause(!UserPreferences.isPersistNotify(), false)
        }
    }

    /**
     * @param bluetooth true if the event for unpausing came from bluetooth
     */
    private fun unpauseIfPauseOnDisconnect(bluetooth: Boolean) {
        if (mediaPlayer!!.isAudioChannelInUse()) {
            Log.d(TAG, "unpauseIfPauseOnDisconnect() audio is in use")
            return
        }
        if (transientPause) {
            transientPause = false
            if (Build.VERSION.SDK_INT >= 31) {
                stateManager.stopService()
                return
            }
            if (!bluetooth && UserPreferences.isUnpauseOnHeadsetReconnect()) {
                mediaPlayer!!.resume()
            } else if (bluetooth && UserPreferences.isUnpauseOnBluetoothReconnect()) {
                // let the user know we've started playback again...
                val v = getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
                if (v != null) {
                    v.vibrate(500L)
                }
                mediaPlayer!!.resume()
            }
        }
    }

    private val shutdownReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (TextUtils.equals(intent.getAction(), PlaybackServiceInterface.ACTION_SHUTDOWN_PLAYBACK_SERVICE)) {
                stateManager.stopService()
                PlaybackPreferences.writeNoMediaPlaying()
                EventBus.getDefault().post(PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN))
                EventBus.getDefault().post(PlayerStatusEvent())
            }
        }

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun volumeAdaptionChanged(event: VolumeAdaptionChangedEvent) {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()
        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, event.getFeedId(), event.getVolumeAdaptionSetting())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun speedPresetChanged(event: SpeedPresetChangedEvent) {
        if (getPlayable() is FeedMedia) {
            val playable = getPlayable() as FeedMedia
            if (playable.getItem()!!.getFeed()!!.getId() == event.getFeedId()) {
                if (event.getSpeed() == FeedPreferences.SPEED_USE_GLOBAL) {
                    setSpeed(UserPreferences.getPlaybackSpeed())
                } else {
                    setSpeed(event.getSpeed())
                }
                if (event.getSkipSilence() == FeedPreferences.SkipSilence.GLOBAL) {
                    setSkipSilence(UserPreferences.isSkipSilence())
                } else {
                    setSkipSilence(event.getSkipSilence() == FeedPreferences.SkipSilence.AGGRESSIVE)
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun skipIntroEndingPresetChanged(event: SkipIntroEndingChangedEvent) {
        if (getPlayable() is FeedMedia) {
            val playable = getPlayable() as FeedMedia
            if (playable.getItem()!!.getFeed()!!.getId() == event.getFeedId()) {
                if (event.getSkipEnding() != 0) {
                    val feedPreferences = playable.getItem()!!.getFeed()!!.getPreferences()!!
                    feedPreferences.setFeedSkipIntro(event.getSkipIntro())
                    feedPreferences.setFeedSkipEnding(event.getSkipEnding())
                }
            }
        }
    }

    fun resume() {
        mediaPlayer!!.resume()
    }

    fun prepare() {
        mediaPlayer!!.prepare()
    }

    fun pause(abandonAudioFocus: Boolean, reinit: Boolean) {
        mediaPlayer!!.pause(abandonAudioFocus, reinit)
    }

    fun getPSMPInfo(): PlaybackServiceMediaPlayer.PSMPInfo {
        return mediaPlayer!!.getPSMPInfo()
    }

    fun getStatus(): PlayerStatus {
        return mediaPlayer!!.getPlayerStatus()
    }

    fun getPlayable(): Playable? {
        return mediaPlayer!!.getPlayable()
    }

    fun setSpeed(speed: Float) {
        PlaybackPreferences.setCurrentlyPlayingTemporaryPlaybackSpeed(speed)
        mediaPlayer!!.setPlaybackParams(speed, getCurrentSkipSilence())
    }

    fun setSkipSilence(skipSilence: Boolean) {
        PlaybackPreferences.setCurrentlyPlayingTemporarySkipSilence(skipSilence)
        mediaPlayer!!.setPlaybackParams(getCurrentPlaybackSpeed(), skipSilence)
    }

    fun getCurrentPlaybackSpeed(): Float {
        if (mediaPlayer == null) {
            return 1.0f
        }
        return mediaPlayer!!.getPlaybackSpeed()
    }

    fun getCurrentSkipSilence(): Boolean {
        if (mediaPlayer == null) {
            return false
        }
        return mediaPlayer!!.getSkipSilence()
    }

    fun isStartWhenPrepared(): Boolean {
        return mediaPlayer!!.isStartWhenPrepared()
    }

    fun setStartWhenPrepared(s: Boolean) {
        mediaPlayer!!.setStartWhenPrepared(s)
    }

    fun seekTo(t: Int) {
        mediaPlayer!!.seekTo(t)
        EventBus.getDefault().post(PlaybackPositionEvent(t, getDuration()))
    }

    private fun seekDelta(d: Int) {
        mediaPlayer!!.seekDelta(d)
    }

    /**
     * call getDuration() on mediaplayer or return INVALID_TIME if player is in
     * an invalid state.
     */
    fun getDuration(): Int {
        if (mediaPlayer == null) {
            return Playable.INVALID_TIME
        }
        return mediaPlayer!!.getDuration()
    }

    /**
     * call getCurrentPosition() on mediaplayer or return INVALID_TIME if player
     * is in an invalid state.
     */
    fun getCurrentPosition(): Int {
        if (mediaPlayer == null) {
            return Playable.INVALID_TIME
        }
        return mediaPlayer!!.getPosition()
    }

    fun getAudioTracks(): List<String> {
        if (mediaPlayer == null) {
            return Collections.emptyList()
        }
        return mediaPlayer!!.getAudioTracks()
    }

    fun getSelectedAudioTrack(): Int {
        if (mediaPlayer == null) {
            return -1
        }
        return mediaPlayer!!.getSelectedAudioTrack()
    }

    fun setAudioTrack(track: Int) {
        if (mediaPlayer != null) {
            mediaPlayer!!.setAudioTrack(track)
        }
    }

    fun isStreaming(): Boolean {
        return mediaPlayer!!.isStreaming()
    }

    fun getVideoSize(): Pair<Int, Int>? {
        return mediaPlayer!!.getVideoSize()
    }

    private fun setupPositionObserver() {
        if (positionEventTimer != null) {
            positionEventTimer!!.dispose()
        }

        Log.d(TAG, "Setting up position observer")
        positionEventTimer = Observable.interval(1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    EventBus.getDefault().post(PlaybackPositionEvent(getCurrentPosition(), getDuration()))
                    if (Build.VERSION.SDK_INT < 29) {
                        notificationBuilder.updatePosition(getCurrentPosition(), getCurrentPlaybackSpeed())
                        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        if (ContextCompat.checkSelfPermission(getApplicationContext(),
                                        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                            notificationManager.notify(R.id.notification_playing, notificationBuilder.build())
                        }
                    }
                    skipEndingIfNecessary()
                }
    }

    private fun cancelPositionObserver() {
        if (positionEventTimer != null) {
            positionEventTimer!!.dispose()
        }
    }

    private fun addPlayableToQueue(playable: Playable) {
        if (playable is FeedMedia) {
            DBWriter.addQueueItem(this, playable.getItem()!!)
            notifyChildrenChanged(getString(R.string.queue_label))
        }
    }

    private val sessionCallback: MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {

        override fun onPlay() {
            Log.d(TAG, "onPlay()")
            val status = getStatus()
            if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
                resume()
            } else if (status == PlayerStatus.INITIALIZED) {
                setStartWhenPrepared(true)
                prepare()
            }
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            Log.d(TAG, "onPlayFromMediaId: mediaId: " + mediaId + " extras: " + extras.toString())
            val p = DBReader.getFeedMedia(java.lang.Long.parseLong(mediaId))
            if (p != null) {
                startPlaying(p, false)
            }
        }

        override fun onPlayFromSearch(query: String, extras: Bundle?) {
            Log.d(TAG, "onPlayFromSearch  query=" + query + " extras=" + extras.toString())

            if (query == "") {
                Log.d(TAG, "onPlayFromSearch called with empty query, resuming from the last position")
                startPlayingFromPreferences()
                return
            }

            val results = DBReader.searchFeedItems(0L, query, FeedItemFilter.unfiltered())
            if (results.size > 0 && results.get(0).getMedia() != null) {
                val media = results.get(0).getMedia()
                startPlaying(media!!, false)
                return
            }
            onPlay()
        }

        override fun onPause() {
            Log.d(TAG, "onPause()")
            if (getStatus() == PlayerStatus.PLAYING) {
                pause(!UserPreferences.isPersistNotify(), false)
            }
        }

        override fun onStop() {
            Log.d(TAG, "onStop()")
            if (!mediaPlayer!!.isCasting()) {
                mediaPlayer!!.stopPlayback(true)
            }
        }

        override fun onSkipToPrevious() {
            Log.d(TAG, "onSkipToPrevious()")
            seekDelta(-UserPreferences.getRewindSecs() * 1000)
        }

        override fun onRewind() {
            Log.d(TAG, "onRewind()")
            seekDelta(-UserPreferences.getRewindSecs() * 1000)
        }

        fun onNextChapter() {
            val chapters = mediaPlayer!!.getPlayable()!!.getChapters()
            if (chapters == null) {
                // No chapters, just fallback to next episode
                mediaPlayer!!.skip()
                return
            }

            val nextChapter = Chapter.getAfterPosition(chapters, mediaPlayer!!.getPosition()) + 1

            if (chapters.size < nextChapter + 1) {
                // We are on the last chapter, just fallback to the next episode
                mediaPlayer!!.skip()
                return
            }

            mediaPlayer!!.seekTo(chapters.get(nextChapter).getStart().toInt())
        }

        override fun onFastForward() {
            Log.d(TAG, "onFastForward()")
            seekDelta(UserPreferences.getFastForwardSecs() * 1000)
        }

        override fun onSkipToNext() {
            Log.d(TAG, "onSkipToNext()")
            val uiModeManager = getApplicationContext()
                    .getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            if (UserPreferences.getHardwareForwardButton() == KeyEvent.KEYCODE_MEDIA_NEXT
                    || uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_CAR) {
                mediaPlayer!!.skip()
            } else {
                seekDelta(UserPreferences.getFastForwardSecs() * 1000)
            }
        }


        override fun onSeekTo(pos: Long) {
            Log.d(TAG, "onSeekTo()")
            seekTo(pos.toInt())
        }

        override fun onSetPlaybackSpeed(speed: Float) {
            Log.d(TAG, "onSetPlaybackSpeed()")
            setSpeed(speed)
        }

        override fun onMediaButtonEvent(mediaButton: Intent?): Boolean {
            Log.d(TAG, "onMediaButtonEvent(" + mediaButton + ")")
            if (mediaButton != null) {
                val keyEvent = mediaButton.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null &&
                        keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                        keyEvent.getRepeatCount() == 0) {
                    val keyCode = keyEvent.getKeyCode()
                    if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                        clickCount += 1
                        clickHandler.removeCallbacksAndMessages(null)
                        clickHandler.postDelayed({
                            if (clickCount == 1) {
                                handleKeycode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false)
                            } else if (clickCount == 2) {
                                onFastForward()
                            } else if (clickCount == 3) {
                                onRewind()
                            }
                            clickCount = 0
                        }, ViewConfiguration.getDoubleTapTimeout().toLong())
                        return true
                    } else {
                        return handleKeycode(keyCode, false)
                    }
                }
            }
            return false
        }

        override fun onCustomAction(action: String?, extra: Bundle?) {
            Log.d(TAG, "onCustomAction(" + action + ")")
            if (CUSTOM_ACTION_FAST_FORWARD == action) {
                onFastForward()
            } else if (CUSTOM_ACTION_REWIND == action) {
                onRewind()
            } else if (CUSTOM_ACTION_SKIP_TO_NEXT == action) {
                mediaPlayer!!.skip()
            } else if (CUSTOM_ACTION_NEXT_CHAPTER == action) {
                onNextChapter()
            } else if (CUSTOM_ACTION_CHANGE_PLAYBACK_SPEED == action) {
                val selectedSpeeds = UserPreferences.getPlaybackSpeedArray()

                // If the list has zero or one element, there's nothing we can do to change the playback speed.
                if (selectedSpeeds.size > 1) {
                    val speedPosition = selectedSpeeds.indexOf(mediaPlayer!!.getPlaybackSpeed())
                    val newSpeed: Float

                    if (speedPosition == selectedSpeeds.size - 1) {
                        // This is the last element. Wrap instead of going over the size of the list.
                        newSpeed = selectedSpeeds.get(0)
                    } else {
                        // If speedPosition is still -1 (the user isn't using a preset), use the first preset in the
                        // list.
                        newSpeed = selectedSpeeds.get(speedPosition + 1)
                    }
                    onSetPlaybackSpeed(newSpeed)
                }
            } else if (CUSTOM_ACTION_TOGGLE_SLEEP_TIMER == action) {
                if (sleepTimerActive()) {
                    disableSleepTimer()
                } else {
                    setSleepTimer(SleepTimerPreferences.timerMillisOrEpisodes())
                }
            }
        }
    }
}
