package de.danoeh.antennapod.ui.episodeslist

import android.app.Activity
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
import de.danoeh.antennapod.ui.common.DateFormatter
import de.danoeh.antennapod.playback.service.PlaybackStatus
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.ui.common.CircularProgressBar
import de.danoeh.antennapod.ui.common.SquareImageView
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils

class HorizontalItemViewHolder(private val activity: Activity, parent: ViewGroup) :
        RecyclerView.ViewHolder(LayoutInflater.from(activity).inflate(R.layout.horizontal_itemlist_item, parent, false)) {
    val card: CardView
    val secondaryActionIcon: ImageView
    private val cover: SquareImageView
    private val title: TextView
    private val date: TextView
    private val progressBar: ProgressBar
    private val circularProgressBar: CircularProgressBar
    private val progressBarReplacementSpacer: View

    private var item: FeedItem? = null

    init {
        card = itemView.findViewById(R.id.card)
        cover = itemView.findViewById(R.id.cover)
        title = itemView.findViewById(R.id.titleLabel)
        date = itemView.findViewById(R.id.dateLabel)
        secondaryActionIcon = itemView.findViewById(R.id.secondaryActionIcon)
        circularProgressBar = itemView.findViewById(R.id.circularProgressBar)
        progressBar = itemView.findViewById(R.id.progressBar)
        progressBarReplacementSpacer = itemView.findViewById(R.id.progressBarReplacementSpacer)
        itemView.setTag(this)
    }

    fun bind(item: FeedItem) {
        this.item = item

        card.setAlpha(1.0f)
        card.setCardBackgroundColor(ThemeUtils.getColorFromAttr(activity, R.attr.colorSurfaceContainer))
        CoverLoader()
                .withUri(ImageResourceUtils.getEpisodeListImageLocation(item))
                .withFallbackUri(item.getFeed()!!.getImageUrl())
                .withCoverView(cover)
                .load()
        title.setText(item.getTitle())
        date.setText(DateFormatter.formatAbbrev(activity, item.getPubDate()))
        date.setContentDescription(DateFormatter.formatForAccessibility(item.getPubDate()))
        val actionButton = ItemActionButton.forItem(item)
        actionButton.configure(secondaryActionIcon, secondaryActionIcon, activity)
        secondaryActionIcon.setFocusable(false)

        val media: FeedMedia? = item.getMedia()
        if (media == null) {
            circularProgressBar.setPercentage(0f, item)
            setProgressBar(false, 0f)
        } else {
            if (PlaybackStatus.isCurrentlyPlaying(media)) {
                card.setCardBackgroundColor(ThemeUtils.getColorFromAttr(activity, R.attr.colorSecondaryContainer))
            }

            if (item.getMedia()!!.getDuration() > 0 && item.getMedia()!!.getPosition() > 0) {
                setProgressBar(true, 100.0f * item.getMedia()!!.getPosition() / item.getMedia()!!.getDuration())
            } else {
                setProgressBar(false, 0f)
            }

            if (DownloadServiceInterface.get()!!.isDownloadingEpisode(media.getDownloadUrl())) {
                val percent = 0.01f * DownloadServiceInterface.get()!!.getProgress(media.getDownloadUrl())
                circularProgressBar.setPercentage(Math.max(percent, 0.01f), item)
                circularProgressBar.setIndeterminate(
                        DownloadServiceInterface.get()!!.isEpisodeQueued(media.getDownloadUrl()))
            } else if (media.isDownloaded()) {
                circularProgressBar.setPercentage(1f, item) // Do not animate 100% -> 0%
                circularProgressBar.setIndeterminate(false)
            } else {
                circularProgressBar.setPercentage(0f, item) // Animate X% -> 0%
                circularProgressBar.setIndeterminate(false)
            }
        }
    }

    fun bindDummy() {
        card.setAlpha(0.1f)
        CoverLoader()
                .withResource(android.R.color.transparent)
                .withCoverView(cover)
                .load()
        title.setText("████ █████")
        date.setText("███")
        secondaryActionIcon.setImageDrawable(null)
        circularProgressBar.setPercentage(0f, null)
        circularProgressBar.setIndeterminate(false)
        setProgressBar(true, 50f)
    }

    fun isCurrentlyPlayingItem(): Boolean {
        return item != null && item!!.getMedia() != null && PlaybackStatus.isCurrentlyPlaying(item!!.getMedia()!!)
    }

    fun notifyPlaybackPositionUpdated(event: PlaybackPositionEvent) {
        setProgressBar(true, 100.0f * event.getPosition() / event.getDuration())
    }

    private fun setProgressBar(visible: Boolean, progress: Float) {
        progressBar.setVisibility(if (visible) ViewGroup.VISIBLE else ViewGroup.GONE)
        progressBarReplacementSpacer.setVisibility(if (visible) View.GONE else ViewGroup.VISIBLE)
        progressBar.setProgress(Math.max(5, progress.toInt())) // otherwise invisible below the edge radius
    }
}
