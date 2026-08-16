package de.danoeh.antennapod.ui.screen.playback.video

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.util.Consumer
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.databinding.Media3VideoPlayerActivityBinding
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.playback.service.Media3PlaybackService
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler
import de.danoeh.antennapod.ui.screen.chapter.ChaptersFragment
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
import java.util.concurrent.ExecutionException

class Media3VideoPlayerActivity : AppCompatActivity(), Toolbar.OnMenuItemClickListener {
    private var viewBinding: Media3VideoPlayerActivityBinding? = null
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var currentMedia: FeedMedia? = null
    private var mediaLoadDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY)
        setTheme(R.style.Theme_AntennaPod_Dark)
        getSupportActionBar()!!.hide()
        super.onCreate(savedInstanceState)
        viewBinding = Media3VideoPlayerActivityBinding.inflate(getLayoutInflater())
        setContentView(viewBinding!!.getRoot())

        viewBinding!!.playerView.setUseController(false)
        setupControlsView()
        setupFullScreenMode()
        setupPictureInPicture()
    }

    private fun setupControlsView() {
        val toolbar = viewBinding!!.controlsView.getToolbar()
        toolbar.inflateMenu(R.menu.mediaplayer)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setNavigationOnClickListener {
            val intent = Intent(this@Media3VideoPlayerActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
        toolbar.getMenu().findItem(R.id.player_switch_to_audio_only)!!.setVisible(true)
        toolbar.getMenu().findItem(R.id.playback_speed)!!.setVisible(true)
        toolbar.getMenu().findItem(R.id.player_show_chapters)!!.setVisible(true)
        toolbar.getMenu().findItem(R.id.audio_controls)!!.setVisible(true)

        viewBinding!!.controlsView.setListener(object : VideoPlayerControlsView.ControlsListener {
            override fun onPlayPause() {
                PlaybackController.bindToMedia3Service(this@Media3VideoPlayerActivity, Consumer { controller ->
                    if (controller.isPlaying()) {
                        controller.pause()
                    } else {
                        controller.play()
                    }
                })
            }

            override fun onRewind() {
                PlaybackController.bindToMedia3Service(this@Media3VideoPlayerActivity, MediaController::seekBack)
            }

            override fun onFastForward() {
                PlaybackController.bindToMedia3Service(this@Media3VideoPlayerActivity, MediaController::seekForward)
            }

            override fun onSeek(positionMs: Int) {
                PlaybackController.bindToMedia3Service(this@Media3VideoPlayerActivity,
                        Consumer { controller -> controller.seekTo(positionMs.toLong()) })
            }
        })

        viewBinding!!.getRoot().setOnTouchListener { v, event ->
            viewBinding!!.controlsView.handleTouchEvent(event, PictureInPictureUtil.isInPictureInPictureMode(this))
            true
        }
    }

    private fun setupFullScreenMode() {
        viewBinding!!.playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }

    private fun setupPictureInPicture() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }

        val builder = PictureInPictureParams.Builder()

        if (mediaController != null) {
            val videoWidth = mediaController!!.getVideoSize().width
            val videoHeight = mediaController!!.getVideoSize().height
            if (videoWidth > 0 && videoHeight > 0) {
                if (Build.VERSION.SDK_INT >= 33) {
                    val aspectRatio = Rational(videoWidth, videoHeight)
                    builder.setAspectRatio(aspectRatio)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(true)
            if (viewBinding!!.playerView.getClipBounds() != null) {
                builder.setSourceRectHint(viewBinding!!.playerView.getClipBounds())
            }
        }

        setPictureInPictureParams(builder.build())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= 26
                && PictureInPictureUtil.supportsPictureInPicture(this)
                && !PictureInPictureUtil.isInPictureInPictureMode(this)) {
            viewBinding!!.controlsView.hideControls(false)
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean,
                                               newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewBinding!!.playerView.setUseController(!isInPictureInPictureMode)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        val sessionToken = SessionToken(this,
                ComponentName(this, Media3PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture!!.addListener({
            try {
                mediaController = controllerFuture!!.get()
                viewBinding!!.playerView.setPlayer(mediaController)
                setupPictureInPicture()
                setupMedia3Listeners()
            } catch (e: ExecutionException) {
                Log.e(TAG, "Error getting media controller", e)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Error getting media controller", e)
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        if (mediaLoadDisposable != null) {
            mediaLoadDisposable!!.dispose()
        }
        viewBinding!!.playerView.setPlayer(null)
        if (mediaController != null) {
            mediaController!!.release()
            mediaController = null
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture!!)
            controllerFuture = null
        }
        viewBinding!!.controlsView.cancelAutoHide()
    }

    override fun onResume() {
        super.onResume()
        viewBinding!!.playerView.setUseController(false)
    }

    private fun setupMedia3Listeners() {
        if (mediaController == null) {
            return
        }
        mediaController!!.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewBinding!!.controlsView.setPlayButtonShowsPlay(Util.shouldShowPlayButton(mediaController))
                if (isPlaying) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                loadMediaInfo()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                viewBinding!!.controlsView.setPlayButtonShowsPlay(Util.shouldShowPlayButton(mediaController))
                if (playbackState == Player.STATE_BUFFERING) {
                    viewBinding!!.controlsView.setProgressBarVisibility(View.VISIBLE)
                } else {
                    viewBinding!!.controlsView.setProgressBarVisibility(View.INVISIBLE)
                }
            }
        })
        loadMediaInfo()
    }

    private fun loadMediaInfo() {
        if (mediaLoadDisposable != null) {
            mediaLoadDisposable!!.dispose()
        }
        mediaLoadDisposable = Maybe.fromCallable<FeedMedia> {
            DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ media ->
                    currentMedia = media
                    FeedItemMenuHandler.onPrepareMenu(viewBinding!!.controlsView.getToolbar().getMenu(),
                            Collections.singletonList(currentMedia!!.getItem()!!))
                })
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.player_switch_to_audio_only) {
            finish()
            return true
        } else if (item.getItemId() == R.id.player_show_chapters) {
            ChaptersFragment().show(getSupportFragmentManager(), ChaptersFragment.TAG)
            return true
        } else if (item.getItemId() == R.id.transcript_item) {
            TranscriptDialogFragment().show(getSupportFragmentManager(), TranscriptDialogFragment.TAG)
            return true
        } else if (item.getItemId() == R.id.disable_sleeptimer_item
                || item.getItemId() == R.id.set_sleeptimer_item) {
            SleepTimerDialog().show(getSupportFragmentManager(), "SleepTimerDialog")
            return true
        } else if (item.getItemId() == R.id.audio_controls) {
            val dialog = PlaybackControlsDialog.newInstance()
            dialog.show(getSupportFragmentManager(), "playback_controls")
            return true
        } else if (item.getItemId() == R.id.playback_speed) {
            VariableSpeedDialog().show(getSupportFragmentManager(), null)
            return true
        }

        if (currentMedia == null) {
            return false
        }

        if (item.getItemId() == R.id.add_to_favorites_item) {
            DBWriter.addFavoriteItems(Collections.singletonList(currentMedia!!.getItem()!!))
        } else if (item.getItemId() == R.id.remove_from_favorites_item) {
            DBWriter.removeFavoriteItems(Collections.singletonList(currentMedia!!.getItem()!!))
        } else if (item.getItemId() == R.id.open_feed_item) {
            MainActivityStarter(this).withOpenFeed(currentMedia!!.getItem()!!.getFeedId())
                    .withClearTop().start()
        } else if (item.getItemId() == R.id.visit_website_item) {
            IntentUtils.openInBrowser(this, getWebsiteLinkWithFallback(currentMedia!!)!!)
        } else if (item.getItemId() == R.id.share_item) {
            ShareDialog.newInstance(currentMedia!!.getItem()!!).show(getSupportFragmentManager(), "ShareDialog")
        } else {
            return false
        }
        return true
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun itemChanged(event: FeedItemEvent) {
        if (currentMedia != null && FeedItemEvent.indexOfItemWithId(event.items, currentMedia!!.getItemId()) != -1) {
            loadMediaInfo()
        }
    }

    private fun getWebsiteLinkWithFallback(media: FeedMedia?): String? {
        if (media == null) {
            return null
        } else if (StringUtils.isNotBlank(media.getWebsiteLink())) {
            return media.getWebsiteLink()
        } else {
            return media.getItem()!!.getFeed()!!.getLink()
        }
    }

    companion object {
        private const val TAG = "M3VideoPlayerActivity"
    }
}
