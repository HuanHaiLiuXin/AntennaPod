package de.danoeh.antennapod.ui.screen.playback.audio

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.util.Consumer
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.media3.session.MediaController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.danoeh.antennapod.BuildConfig
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.PlayerErrorEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.playback.BufferUpdateEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent
import de.danoeh.antennapod.event.playback.SpeedChangedEvent
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.cast.CastEnabledActivity
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.playback.service.PlaybackService
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter
import de.danoeh.antennapod.ui.chapters.ChapterUtils
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils
import de.danoeh.antennapod.ui.episodes.TimeSpeedConverter
import de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler
import de.danoeh.antennapod.ui.screen.feed.preferences.SkipPreferenceDialog
import de.danoeh.antennapod.ui.screen.playback.MediaPlayerErrorDialog
import de.danoeh.antennapod.ui.screen.playback.PlayButton
import de.danoeh.antennapod.ui.screen.playback.SleepTimerDialog
import de.danoeh.antennapod.ui.screen.playback.TranscriptDialogFragment
import de.danoeh.antennapod.ui.screen.playback.VariableSpeedDialog
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Collections

/**
 * Shows the audio player.
 */
class AudioPlayerFragment : Fragment(),
        SeekBar.OnSeekBarChangeListener, Toolbar.OnMenuItemClickListener {
    private var txtvPlaybackSpeed: TextView? = null
    private var pager: ViewPager2? = null
    private var txtvPosition: TextView? = null
    private var txtvLength: TextView? = null
    private var sbPosition: ChapterSeekBar? = null
    private var butRev: ImageButton? = null
    private var txtvRev: TextView? = null
    private var butPlay: PlayButton? = null
    private var butFF: ImageButton? = null
    private var txtvFF: TextView? = null
    private var butSkip: ImageButton? = null
    private var toolbar: MaterialToolbar? = null
    private var progressIndicator: ProgressBar? = null
    private var cardViewSeek: CardView? = null
    private var txtvSeek: TextView? = null

    private var currentMedia: FeedMedia? = null
    private var disposable: Disposable? = null
    private var showTimeLeft = false
    private var seekedToChapterStart = false
    private var currentChapterIndex = -1

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val root = inflater.inflate(R.layout.audioplayer_fragment, container, false)
        root.setOnTouchListener { v, event -> true } // Avoid clicks going through player to fragments below
        toolbar = root.findViewById(R.id.toolbar)
        toolbar!!.setTitle("")
        toolbar!!.setNavigationOnClickListener {
            (getActivity() as MainActivity).getBottomSheet().setState(BottomSheetBehavior.STATE_COLLAPSED)
        }
        toolbar!!.setOnMenuItemClickListener(this)
        toolbar!!.inflateMenu(R.menu.mediaplayer)

        val externalPlayerFragment = ExternalPlayerFragment()
        getChildFragmentManager().beginTransaction()
                .replace(R.id.playerFragment, externalPlayerFragment, ExternalPlayerFragment.TAG)
                .commit()

        txtvPlaybackSpeed = root.findViewById(R.id.txtvPlaybackSpeed)
        sbPosition = root.findViewById(R.id.sbPosition)
        txtvPosition = root.findViewById(R.id.txtvPosition)
        txtvLength = root.findViewById(R.id.txtvLength)
        butRev = root.findViewById(R.id.butRev)
        txtvRev = root.findViewById(R.id.txtvRev)
        butPlay = root.findViewById(R.id.butPlay)
        butFF = root.findViewById(R.id.butFF)
        txtvFF = root.findViewById(R.id.txtvFF)
        butSkip = root.findViewById(R.id.butSkip)
        progressIndicator = root.findViewById(R.id.progLoading)
        cardViewSeek = root.findViewById(R.id.cardViewSeek)
        txtvSeek = root.findViewById(R.id.txtvSeek)

        setupLengthTextView()
        setupControlButtons()
        val butPlaybackSpeed = root.findViewById<ImageButton>(R.id.butPlaybackSpeed)
        butPlaybackSpeed.setOnClickListener { VariableSpeedDialog().show(getChildFragmentManager(), null) }
        sbPosition!!.setOnSeekBarChangeListener(this)

        pager = root.findViewById(R.id.pager)
        pager!!.setAdapter(AudioPlayerPagerAdapter(this))
        //noinspection WrongConstant
        pager!!.setOffscreenPageLimit(NUM_CONTENT_FRAGMENTS)
        pager!!.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pager!!.post {
                    if (getActivity() != null) {
                        // By the time this is posted, the activity might be closed again.
                        (getActivity() as MainActivity).getBottomSheet().updateScrollingChild()
                    }
                }
            }
        })

        return root
    }

    private fun setChapterDividers() {
        if (currentMedia == null) {
            return
        }

        var dividerPos: FloatArray? = null

        if (currentMedia!!.getChapters() != null && !currentMedia!!.getChapters()!!.isEmpty()) {
            val chapters = currentMedia!!.getChapters()!!
            val duration = currentMedia!!.getDuration()
            if (duration > 0) {
                dividerPos = FloatArray(chapters.size)
                for (i in chapters.indices) {
                    dividerPos!![i] = chapters.get(i).getStart() / duration.toFloat()
                }
            }
        }

        sbPosition!!.setDividerPos(dividerPos)
    }

    private fun setupControlButtons() {
        butRev!!.setOnClickListener {
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                PlaybackController.bindToMedia3Service(requireContext(), MediaController::seekBack)
            } else {
                PlaybackController.bindToService(requireActivity(), Consumer { playbackService ->
                    playbackService.seekTo(playbackService.getCurrentPosition()
                            - UserPreferences.getRewindSecs() * 1000)
                })
            }
        }
        butRev!!.setOnLongClickListener {
            SkipPreferenceDialog.showSkipPreference(requireContext(),
                    SkipPreferenceDialog.SkipDirection.SKIP_REWIND, txtvRev)
            true
        }
        butPlay!!.setOnClickListener {
            if (PlaybackService.isRunning
                    && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING) {
                if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                    PlaybackController.bindToMedia3Service(requireContext(), MediaController::pause)
                } else {
                    requireActivity().sendBroadcast(
                            MediaButtonStarter.createIntent(requireContext(), KeyEvent.KEYCODE_MEDIA_PAUSE))
                }
            } else {
                PlaybackServiceStarter(requireContext(), currentMedia)
                        .callEvenIfRunning(true)
                        .start()
            }
        }
        butFF!!.setOnClickListener {
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                PlaybackController.bindToMedia3Service(requireContext(), MediaController::seekForward)
            } else {
                PlaybackController.bindToService(requireActivity(), Consumer { playbackService ->
                    playbackService.seekTo(playbackService.getCurrentPosition()
                            + UserPreferences.getFastForwardSecs() * 1000)
                })
            }
        }
        butFF!!.setOnLongClickListener {
            SkipPreferenceDialog.showSkipPreference(requireContext(),
                    SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, txtvFF)
            false
        }
        butSkip!!.setOnClickListener {
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                PlaybackController.bindToMedia3Service(requireContext(), MediaController::seekToNextMediaItem)
            } else {
                requireActivity().sendBroadcast(
                        MediaButtonStarter.createIntent(requireContext(), KeyEvent.KEYCODE_MEDIA_NEXT))
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemsUpdate(event: FeedItemEvent) {
        if (currentMedia == null) {
            return
        }
        if (FeedItemEvent.indexOfItemWithId(event.items, currentMedia!!.getItemId()) != -1) {
            this@AudioPlayerFragment.loadMediaInfo(false)
        }
        if (event.items.isEmpty()) {
            // The unread update event is sometimes abused to trigger UI updates
            updatePosition(PlaybackPositionEvent(currentMedia!!.getPosition(),
                    currentMedia!!.getDuration()))
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackServiceChanged(event: PlaybackServiceEvent) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN) {
            (getActivity() as MainActivity).getBottomSheet().setState(BottomSheetBehavior.STATE_COLLAPSED)
        }
    }

    private fun setupLengthTextView() {
        showTimeLeft = UserPreferences.shouldShowRemainingTime()
        txtvLength!!.setOnClickListener {
            if (currentMedia == null) {
                return@setOnClickListener
            }
            showTimeLeft = !showTimeLeft
            UserPreferences.setShowRemainTimeSetting(showTimeLeft)
            updatePosition(PlaybackPositionEvent(currentMedia!!.getPosition(),
                    currentMedia!!.getDuration()))
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updatePlaybackSpeedButton(event: SpeedChangedEvent) {
        val speedStr = DecimalFormat("0.00").format(event.getNewSpeed())
        txtvPlaybackSpeed!!.setText(speedStr)
    }

    private fun loadMediaInfo(includingChapters: Boolean) {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Maybe.create<FeedMedia>({ emitter ->
            val media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
            if (media != null) {
                if (includingChapters) {
                    ChapterUtils.loadChapters(media, requireContext(), false)
                }
                emitter.onSuccess(media)
            } else {
                emitter.onComplete()
            }
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ media ->
                    currentMedia = media
                    updateUi()
                    if (media.getChapters() == null && !includingChapters) {
                        loadMediaInfo(true)
                    }
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun updateUi() {
        if (currentMedia == null) {
            return
        }
        updatePosition(PlaybackPositionEvent(currentMedia!!.getPosition(), currentMedia!!.getDuration()))
        updatePlaybackSpeedButton(SpeedChangedEvent(PlaybackSpeedUtils.getCurrentPlaybackSpeed(currentMedia!!)))
        setChapterDividers()
        setupOptionsMenu()
        val isPlaying = PlaybackService.isRunning
                && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING
        butPlay!!.setIsShowPlay(!isPlaying)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusEvent(event: PlayerStatusEvent) {
        loadMediaInfo(false)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    fun sleepTimerUpdate(event: SleepTimerUpdatedEvent) {
        val timerActive = !event.isCancelled() && !event.isOver()
        toolbar!!.getMenu().findItem(R.id.set_sleeptimer_item)!!.setVisible(!timerActive)
        toolbar!!.getMenu().findItem(R.id.disable_sleeptimer_item)!!.setVisible(timerActive)
        if (event.isCancelled() || event.wasJustEnabled() || event.isOver()) {
            this@AudioPlayerFragment.loadMediaInfo(false)
        }
    }

    override fun onStart() {
        super.onStart()
        loadMediaInfo(false)
        EventBus.getDefault().register(this)
        txtvRev!!.setText(NumberFormat.getInstance().format(UserPreferences.getRewindSecs()))
        txtvFF!!.setText(NumberFormat.getInstance().format(UserPreferences.getFastForwardSecs()))
    }

    override fun onStop() {
        super.onStop()
        progressIndicator!!.setVisibility(View.GONE)
        EventBus.getDefault().unregister(this)
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    fun bufferUpdate(event: BufferUpdateEvent) {
        if (event.hasStarted()) {
            progressIndicator!!.setVisibility(View.VISIBLE)
        } else if (event.hasEnded()) {
            progressIndicator!!.setVisibility(View.GONE)
        } else if (currentMedia != null && !currentMedia!!.localFileAvailable()) {
            sbPosition!!.setSecondaryProgress((event.getProgress() * sbPosition!!.getMax()).toInt())
        } else {
            sbPosition!!.setSecondaryProgress(0)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updatePosition(event: PlaybackPositionEvent) {
        if (txtvPosition == null || txtvLength == null || sbPosition == null) {
            return
        }

        val playbackSpeed = if (currentMedia != null) PlaybackSpeedUtils.getCurrentPlaybackSpeed(currentMedia!!) else 1.0f
        val converter = TimeSpeedConverter(playbackSpeed)
        val convertedPosition = converter.convert(event.getPosition())
        val convertedDuration = converter.convert(event.getDuration())
        val remainingTime = converter.convert(Math.max(event.getDuration() - event.getPosition(), 0))
        if (currentMedia != null) {
            currentChapterIndex = Chapter.getAfterPosition(currentMedia!!.getChapters(), convertedPosition)
        }
        Log.d(TAG, "currentPosition " + Converter.getDurationStringLong(convertedPosition))
        if (convertedPosition == Playable.INVALID_TIME || convertedDuration == Playable.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time")
            return
        }
        txtvPosition!!.setText(Converter.getDurationStringLong(convertedPosition))
        txtvPosition!!.setContentDescription(getString(R.string.position,
                Converter.getDurationStringLocalized(requireContext(), convertedPosition.toLong())))
        showTimeLeft = UserPreferences.shouldShowRemainingTime()
        if (showTimeLeft) {
            txtvLength!!.setContentDescription(getString(R.string.remaining_time,
                    Converter.getDurationStringLocalized(requireContext(), remainingTime.toLong())))
            txtvLength!!.setText((if (remainingTime > 0) "-" else "") + Converter.getDurationStringLong(remainingTime))
        } else {
            txtvLength!!.setContentDescription(getString(R.string.chapter_duration,
                    Converter.getDurationStringLocalized(requireContext(), convertedDuration.toLong())))
            txtvLength!!.setText(Converter.getDurationStringLong(convertedDuration))
        }

        if (!sbPosition!!.isPressed()) {
            val progress = event.getPosition().toFloat() / event.getDuration()
            sbPosition!!.setProgress((progress * sbPosition!!.getMax()).toInt())
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun mediaPlayerError(event: PlayerErrorEvent) {
        MediaPlayerErrorDialog.show(requireActivity(), event)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (currentMedia == null || txtvLength == null) {
            return
        }

        if (fromUser) {
            val prog = progress / seekBar.getMax().toFloat()
            val playbackSpeed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(currentMedia!!)
            val converter = TimeSpeedConverter(playbackSpeed)
            val duration = currentMedia!!.getDuration()
            var position = converter.convert((prog * duration).toInt())
            val newChapterIndex = Chapter.getAfterPosition(currentMedia!!.getChapters(), position)
            if (newChapterIndex > -1) {
                if (!sbPosition!!.isPressed() && currentChapterIndex != newChapterIndex) {
                    currentChapterIndex = newChapterIndex
                    position = currentMedia!!.getChapters()!!.get(currentChapterIndex).getStart().toInt()
                    seekedToChapterStart = true
                    val positionFinal = position
                    if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                        PlaybackController.bindToMedia3Service(requireContext(), Consumer { controller ->
                            controller.seekTo(positionFinal.toLong())
                        })
                    } else {
                        PlaybackController.bindToService(requireActivity(), Consumer { playbackService ->
                            playbackService.seekTo(positionFinal)
                        })
                    }
                    sbPosition!!.highlightCurrentChapter()
                }
                txtvSeek!!.setText(currentMedia!!.getChapters()!!.get(newChapterIndex).getTitle()
                        + "\n" + Converter.getDurationStringLong(position))
            } else {
                txtvSeek!!.setText(Converter.getDurationStringLong(position))
            }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        // interrupt position Observer, restart later
        cardViewSeek!!.setScaleX(.8f)
        cardViewSeek!!.setScaleY(.8f)
        cardViewSeek!!.animate()
                .setInterpolator(FastOutSlowInInterpolator())
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(200)
                .start()
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (seekedToChapterStart) {
            seekedToChapterStart = false
        } else if (currentMedia != null) {
            val prog = seekBar.getProgress() / seekBar.getMax().toFloat()
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                PlaybackController.bindToMedia3Service(requireContext(), Consumer { controller ->
                    controller.seekTo((controller.getDuration() * prog).toLong())
                })
            } else {
                PlaybackController.bindToService(requireActivity(), Consumer { playbackService ->
                    playbackService.seekTo((playbackService.getDuration() * prog).toInt())
                })
            }
        }
        cardViewSeek!!.setScaleX(1f)
        cardViewSeek!!.setScaleY(1f)
        cardViewSeek!!.animate()
                .setInterpolator(FastOutSlowInInterpolator())
                .alpha(0f).scaleX(.8f).scaleY(.8f)
                .setDuration(200)
                .start()
    }

    fun setupOptionsMenu() {
        toolbar!!.getMenu().findItem(R.id.open_feed_item)!!.setVisible(true)
        FeedItemMenuHandler.onPrepareMenu(toolbar!!.getMenu(),
                Collections.singletonList(currentMedia!!.getItem()!!))
        (getActivity() as CastEnabledActivity).requestCastButton(toolbar!!.getMenu())
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (currentMedia == null) {
            return false
        }

        val feedItem: FeedItem? = currentMedia!!.getItem()
        if (feedItem != null && FeedItemMenuHandler.onMenuItemClicked(this, item.getItemId(), feedItem)) {
            return true
        }

        val itemId = item.getItemId()
        if (itemId == R.id.disable_sleeptimer_item || itemId == R.id.set_sleeptimer_item) {
            SleepTimerDialog().show(getChildFragmentManager(), "SleepTimerDialog")
            return true
        } else if (itemId == R.id.transcript_item) {
            TranscriptDialogFragment().show(
                    requireActivity().getSupportFragmentManager(), TranscriptDialogFragment.TAG)
            return true
        } else if (itemId == R.id.open_feed_item) {
            if (feedItem != null) {
                openFeed(feedItem.getFeed())
            }
            return true
        }
        return false
    }

    private fun openFeed(feed: Feed?) {
        if (feed == null) {
            return
        }
        if (feed.getState() == Feed.STATE_NOT_SUBSCRIBED) {
            startActivity(OnlineFeedviewActivityStarter(requireContext(), feed.getDownloadUrl()!!).getIntent())
        } else {
            MainActivityStarter(requireContext()).withOpenFeed(feed.getId()).withClearTop().start()
        }
    }

    fun fadePlayerToToolbar(slideOffset: Float) {
        val playerFadeProgress = Math.max(0.0f, Math.min(0.2f, slideOffset - 0.2f)) / 0.2f
        val player = requireView().findViewById<View>(R.id.playerFragment)
        player.setAlpha(1 - playerFadeProgress)
        player.setVisibility(if (playerFadeProgress > 0.99f) View.INVISIBLE else View.VISIBLE)
        val toolbarFadeProgress = Math.max(0.0f, Math.min(0.2f, slideOffset - 0.6f)) / 0.2f
        toolbar!!.setAlpha(toolbarFadeProgress)
        toolbar!!.setVisibility(if (toolbarFadeProgress < 0.01f) View.INVISIBLE else View.VISIBLE)
    }

    fun scrollToPage(page: Int, smoothScroll: Boolean) {
        if (pager == null) {
            return
        }

        pager!!.setCurrentItem(page, smoothScroll)

        val visibleChild = getChildFragmentManager().findFragmentByTag("f" + POS_DESCRIPTION)
        if (visibleChild is ItemDescriptionFragment) {
            visibleChild.scrollToTop()
        }
    }

    fun scrollToPage(page: Int) {
        scrollToPage(page, false)
    }

    private class AudioPlayerPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            Log.d(TAG, "getItem(" + position + ")")

            return when (position) {
                POS_COVER -> CoverFragment()
                else -> ItemDescriptionFragment()
            }
        }

        override fun getItemCount(): Int {
            return NUM_CONTENT_FRAGMENTS
        }

        companion object {
            private const val TAG = "AudioPlayerPagerAdapter"
        }
    }

    companion object {
        const val TAG = "AudioPlayerFragment"
        const val POS_COVER = 0
        const val POS_DESCRIPTION = 1
        private const val NUM_CONTENT_FRAGMENTS = 2
    }
}
