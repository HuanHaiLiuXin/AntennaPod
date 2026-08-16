package de.danoeh.antennapod.ui.episodeslist

import android.app.Activity
import android.text.Layout
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView

import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView


import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.CoverLoader
import de.danoeh.antennapod.actionbutton.ItemActionButton
import de.danoeh.antennapod.playback.service.PlaybackStatus
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.ui.common.DateFormatter
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.ui.common.CircularProgressBar
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils

/**
 * Holds the view which shows FeedItems.
 */
class EpisodeItemViewHolder(private val activity: Activity, parent: ViewGroup) :
        RecyclerView.ViewHolder(LayoutInflater.from(activity).inflate(R.layout.feeditemlist_item, parent, false)) {
    companion object {
        private const val TAG = "EpisodeItemViewHolder"
    }

    @JvmField
    val container: View = itemView.findViewById(R.id.container)
    @JvmField
    val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
    @JvmField
    val placeholder: TextView = itemView.findViewById(R.id.txtvPlaceholder)
    @JvmField
    val cover: ImageView = itemView.findViewById(R.id.imgvCover)
    private val title: TextView
    private val pubDate: TextView
    private val position: TextView
    private val duration: TextView
    private val size: TextView
    @JvmField
    val isInbox: ImageView
    @JvmField
    val isInQueue: ImageView
    private val isVideo: ImageView
    @JvmField
    val isFavorite: ImageView
    private val progressBar: ProgressBar
    @JvmField
    val secondaryActionButton: View
    @JvmField
    val secondaryActionIcon: ImageView
    private val secondaryActionProgress: CircularProgressBar
    private val separatorIcons: TextView
    private val leftPadding: View
    @JvmField
    val coverHolder: CardView

    private var item: FeedItem? = null

    init {
        title = itemView.findViewById(R.id.txtvTitle)
        title.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
        pubDate = itemView.findViewById(R.id.txtvPubDate)
        position = itemView.findViewById(R.id.txtvPosition)
        duration = itemView.findViewById(R.id.txtvDuration)
        progressBar = itemView.findViewById(R.id.progressBar)
        isInQueue = itemView.findViewById(R.id.ivInPlaylist)
        isVideo = itemView.findViewById(R.id.ivIsVideo)
        isInbox = itemView.findViewById(R.id.statusInbox)
        isFavorite = itemView.findViewById(R.id.isFavorite)
        size = itemView.findViewById(R.id.size)
        separatorIcons = itemView.findViewById(R.id.separatorIcons)
        secondaryActionProgress = itemView.findViewById(R.id.secondaryActionProgress)
        secondaryActionButton = itemView.findViewById(R.id.secondaryActionButton)
        secondaryActionIcon = itemView.findViewById(R.id.secondaryActionIcon)
        coverHolder = itemView.findViewById(R.id.coverHolder)
        leftPadding = itemView.findViewById(R.id.left_padding)
        itemView.setTag(this)
    }

    fun bind(item: FeedItem) {
        this.item = item
        placeholder.setText(item.getFeed()!!.getTitle())
        title.setText(item.getTitle())
        if (item.isPlayed()) {
            leftPadding.setContentDescription(item.getTitle().toString() + ". " + activity.getString(R.string.is_played))
        } else {
            leftPadding.setContentDescription(item.getTitle())
        }
        pubDate.setText(DateFormatter.formatAbbrev(activity, item.getPubDate()))
        pubDate.setContentDescription(DateFormatter.formatForAccessibility(item.getPubDate()))
        isInbox.setVisibility(if (item.isNew()) View.VISIBLE else View.GONE)
        isFavorite.setVisibility(if (item.isTagged(FeedItem.TAG_FAVORITE)) View.VISIBLE else View.GONE)
        isInQueue.setVisibility(if (item.isTagged(FeedItem.TAG_QUEUE)) View.VISIBLE else View.GONE)
        container.setAlpha(if (item.isPlayed()) 0.5f else 1.0f)

        val actionButton = ItemActionButton.forItem(item)
        actionButton.configure(secondaryActionButton, secondaryActionIcon, activity)
        secondaryActionButton.setFocusable(false)

        if (item.getMedia() != null) {
            bind(item.getMedia()!!)
        } else {
            secondaryActionProgress.setPercentage(0f, item)
            secondaryActionProgress.setIndeterminate(false)
            isVideo.setVisibility(View.GONE)
            progressBar.setVisibility(View.GONE)
            duration.setVisibility(View.GONE)
            position.setVisibility(View.GONE)
            itemView.setActivated(false)
        }

        if (coverHolder.getVisibility() == View.VISIBLE) {
            CoverLoader()
                    .withUri(ImageResourceUtils.getEpisodeListImageLocation(item))
                    .withFallbackUri(item.getFeed()!!.getImageUrl())
                    .withPlaceholderView(placeholder)
                    .withCoverView(cover)
                    .load()
        }
    }

    private fun bind(media: FeedMedia) {
        isVideo.setVisibility(if (media.getMediaType() == MediaType.VIDEO) View.VISIBLE else View.GONE)
        duration.setVisibility(if (media.getDuration() > 0) View.VISIBLE else View.GONE)

        itemView.setActivated(PlaybackStatus.isCurrentlyPlaying(media))

        if (DownloadServiceInterface.get()!!.isDownloadingEpisode(media.getDownloadUrl())) {
            val percent = 0.01f * DownloadServiceInterface.get()!!.getProgress(media.getDownloadUrl())
            secondaryActionProgress.setPercentage(Math.max(percent, 0.01f), item)
            secondaryActionProgress.setIndeterminate(
                    DownloadServiceInterface.get()!!.isEpisodeQueued(media.getDownloadUrl()))
        } else if (media.isDownloaded()) {
            secondaryActionProgress.setPercentage(1f, item) // Do not animate 100% -> 0%
            secondaryActionProgress.setIndeterminate(false)
        } else {
            secondaryActionProgress.setPercentage(0f, item) // Animate X% -> 0%
            secondaryActionProgress.setIndeterminate(false)
        }

        duration.setText(Converter.getDurationStringLong(media.getDuration()))
        duration.setContentDescription(activity.getString(R.string.chapter_duration,
                Converter.getDurationStringLocalized(activity, media.getDuration().toLong())))
        if (PlaybackStatus.isPlaying(item?.getMedia()) || item!!.isInProgress()) {
            val progress = (100.0 * media.getPosition() / media.getDuration()).toInt()
            val remainingTime = Math.max(media.getDuration() - media.getPosition(), 0)
            progressBar.setProgress(progress)
            position.setText(Converter.getDurationStringLong(media.getPosition()))
            position.setContentDescription(activity.getString(R.string.position,
                    Converter.getDurationStringLocalized(activity, media.getPosition().toLong())))
            progressBar.setVisibility(View.VISIBLE)
            position.setVisibility(View.VISIBLE)
            if (UserPreferences.shouldShowRemainingTime()) {
                duration.setText((if (remainingTime > 0) "-" else "") + Converter.getDurationStringLong(remainingTime))
                duration.setContentDescription(activity.getString(R.string.chapter_duration,
                        Converter.getDurationStringLocalized(activity, (media.getDuration() - media.getPosition()).toLong())))
            }
        } else {
            progressBar.setVisibility(View.GONE)
            position.setVisibility(View.GONE)
        }

        if (media.getSize() > 0) {
            size.setText(Formatter.formatShortFileSize(activity, media.getSize()))
        } else if (NetworkUtils.isEpisodeHeadDownloadAllowed() && !media.checkedOnSizeButUnknown()) {
            size.setText("")
            MediaSizeLoader.getFeedMediaSizeObservable(media).subscribe(
                    { sizeValue ->
                        if (sizeValue > 0) {
                            size.setText(Formatter.formatShortFileSize(activity, sizeValue))
                        } else {
                            size.setText("")
                        }
                    }, { error ->
                        size.setText("")
                        Log.e(TAG, Log.getStackTraceString(error))
                    })
        } else {
            size.setText("")
        }
    }

    fun bindDummy() {
        item = FeedItem()
        item!!.setFeed(Feed("", ""))
        container.setAlpha(0.1f)
        secondaryActionIcon.setImageDrawable(null)
        isInbox.setVisibility(View.VISIBLE)
        isVideo.setVisibility(View.GONE)
        isFavorite.setVisibility(View.GONE)
        isInQueue.setVisibility(View.GONE)
        title.setText("███████")
        pubDate.setText("████")
        duration.setText("████")
        secondaryActionProgress.setPercentage(0f, null)
        secondaryActionProgress.setIndeterminate(false)
        progressBar.setVisibility(View.GONE)
        position.setVisibility(View.GONE)
        dragHandle.setVisibility(View.GONE)
        size.setText("")
        itemView.setActivated(false)
        placeholder.setText("")
        if (coverHolder.getVisibility() == View.VISIBLE) {
            CoverLoader()
                    .withResource(R.color.medium_gray)
                    .withPlaceholderView(placeholder)
                    .withCoverView(cover)
                    .load()
        }
    }

    private fun updateDuration(event: PlaybackPositionEvent) {
        if (getFeedItem()!!.getMedia() != null) {
            getFeedItem()!!.getMedia()!!.setPosition(event.getPosition())
            getFeedItem()!!.getMedia()!!.setDuration(event.getDuration())
        }
        val currentPosition = event.getPosition()
        val timeDuration = event.getDuration()
        val remainingTime = Math.max(timeDuration - currentPosition, 0)
        Log.d(TAG, "currentPosition " + Converter.getDurationStringLong(currentPosition))
        if (currentPosition == Playable.INVALID_TIME || timeDuration == Playable.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time")
            return
        }
        if (UserPreferences.shouldShowRemainingTime()) {
            duration.setText((if (remainingTime > 0) "-" else "") + Converter.getDurationStringLong(remainingTime))
        } else {
            duration.setText(Converter.getDurationStringLong(timeDuration))
        }
    }

    fun getFeedItem(): FeedItem? {
        return item
    }

    fun isPlayingItem(): Boolean {
        return item!!.getMedia() != null && PlaybackStatus.isPlaying(item!!.getMedia())
    }

    fun notifyPlaybackPositionUpdated(event: PlaybackPositionEvent) {
        progressBar.setProgress((100.0 * event.getPosition() / event.getDuration()).toInt())
        position.setText(Converter.getDurationStringLong(event.getPosition()))
        updateDuration(event)
        duration.setVisibility(View.VISIBLE) // Even if the duration was previously unknown, it is now known
    }

    /**
     * Hides the separator dot between icons and text if there are no icons.
     */
    fun hideSeparatorIfNecessary() {
        val hasIcons = isInbox.getVisibility() == View.VISIBLE
                || isInQueue.getVisibility() == View.VISIBLE
                || isVideo.getVisibility() == View.VISIBLE
                || isFavorite.getVisibility() == View.VISIBLE
                || isInbox.getVisibility() == View.VISIBLE
        separatorIcons.setVisibility(if (hasIcons) View.VISIBLE else View.GONE)
    }
}
