package de.danoeh.antennapod.ui.screen.playback.audio

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.util.Consumer
import androidx.fragment.app.Fragment
import androidx.media3.session.MediaController
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import de.danoeh.antennapod.BuildConfig
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.CoverFragmentBinding
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.EmbeddedChapterImage
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.playback.service.PlaybackService
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter
import de.danoeh.antennapod.ui.chapters.ChapterUtils
import de.danoeh.antennapod.ui.common.DateFormatter
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils
import de.danoeh.antennapod.ui.screen.chapter.ChaptersFragment
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Displays the cover and the title of a FeedItem.
 */
class CoverFragment : Fragment() {
    private var viewBinding: CoverFragmentBinding? = null
    private var disposable: Disposable? = null
    private var displayedChapterIndex = -1
    private var media: Playable? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = CoverFragmentBinding.inflate(inflater)
        viewBinding!!.imgvCover.setOnClickListener {
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                if (PlaybackService.isRunning) {
                    PlaybackController.bindToMedia3Service(getActivity()!!, MediaController::pause)
                } else if (media != null) {
                    PlaybackServiceStarter(getContext()!!, media!!)
                            .callEvenIfRunning(true)
                            .start()
                }
                return@setOnClickListener
            }
            if (PlaybackService.isRunning
                    && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING) {
                getContext()!!.sendBroadcast(MediaButtonStarter.createIntent(getContext()!!, KeyEvent.KEYCODE_MEDIA_PAUSE))
            } else if (media != null) {
                PlaybackServiceStarter(getContext()!!, media!!)
                        .callEvenIfRunning(true)
                        .start()
            }
        }
        viewBinding!!.openDescription.setOnClickListener { view ->
            (requireParentFragment() as AudioPlayerFragment)
                    .scrollToPage(AudioPlayerFragment.POS_DESCRIPTION, true) }
        val colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                viewBinding!!.txtvPodcastTitle.getCurrentTextColor(), BlendModeCompat.SRC_IN)
        viewBinding!!.butNextChapter.setColorFilter(colorFilter)
        viewBinding!!.butPrevChapter.setColorFilter(colorFilter)
        viewBinding!!.descriptionIcon.setColorFilter(colorFilter)
        viewBinding!!.chapterButton.setOnClickListener {
            ChaptersFragment().show(getChildFragmentManager(), ChaptersFragment.TAG)
        }
        viewBinding!!.butPrevChapter.setOnClickListener { seekToPrevChapter() }
        viewBinding!!.butNextChapter.setOnClickListener { seekToNextChapter() }
        return viewBinding!!.getRoot()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configureForOrientation(getResources().getConfiguration())
    }

    private fun loadMediaInfo(includingChapters: Boolean) {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Maybe.create<Playable>({ emitter ->
            val media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
            if (media != null) {
                if (includingChapters) {
                    ChapterUtils.loadChapters(media, getContext()!!, false)
                }
                emitter.onSuccess(media)
            } else {
                emitter.onComplete()
            }
        }).subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ media ->
                    this.media = media
                    displayMediaInfo(media)
                    if (media.getChapters() == null && !includingChapters) {
                        loadMediaInfo(true)
                    }
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun displayMediaInfo(media: Playable) {
        val pubDateStr = DateFormatter.formatAbbrev(getActivity()!!, media.getPubDate())
        viewBinding!!.txtvPodcastTitle.setText(StringUtils.stripToEmpty(media.getFeedTitle())
                + "\u00A0"
                + "・"
                + "\u00A0"
                + StringUtils.replace(StringUtils.stripToEmpty(pubDateStr), " ", "\u00A0"))
        if (media is FeedMedia) {
            viewBinding!!.txtvPodcastTitle.setOnClickListener { v -> openFeed((media as FeedMedia).getItem()!!.getFeed()) }
        } else {
            viewBinding!!.txtvPodcastTitle.setOnClickListener(null)
        }
        viewBinding!!.txtvPodcastTitle.setOnLongClickListener { copyText(media.getFeedTitle()) }
        viewBinding!!.txtvEpisodeTitle.setText(media.getEpisodeTitle())
        viewBinding!!.txtvEpisodeTitle.setOnLongClickListener { copyText(media.getEpisodeTitle()) }
        viewBinding!!.txtvEpisodeTitle.setOnClickListener {
            val lines = viewBinding!!.txtvEpisodeTitle.getLineCount()
            val animUnit = 1500
            if (lines > viewBinding!!.txtvEpisodeTitle.getMaxLines()) {
                val titleHeight = viewBinding!!.txtvEpisodeTitle.getHeight()
                        - viewBinding!!.txtvEpisodeTitle.getPaddingTop()
                        - viewBinding!!.txtvEpisodeTitle.getPaddingBottom()
                val verticalMarquee = ObjectAnimator.ofInt(
                        viewBinding!!.txtvEpisodeTitle, "scrollY", 0,
                        (lines - viewBinding!!.txtvEpisodeTitle.getMaxLines())
                                * (titleHeight / viewBinding!!.txtvEpisodeTitle.getMaxLines()))
                        .setDuration((lines * animUnit).toLong())
                val fadeOut = ObjectAnimator.ofFloat(
                        viewBinding!!.txtvEpisodeTitle, "alpha", 0f)
                fadeOut.setStartDelay(animUnit.toLong())
                fadeOut.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        viewBinding!!.txtvEpisodeTitle.scrollTo(0, 0)
                    }
                })
                val fadeBackIn = ObjectAnimator.ofFloat(
                        viewBinding!!.txtvEpisodeTitle, "alpha", 1f)
                val set = AnimatorSet()
                set.playSequentially(verticalMarquee, fadeOut, fadeBackIn)
                set.start()
            }
        }

        displayedChapterIndex = -1
        refreshChapterData(Chapter.getAfterPosition(media.getChapters(), media.getPosition()))
        updateChapterControlVisibility()
    }

    private fun openFeed(feed: Feed?) {
        if (feed == null) {
            return
        }
        if (feed.getState() == Feed.STATE_NOT_SUBSCRIBED) {
            startActivity(OnlineFeedviewActivityStarter(getContext()!!, feed.getDownloadUrl()!!).getIntent())
        } else {
            MainActivityStarter(getContext()!!).withOpenFeed(feed.getId()).withClearTop().start()
        }
    }

    private fun updateChapterControlVisibility() {
        var chapterControlVisible = false
        if (media!!.getChapters() != null) {
            chapterControlVisible = media!!.getChapters()!!.size > 0
        } else if (media is FeedMedia) {
            val fm = media as FeedMedia
            // If an item has chapters but they are not loaded yet, still display the button.
            chapterControlVisible = fm.getItem() != null && fm.getItem()!!.hasChapters()
        }
        val newVisibility = if (chapterControlVisible) View.VISIBLE else View.GONE
        if (viewBinding!!.chapterButton.getVisibility() != newVisibility) {
            viewBinding!!.chapterButton.setVisibility(newVisibility)
            ObjectAnimator.ofFloat(viewBinding!!.chapterButton,
                    "alpha",
                    if (chapterControlVisible) 0f else 1f,
                    if (chapterControlVisible) 1f else 0f)
                    .start()
        }
    }

    private fun refreshChapterData(chapterIndex: Int) {
        val chapters = media!!.getChapters()
        if (chapterIndex > -1 && chapters != null) {
            if (media!!.getPosition() > media!!.getDuration() || chapterIndex >= chapters.size - 1) {
                displayedChapterIndex = chapters.size - 1
                viewBinding!!.butNextChapter.setVisibility(View.INVISIBLE)
            } else {
                displayedChapterIndex = chapterIndex
                viewBinding!!.butNextChapter.setVisibility(View.VISIBLE)
            }
        }

        displayCoverImage()
    }

    private fun getCurrentChapter(): Chapter? {
        if (media == null || media!!.getChapters() == null || displayedChapterIndex == -1) {
            return null
        }
        return media!!.getChapters()!!.get(displayedChapterIndex)
    }

    private fun seekToPrevChapter() {
        val curr = getCurrentChapter()

        if (curr == null || displayedChapterIndex == -1) {
            return
        }

        PlaybackController.bindToMedia3Service(getActivity()!!, Consumer { controller ->
            if (displayedChapterIndex < 1) {
                controller.seekTo(0)
            } else if ((controller.getCurrentPosition().toFloat() - 10000 * controller.getPlaybackParameters().speed)
                    < curr.getStart().toFloat()) {
                refreshChapterData(displayedChapterIndex - 1)
                controller.seekTo(media!!.getChapters()!!.get(displayedChapterIndex).getStart())
            } else {
                controller.seekTo(curr.getStart())
            }
        })
    }

    private fun seekToNextChapter() {
        if (media == null || media!!.getChapters() == null
                || displayedChapterIndex == -1 || displayedChapterIndex + 1 >= media!!.getChapters()!!.size) {
            return
        }

        refreshChapterData(displayedChapterIndex + 1)
        PlaybackController.bindToMedia3Service(getActivity()!!, Consumer { controller ->
            controller.seekTo(media!!.getChapters()!!.get(displayedChapterIndex).getStart())
        })
    }

    override fun onStart() {
        super.onStart()
        loadMediaInfo(false)
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()

        EventBus.getDefault().unregister(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (disposable != null) {
            disposable!!.dispose()
        }
        viewBinding = null
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusEvent(event: PlayerStatusEvent) {
        loadMediaInfo(false)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        if (media == null) {
            return
        }
        val newChapterIndex = Chapter.getAfterPosition(media!!.getChapters(), event.getPosition())
        if (newChapterIndex > -1 && newChapterIndex != displayedChapterIndex) {
            refreshChapterData(newChapterIndex)
        }
    }

    private fun displayCoverImage() {
        val options = RequestOptions()
                .dontAnimate()
                .transform(FitCenter(),
                        RoundedCorners((16 * getResources().getDisplayMetrics().density).toInt()))

        val cover: RequestBuilder<Drawable> = Glide.with(this)
                .load(media!!.getImageLocation())
                .error(Glide.with(this)
                        .load(ImageResourceUtils.getFallbackImageLocation(media!!))
                        .apply(options))
                .apply(options)

        if (displayedChapterIndex == -1 || media == null || media!!.getChapters() == null
                || TextUtils.isEmpty(media!!.getChapters()!!.get(displayedChapterIndex).getImageUrl())) {
            cover.into(viewBinding!!.imgvCover)
        } else {
            Glide.with(this)
                    .load(EmbeddedChapterImage.getModelFor(media!!, displayedChapterIndex))
                    .apply(options)
                    .thumbnail(cover)
                    .error(cover)
                    .into(viewBinding!!.imgvCover)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configureForOrientation(newConfig)
    }

    private fun configureForOrientation(newConfig: Configuration) {
        val isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT

        viewBinding!!.coverFragment.setOrientation(if (isPortrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL)

        if (isPortrait) {
            viewBinding!!.coverHolder.setLayoutParams(LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            viewBinding!!.coverFragmentTextContainer.setLayoutParams(
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        } else {
            viewBinding!!.coverHolder.setLayoutParams(LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
            viewBinding!!.coverFragmentTextContainer.setLayoutParams(LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        }

        (viewBinding!!.episodeDetails.getParent() as ViewGroup).removeView(viewBinding!!.episodeDetails)
        if (isPortrait) {
            viewBinding!!.coverFragment.addView(viewBinding!!.episodeDetails)
        } else {
            viewBinding!!.coverFragmentTextContainer.addView(viewBinding!!.episodeDetails)
        }
    }

    private fun copyText(text: String?): Boolean {
        val clipboardManager = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("AntennaPod", text))
        }
        if (Build.VERSION.SDK_INT <= 32) {
            EventBus.getDefault().post(MessageEvent(getString(R.string.copied_to_clipboard)))
        }
        return true
    }

    companion object {
        private const val TAG = "CoverFragment"
    }
}
