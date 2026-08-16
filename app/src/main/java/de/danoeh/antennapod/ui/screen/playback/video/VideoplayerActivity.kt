package de.danoeh.antennapod.ui.screen.playback.video

import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.SurfaceHolder
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.databinding.VideoplayerActivityBinding
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.PlayerErrorEvent
import de.danoeh.antennapod.event.playback.BufferUpdateEvent
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.base.PlayerStatus
import de.danoeh.antennapod.playback.cast.CastEnabledActivity
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.playback.service.PlaybackService
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler
import de.danoeh.antennapod.ui.screen.chapter.ChaptersFragment
import de.danoeh.antennapod.ui.screen.playback.MediaPlayerErrorDialog
import de.danoeh.antennapod.ui.screen.playback.PlaybackControlsDialog
import de.danoeh.antennapod.ui.screen.playback.SleepTimerDialog
import de.danoeh.antennapod.ui.screen.playback.TranscriptDialogFragment
import de.danoeh.antennapod.ui.screen.playback.VariableSpeedDialog
import de.danoeh.antennapod.ui.share.ShareDialog
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Collections

/**
 * Activity for playing video files.
 */
class VideoplayerActivity : CastEnabledActivity(),
        Toolbar.OnMenuItemClickListener {
    private var videoSurfaceCreated = false
    private var destroyingDueToReload = false
    private var switchToAudioOnly = false
    private var viewBinding: VideoplayerActivityBinding? = null
    private var controller: PlaybackController? = null
    private var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY)
        setTheme(R.style.Theme_AntennaPod_Dark)
        getSupportActionBar()!!.hide()
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate()")

        getWindow().setFormat(PixelFormat.TRANSPARENT)
        viewBinding = VideoplayerActivityBinding.inflate(LayoutInflater.from(this))
        setContentView(viewBinding!!.getRoot())
        setupControlsView()
        setupView()
        setupPip()
    }

    private fun setupControlsView() {
        viewBinding!!.controlsView.setListener(object : VideoPlayerControlsView.ControlsListener {
            override fun onPlayPause() {
                this@VideoplayerActivity.onPlayPause()
            }

            override fun onRewind() {
                this@VideoplayerActivity.onRewind()
            }

            override fun onFastForward() {
                this@VideoplayerActivity.onFastForward()
            }

            override fun onSeek(positionMs: Int) {
                if (controller != null) {
                    controller!!.seekTo(positionMs)
                }
            }
        })

        viewBinding!!.videoPlayerContainer.setOnTouchListener { v, event ->
            viewBinding!!.controlsView.handleTouchEvent(event, PictureInPictureUtil.isInPictureInPictureMode(this))
            true
        }

        val toolbar = viewBinding!!.controlsView.getToolbar()
        toolbar.inflateMenu(R.menu.mediaplayer)
        requestCastButton(toolbar.getMenu())
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setNavigationOnClickListener {
            val intent = Intent(this@VideoplayerActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    internal fun setupPip() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(true)
            builder.setSourceRectHint(viewBinding!!.getRoot().getClipBounds())
        }
        setPictureInPictureParams(builder.build())
    }

    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (Build.VERSION.SDK_INT < 35) {
            return
        }
        if (pipState.isTransitioningToPip()) {
            viewBinding!!.controlsView.hideControls(false)
        }
    }

    override fun onResume() {
        super.onResume()
        switchToAudioOnly = false
        if (PlaybackService.isCasting()) {
            val intent = PlaybackService.getPlayerActivityIntent(this)
            if (intent.getComponent()!!.getClassName() != VideoplayerActivity::class.java.getName()) {
                destroyingDueToReload = true
                finish()
                startActivity(intent)
            }
        }
    }

    override fun onStop() {
        if (controller != null) {
            controller!!.release()
            controller = null // prevent leak
        }
        if (disposable != null) {
            disposable!!.dispose()
        }
        viewBinding!!.controlsView.cancelAutoHide()
        EventBus.getDefault().unregister(this)
        super.onStop()
        if (!PictureInPictureUtil.isInPictureInPictureMode(this)) {
            viewBinding!!.controlsView.hideControls(false)
        }
        // Controller released; we will not receive buffering updates
        viewBinding!!.controlsView.setProgressBarVisibility(View.GONE)
    }

    override fun onUserLeaveHint() {
        if (!PictureInPictureUtil.isInPictureInPictureMode(this)) {
            compatEnterPictureInPicture()
        }
    }

    override fun onStart() {
        super.onStart()
        controller = newPlaybackController()
        controller!!.init()
        loadMediaInfo()
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        if (!PictureInPictureUtil.isInPictureInPictureMode(this)) {
            if (controller != null && controller!!.getStatus() == PlayerStatus.PLAYING) {
                controller!!.pause()
            }
        }
        super.onPause()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Glide.get(this).trimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).clearMemory()
    }

    private fun newPlaybackController(): PlaybackController {
        return object : PlaybackController(this@VideoplayerActivity) {
            override fun updatePlayButtonShowsPlay(showPlay: Boolean) {
                viewBinding!!.controlsView.setPlayButtonShowsPlay(showPlay)
                if (showPlay) {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    setupVideoAspectRatio()
                    if (videoSurfaceCreated && controller != null) {
                        Log.d(TAG, "Videosurface already created, setting videosurface now")
                        controller!!.setVideoSurface(viewBinding!!.videoView.getHolder())
                    }
                }
            }

            override fun loadMediaInfo() {
                this@VideoplayerActivity.loadMediaInfo()
            }

            override fun onPlaybackEnd() {
                finish()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    fun bufferUpdate(event: BufferUpdateEvent) {
        if (event.hasStarted()) {
            viewBinding!!.controlsView.setProgressBarVisibility(View.VISIBLE)
        } else if (event.hasEnded()) {
            viewBinding!!.controlsView.setProgressBarVisibility(View.INVISIBLE)
        } else {
            viewBinding!!.controlsView.setBufferingProgress(event.getProgress())
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    fun sleepTimerUpdate(event: SleepTimerUpdatedEvent) {
        if (event.isCancelled() || event.wasJustEnabled()) {
            updateToolbar(null)
        }
    }

    protected fun loadMediaInfo() {
        Log.d(TAG, "loadMediaInfo()")
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Maybe.create<Pair<Playable, FeedItem?>>({ emitter ->
            if (controller == null) {
                emitter.onComplete()
                return@create
            }
            val media = controller!!.getMedia()
            if (media == null) {
                emitter.onComplete()
                return@create
            }
            var feedItem = getFeedItem(controller!!.getMedia())
            if (feedItem != null) {
                feedItem = DBReader.getFeedItem(feedItem.getId())
            }
            emitter.onSuccess(Pair(media, feedItem))
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { result ->
                            val media = result.first
                            if (controller!!.getStatus() == PlayerStatus.PLAYING
                                    && !controller!!.isPlayingVideoLocally()) {
                                Log.d(TAG, "Closing, no longer video")
                                destroyingDueToReload = true
                                finish()
                                MainActivityStarter(this).withOpenPlayer().start()
                                return@subscribe
                            }
                            updateToolbar(media)
                        }, { error -> Log.e(TAG, Log.getStackTraceString(error)) }
                )
    }

    protected fun setupView() {
        viewBinding!!.videoView.getHolder().addCallback(surfaceHolderCallback)
        viewBinding!!.videoView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN)

        viewBinding!!.videoPlayerContainer.getViewTreeObserver().addOnGlobalLayoutListener {
            viewBinding!!.videoView.setAvailableSize(
                    viewBinding!!.videoPlayerContainer.getWidth().toFloat(),
                    viewBinding!!.videoPlayerContainer.getHeight().toFloat())
        }
    }

    private fun setupVideoAspectRatio() {
        if (videoSurfaceCreated && controller != null) {
            val videoSize = controller!!.getVideoSize()
            if (videoSize != null && videoSize.first > 0 && videoSize.second > 0) {
                Log.d(TAG, "Width,height of video: " + videoSize.first + ", " + videoSize.second)
                viewBinding!!.videoView.setVideoSize(videoSize.first, videoSize.second)
            } else {
                Log.e(TAG, "Could not determine video size")
            }
        }
    }

    internal fun onRewind() {
        if (controller == null) {
            return
        }
        val curr = controller!!.getPosition()
        controller!!.seekTo(curr - UserPreferences.getRewindSecs() * 1000)
    }

    internal fun onPlayPause() {
        if (controller == null) {
            return
        }
        controller!!.playPause()
    }

    internal fun onFastForward() {
        if (controller == null) {
            return
        }
        val curr = controller!!.getPosition()
        controller!!.seekTo(curr + UserPreferences.getFastForwardSecs() * 1000)
    }

    private val surfaceHolderCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            holder.setFixedSize(width, height)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG, "Videoview holder created")
            videoSurfaceCreated = true
            if (controller != null && controller!!.getStatus() == PlayerStatus.PLAYING) {
                controller!!.setVideoSurface(holder)
            }
            setupVideoAspectRatio()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "Videosurface was destroyed")
            videoSurfaceCreated = false
            if (controller != null && !destroyingDueToReload && !switchToAudioOnly) {
                controller!!.notifyVideoSurfaceAbandoned()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackServiceChanged(event: PlaybackServiceEvent) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN) {
            finish()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMediaPlayerError(event: PlayerErrorEvent) {
        MediaPlayerErrorDialog.show(this, event)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedItemEvent(event: FeedItemEvent) {
        loadMediaInfo()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: MessageEvent) {
        Log.d(TAG, "onEvent(" + event + ")")
        val errorDialog = MaterialAlertDialogBuilder(this)
        errorDialog.setMessage(event.message)
        if (event.action != null) {
            errorDialog.setPositiveButton(event.actionText) { dialog, which -> event.action!!.accept(this) }
        }
        errorDialog.show()
    }

    private fun updateToolbar(media: Playable?) {
        val toolbar = viewBinding!!.controlsView.getToolbar()
        if (media != null) {
            toolbar.setSubtitle(media.getEpisodeTitle())
            toolbar.setTitle(media.getFeedTitle())
        }
        val isFeedMedia = media is FeedMedia

        val menu = toolbar.getMenu()
        menu.findItem(R.id.open_feed_item)!!.setVisible(isFeedMedia) // FeedMedia implies it belongs to a Feed
        if (isFeedMedia) {
            FeedItemMenuHandler.onPrepareMenu(menu, Collections.singletonList((media as FeedMedia).getItem()!!))
        }

        if (controller != null) {
            menu.findItem(R.id.set_sleeptimer_item)!!.setVisible(!controller!!.sleepTimerActive())
            menu.findItem(R.id.disable_sleeptimer_item)!!.setVisible(controller!!.sleepTimerActive())
            menu.findItem(R.id.audio_controls)!!.setVisible(controller!!.getAudioTracks().size >= 2)
        }

        menu.findItem(R.id.player_switch_to_audio_only)!!.setVisible(true)
        menu.findItem(R.id.playback_speed)!!.setVisible(true)
        menu.findItem(R.id.player_show_chapters)!!.setVisible(true)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.player_switch_to_audio_only) {
            switchToAudioOnly = true
            finish()
            return true
        } else if (item.getItemId() == R.id.player_show_chapters) {
            ChaptersFragment().show(getSupportFragmentManager(), ChaptersFragment.TAG)
            return true
        } else if (item.getItemId() == R.id.transcript_item) {
            TranscriptDialogFragment().show(getSupportFragmentManager(), TranscriptDialogFragment.TAG)
            return true
        }

        if (controller == null) {
            return false
        }

        val media = controller!!.getMedia()
        if (media == null) {
            return false
        }
        val feedItem: FeedItem? = getFeedItem(media)
        if (item.getItemId() == R.id.add_to_favorites_item && feedItem != null) {
            DBWriter.addFavoriteItems(Collections.singletonList(feedItem))
        } else if (item.getItemId() == R.id.remove_from_favorites_item && feedItem != null) {
            DBWriter.removeFavoriteItems(Collections.singletonList(feedItem))
        } else if (item.getItemId() == R.id.disable_sleeptimer_item
                || item.getItemId() == R.id.set_sleeptimer_item) {
            SleepTimerDialog().show(getSupportFragmentManager(), "SleepTimerDialog")
        } else if (item.getItemId() == R.id.audio_controls) {
            val dialog = PlaybackControlsDialog.newInstance()
            dialog.show(getSupportFragmentManager(), "playback_controls")
        } else if (item.getItemId() == R.id.open_feed_item && feedItem != null) {
            MainActivityStarter(this).withOpenFeed(feedItem.getFeedId()).withClearTop().start()
        } else if (item.getItemId() == R.id.visit_website_item) {
            IntentUtils.openInBrowser(this@VideoplayerActivity, getWebsiteLinkWithFallback(media)!!)
        } else if (item.getItemId() == R.id.share_item && feedItem != null) {
            val shareDialog = ShareDialog.newInstance(feedItem)
            shareDialog.show(getSupportFragmentManager(), "ShareEpisodeDialog")
        } else if (item.getItemId() == R.id.playback_speed) {
            VariableSpeedDialog().show(getSupportFragmentManager(), null)
        } else {
            return false
        }
        return true
    }

    private fun getWebsiteLinkWithFallback(media: Playable?): String? {
        if (media == null) {
            return null
        } else if (StringUtils.isNotBlank(media.getWebsiteLink())) {
            return media.getWebsiteLink()
        } else if (media is FeedMedia) {
            return media.getItem()!!.getLinkWithFallback()
        }
        return null
    }

    private fun getFeedItem(playable: Playable?): FeedItem? {
        return if (playable is FeedMedia) {
            playable.getItem()
        } else {
            null
        }
    }

    private fun compatEnterPictureInPicture() {
        if (PictureInPictureUtil.supportsPictureInPicture(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            viewBinding!!.controlsView.hideControls(false)
            enterPictureInPictureMode()
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val currentFocus = getCurrentFocus()
        if (currentFocus is EditText) {
            return super.onKeyUp(keyCode, event)
        }

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        when (keyCode) {
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_SPACE -> {
                onPlayPause()
                viewBinding!!.controlsView.toggleControls()
                return true
            }
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_COMMA -> {
                onRewind()
                return true
            }
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_PERIOD -> {
                onFastForward()
                return true
            }
            KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_ESCAPE -> {
                //Exit fullscreen mode
                onBackPressed()
                return true
            }
            KeyEvent.KEYCODE_I -> {
                compatEnterPictureInPicture()
                return true
            }
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_W -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_S -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_M -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            else -> {
            }
        }

        //Go to x% of video:
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            controller!!.seekTo((0.1f * (keyCode - KeyEvent.KEYCODE_0) * controller!!.getDuration()).toInt())
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        private const val TAG = "VideoplayerActivity"
    }
}
