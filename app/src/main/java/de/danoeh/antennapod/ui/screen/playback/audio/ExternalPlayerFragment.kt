package de.danoeh.antennapod.ui.screen.playback.audio

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.util.Consumer
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.danoeh.antennapod.BuildConfig
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.playback.service.PlaybackService
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils
import de.danoeh.antennapod.ui.screen.playback.PlayButton
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Fragment which is supposed to be displayed outside of the MediaplayerActivity.
 */
class ExternalPlayerFragment : Fragment() {

    private var imgvCover: ImageView? = null
    private var txtvTitle: TextView? = null
    private var butPlay: PlayButton? = null
    private var feedName: TextView? = null
    private var progressBar: ProgressBar? = null
    private var disposable: Disposable? = null
    private var currentMedia: Playable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.external_player_fragment, container, false)
        imgvCover = root.findViewById(R.id.imgvCover)
        txtvTitle = root.findViewById(R.id.txtvTitle)
        butPlay = root.findViewById(R.id.butPlay)
        feedName = root.findViewById(R.id.txtvAuthor)
        progressBar = root.findViewById(R.id.episodeProgress)

        root.findViewById<View>(R.id.fragmentLayout).setOnClickListener {
            Log.d(TAG, "layoutInfo was clicked")

            if (currentMedia != null) {
                if (currentMedia!!.getMediaType() == MediaType.AUDIO) {
                    (getActivity() as MainActivity).getBottomSheet().setState(BottomSheetBehavior.STATE_EXPANDED)
                } else {
                    val intent = PlaybackService.getPlayerActivityIntent(getActivity()!!, currentMedia!!)
                    startActivity(intent)
                }
            }
        }
        butPlay!!.setOnClickListener {
            if (PlaybackService.isRunning
                    && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING) {
                if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                    PlaybackController.bindToMedia3Service(getContext()!!, Consumer { controller -> controller.pause() })
                } else {
                    getContext()!!.sendBroadcast(
                            MediaButtonStarter.createIntent(getContext()!!, KeyEvent.KEYCODE_MEDIA_PAUSE))
                }
            } else {
                PlaybackServiceStarter(getContext()!!, currentMedia)
                        .callEvenIfRunning(true)
                        .start()
            }
        }
        return root
    }

    override fun onStart() {
        super.onStart()
        loadMediaInfo()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusEvent(event: PlayerStatusEvent) {
        loadMediaInfo()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPositionObserverUpdate(event: PlaybackPositionEvent) {
        if (event.getPosition() == Playable.INVALID_TIME || event.getDuration() == Playable.INVALID_TIME) {
            return
        }
        progressBar!!.setProgress((event.getPosition().toDouble() / event.getDuration() * 100).toInt())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackServiceChanged(event: PlaybackServiceEvent) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN) {
            (getActivity() as MainActivity).setPlayerVisible(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Fragment is about to be destroyed")
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    private fun loadMediaInfo() {
        Log.d(TAG, "Loading media info")

        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Maybe.fromCallable<FeedMedia> {
            DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateUi,
                        { error -> Log.e(TAG, Log.getStackTraceString(error)) },
                        { (getActivity() as MainActivity).setPlayerVisible(false) })
    }

    private fun updateUi(media: Playable?) {
        if (media == null) {
            return
        }
        currentMedia = media
        (getActivity() as MainActivity).setPlayerVisible(true)
        txtvTitle!!.setText(media.getEpisodeTitle())
        feedName!!.setText(media.getFeedTitle())
        onPositionObserverUpdate(PlaybackPositionEvent(media.getPosition(), media.getDuration()))
        val isPlaying = PlaybackService.isRunning
                && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING
        butPlay!!.setIsShowPlay(!isPlaying)

        val options = RequestOptions()
                .placeholder(R.color.light_gray)
                .error(R.color.light_gray)
                .fitCenter()
                .dontAnimate()

        Glide.with(this)
                .load(ImageResourceUtils.getEpisodeListImageLocation(media))
                .error(Glide.with(this)
                        .load(ImageResourceUtils.getFallbackImageLocation(media))
                        .apply(options))
                .apply(options)
                .into(imgvCover!!)

        if (currentMedia!!.getMediaType() == MediaType.VIDEO) {
            butPlay!!.setVisibility(View.GONE)
            (getActivity() as MainActivity).getBottomSheet().setLocked(true)
            (getActivity() as MainActivity).getBottomSheet().setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else {
            butPlay!!.setVisibility(View.VISIBLE)
            (getActivity() as MainActivity).getBottomSheet().setLocked(false)
        }
    }

    companion object {
        const val TAG = "ExternalPlayerFragment"
    }
}
