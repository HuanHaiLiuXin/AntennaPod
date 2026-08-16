package de.danoeh.antennapod.ui.screen.episode

import android.content.Context
import android.os.Bundle
import android.text.Layout
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.balloon.ArrowOrientationRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import de.danoeh.antennapod.BuildConfig
import de.danoeh.antennapod.R
import de.danoeh.antennapod.actionbutton.CancelDownloadActionButton
import de.danoeh.antennapod.actionbutton.DeleteActionButton
import de.danoeh.antennapod.actionbutton.DownloadActionButton
import de.danoeh.antennapod.actionbutton.ItemActionButton
import de.danoeh.antennapod.actionbutton.MarkAsPlayedActionButton
import de.danoeh.antennapod.actionbutton.PauseActionButton
import de.danoeh.antennapod.actionbutton.PlayActionButton
import de.danoeh.antennapod.actionbutton.PlayLocalActionButton
import de.danoeh.antennapod.actionbutton.StreamActionButton
import de.danoeh.antennapod.actionbutton.VisitWebsiteActionButton
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.databinding.FeeditemFragmentBinding
import de.danoeh.antennapod.ui.common.ClipboardUtils
import de.danoeh.antennapod.event.EpisodeDownloadEvent
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.playback.service.PlaybackService
import de.danoeh.antennapod.playback.service.PlaybackStatus
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.UsageStatistics
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter
import de.danoeh.antennapod.ui.cleaner.ShownotesCleaner
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.common.DateFormatter
import de.danoeh.antennapod.ui.common.ImagePlaceholder
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.Collections
import java.util.Locale

/**
 * Displays information about a FeedItem and actions.
 */
class ItemFragment : Fragment() {

    companion object {
        private const val TAG = "ItemFragment"
        private const val ARG_FEEDITEM = "feeditem"

        /**
         * Creates a new instance of an ItemFragment
         *
         * @param feeditem The ID of the FeedItem to show
         * @return The ItemFragment instance
         */
        @JvmStatic
        fun newInstance(feeditem: Long): ItemFragment {
            val fragment = ItemFragment()
            val args = Bundle()
            args.putLong(ARG_FEEDITEM, feeditem)
            fragment.setArguments(args)
            return fragment
        }
    }

    private var itemsLoaded = false
    private var itemId = 0L
    private var item: FeedItem? = null
    private var webviewData: String? = null

    private var actionButton1: ItemActionButton? = null
    private var actionButton2: ItemActionButton? = null
    private var disposable: Disposable? = null
    private var viewBinding: FeeditemFragmentBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        itemId = requireArguments().getLong(ARG_FEEDITEM)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        viewBinding = FeeditemFragmentBinding.inflate(inflater, container, false)
        viewBinding!!.header.setVisibility(View.INVISIBLE)
        viewBinding!!.txtvPodcast.setOnClickListener { openPodcast() }
        viewBinding!!.txtvTitle.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
        viewBinding!!.txtvTitle.setEllipsize(TextUtils.TruncateAt.END)
        viewBinding!!.webvDescription.setTimecodeSelectedListener { time ->
            if (!PlaybackService.isRunning) {
                EventBus.getDefault().post(
                        MessageEvent(getString(R.string.play_this_to_seek_position_message)))
                return@setTimecodeSelectedListener
            }
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                PlaybackController.bindToMedia3Service(requireActivity()) { controller ->
                    controller.seekTo(time.toLong())
                }
                return@setTimecodeSelectedListener
            }
            PlaybackController.bindToService(requireActivity()) { playbackService ->
                if (item!!.getMedia() != null && playbackService.getPlayable() != null
                        && item!!.getMedia()!!.getIdentifier() == playbackService.getPlayable()!!.getIdentifier()) {
                    playbackService.seekTo(time)
                } else {
                    EventBus.getDefault().post(
                            MessageEvent(getString(R.string.play_this_to_seek_position_message)))
                }
            }
        }
        registerForContextMenu(viewBinding!!.webvDescription)
        viewBinding!!.imgvCover.setOnClickListener { openPodcast() }
        viewBinding!!.butAction1.setOnClickListener {
            if (actionButton1 is StreamActionButton && !UserPreferences.isStreamOverDownload()
                    && UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM)) {
                showOnDemandConfigBalloon(true)
                return@setOnClickListener
            } else if (actionButton1 == null) {
                return@setOnClickListener // Not loaded yet
            }
            actionButton1!!.onClick(requireContext())
        }
        viewBinding!!.butAction2.setOnClickListener {
            if (actionButton2 is DownloadActionButton && UserPreferences.isStreamOverDownload()
                    && UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD)) {
                showOnDemandConfigBalloon(false)
                return@setOnClickListener
            } else if (actionButton2 == null) {
                return@setOnClickListener // Not loaded yet
            }
            actionButton2!!.onClick(requireContext())
        }
        viewBinding!!.txtvPodcast.setOnLongClickListener {
            ClipboardUtils.copyText(viewBinding!!.txtvPodcast)
            return@setOnLongClickListener true
        }
        viewBinding!!.txtvTitle.setOnLongClickListener {
            ClipboardUtils.copyText(viewBinding!!.txtvTitle)
            return@setOnLongClickListener true
        }
        return viewBinding!!.getRoot()
    }

    private fun showOnDemandConfigBalloon(offerStreaming: Boolean) {
        val isLocaleRtl = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) ==
                View.LAYOUT_DIRECTION_RTL
        val balloon = Balloon.Builder(requireContext())
                .setArrowOrientation(ArrowOrientation.TOP)
                .setArrowOrientationRules(ArrowOrientationRules.ALIGN_FIXED)
                .setArrowPosition(0.25f + (if (isLocaleRtl xor offerStreaming) 0f else 0.5f))
                .setWidthRatio(1.0f)
                .setMarginLeft(8)
                .setMarginRight(8)
                .setBackgroundColor(ThemeUtils.getColorFromAttr(requireContext(), R.attr.colorSecondary))
                .setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                .setLayout(R.layout.popup_bubble_view)
                .setDismissWhenTouchOutside(true)
                .setLifecycleOwner(this)
                .build()
        val positiveButton = balloon.getContentView().findViewById<Button>(R.id.balloon_button_positive)
        val negativeButton = balloon.getContentView().findViewById<Button>(R.id.balloon_button_negative)
        val message = balloon.getContentView().findViewById<TextView>(R.id.balloon_message)
        message.setText(if (offerStreaming)
            R.string.on_demand_config_stream_text else R.string.on_demand_config_download_text)
        positiveButton.setOnClickListener {
            UserPreferences.setStreamOverDownload(offerStreaming)
            // Update all visible lists to reflect new streaming action button
            EventBus.getDefault().post(FeedItemEvent(Collections.emptyList(), true))
            EventBus.getDefault().post(MessageEvent(getString(R.string.on_demand_config_setting_changed)))
            balloon.dismiss()
        }
        negativeButton.setOnClickListener {
            UsageStatistics.doNotAskAgain(UsageStatistics.ACTION_STREAM) // Type does not matter. Both are silenced.
            balloon.dismiss()
        }
        balloon.showAlignBottom(viewBinding!!.butAction1, 0,
                (-12 * getResources().getDisplayMetrics().density).toInt())
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        load()
    }

    override fun onResume() {
        super.onResume()
        if (itemsLoaded) {
            viewBinding!!.progbarLoading.setVisibility(View.GONE)
            updateAppearance()
        }
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
        viewBinding!!.contentRoot.removeView(viewBinding!!.webvDescription)
        viewBinding!!.webvDescription.destroy()
        viewBinding = null
    }

    private fun onFragmentLoaded() {
        if (webviewData != null && !itemsLoaded) {
            viewBinding!!.webvDescription.loadDataWithBaseURL(
                    "https://127.0.0.1", webviewData!!, "text/html", "utf-8", "about:blank")
        }
        updateAppearance()
    }

    private fun updateAppearance() {
        if (item == null) {
            Log.d(TAG, "updateAppearance item is null")
            return
        }
        viewBinding!!.txtvPodcast.setText(item!!.getFeed()!!.getTitle())
        viewBinding!!.txtvTitle.setText(item!!.getTitle())
        if (item!!.getPubDate() != null) {
            val pubDateStr = DateFormatter.formatAbbrev(requireActivity(), item!!.getPubDate())
            viewBinding!!.txtvPublished.setText(pubDateStr)
            viewBinding!!.txtvPublished.setContentDescription(
                    DateFormatter.formatForAccessibility(item!!.getPubDate()))
        }
        if (item!!.getFeed()!!.getState() == Feed.STATE_NOT_SUBSCRIBED) {
            viewBinding!!.nonSubscribedWarningLabel.setVisibility(View.VISIBLE)
            viewBinding!!.nonSubscribedWarningLabel.setOnClickListener { openPodcast() }
        }
        val radius = 8 * getResources().getDisplayMetrics().density
        val options = RequestOptions()
                .error(ImagePlaceholder.getDrawable(requireContext(), radius))
                .transform(FitCenter(),
                        RoundedCorners(radius.toInt()))
                .dontAnimate()
        Glide.with(this)
                .load(item!!.getImageLocation())
                .error(Glide.with(this)
                        .load(ImageResourceUtils.getFallbackImageLocation(item!!))
                        .apply(options))
                .apply(options)
                .into(viewBinding!!.imgvCover)
        updateButtons()
    }

    private fun updateButtons() {
        viewBinding!!.circularProgressBar.setVisibility(View.GONE)
        if (item!!.hasMedia()) {
            if (DownloadServiceInterface.get()!!.isDownloadingEpisode(item!!.getMedia()!!.getDownloadUrl())) {
                viewBinding!!.circularProgressBar.setVisibility(View.VISIBLE)
                viewBinding!!.circularProgressBar.setPercentage(0.01f * Math.max(1,
                        DownloadServiceInterface.get()!!.getProgress(item!!.getMedia()!!.getDownloadUrl())), item)
                viewBinding!!.circularProgressBar.setIndeterminate(
                        DownloadServiceInterface.get()!!.isEpisodeQueued(item!!.getMedia()!!.getDownloadUrl()))
            }
        }
        val media = item!!.getMedia()
        if (media == null) {
            actionButton1 = MarkAsPlayedActionButton(item!!)
            actionButton2 = VisitWebsiteActionButton(item!!)
            viewBinding!!.noMediaLabel.setVisibility(View.VISIBLE)
            viewBinding!!.txtvDuration.setVisibility(View.GONE)
            viewBinding!!.separatorIcons.setVisibility(View.GONE)
        } else {
            viewBinding!!.noMediaLabel.setVisibility(View.GONE)
            val hasDuration = media.getDuration() > 0
            viewBinding!!.txtvDuration.setVisibility(if (hasDuration) View.VISIBLE else View.GONE)
            viewBinding!!.separatorIcons.setVisibility(if (hasDuration) View.VISIBLE else View.GONE)
            if (hasDuration) {
                viewBinding!!.txtvDuration.setText(Converter.getDurationStringLong(media.getDuration()))
                viewBinding!!.txtvDuration.setContentDescription(
                        Converter.getDurationStringLocalized(requireContext(), media.getDuration().toLong()))
            }
            if (PlaybackStatus.isCurrentlyPlaying(media)) {
                actionButton1 = PauseActionButton(item!!)
            } else if (item!!.getFeed()!!.isLocalFeed()) {
                actionButton1 = PlayLocalActionButton(item!!)
            } else if (media.isDownloaded()) {
                actionButton1 = PlayActionButton(item!!)
            } else {
                actionButton1 = StreamActionButton(item!!)
            }
            if (DownloadServiceInterface.get()!!.isDownloadingEpisode(media.getDownloadUrl())) {
                actionButton2 = CancelDownloadActionButton(item!!)
            } else if (item!!.getFeed()!!.isLocalFeed() || media.isDownloaded()) {
                actionButton2 = DeleteActionButton(item!!)
            } else {
                actionButton2 = DownloadActionButton(item!!)
            }
        }

        viewBinding!!.butAction1Text.setText(actionButton1!!.getLabel())
        viewBinding!!.butAction1Text.setTransformationMethod(null)
        viewBinding!!.butAction1Icon.setImageResource(actionButton1!!.getDrawable())
        viewBinding!!.butAction1.setVisibility(actionButton1!!.getVisibility())

        viewBinding!!.butAction2Text.setText(actionButton2!!.getLabel())
        viewBinding!!.butAction2Text.setTransformationMethod(null)
        viewBinding!!.butAction2Icon.setImageResource(actionButton2!!.getDrawable())
        viewBinding!!.butAction2.setVisibility(actionButton2!!.getVisibility())
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return viewBinding!!.webvDescription.onContextItemSelected(item)
    }

    private fun openPodcast() {
        if (item == null) {
            return
        }
        if (item!!.getFeed()!!.getState() == Feed.STATE_NOT_SUBSCRIBED) {
            startActivity(OnlineFeedviewActivityStarter(requireContext(),
                    item!!.getFeed()!!.getDownloadUrl()!!).getIntent())
        } else {
            val fragment = FeedItemlistFragment.newInstance(item!!.getFeedId())
            (getActivity() as MainActivity).loadChildFragment(fragment)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]")
        if (event.unreadStatusChanged && event.items.isEmpty()) {
            load()
            return
        }
        for (item in event.items) {
            if (this.item != null && this.item!!.getId() == item.getId()) {
                load()
                return
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedListUpdateEvent) {
        if (item != null && item!!.getFeed() != null && event.contains(item!!.getFeed()!!)) {
            load()
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        if (item == null || item!!.getMedia() == null) {
            return
        }
        if (!event.getUrls().contains(item!!.getMedia()!!.getDownloadUrl())) {
            return
        }
        if (itemsLoaded && getActivity() != null) {
            updateButtons()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent) {
        updateButtons()
    }

    private fun load() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        if (!itemsLoaded) {
            viewBinding!!.progbarLoading.setVisibility(View.VISIBLE)
        }
        disposable = Maybe.fromCallable(this::loadInBackground)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    viewBinding!!.progbarLoading.setVisibility(View.GONE)
                    viewBinding!!.header.setVisibility(View.VISIBLE)
                    item = result
                    onFragmentLoaded()
                    itemsLoaded = true
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) },
                        { requireActivity().getSupportFragmentManager().popBackStack() })
    }

    private fun loadInBackground(): FeedItem? {
        val feedItem = DBReader.getFeedItem(itemId)
        val context = getContext()
        if (feedItem != null && context != null) {
            val duration = if (feedItem.getMedia() != null) feedItem.getMedia()!!.getDuration() else Integer.MAX_VALUE
            DBReader.loadDescriptionOfFeedItem(feedItem)
            val t = ShownotesCleaner(context, feedItem.getDescription(), duration)
            webviewData = t.processShownotes()
        }
        return feedItem
    }
}
