package de.danoeh.antennapod.ui.screen.chapter

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.elevation.SurfaceColors
import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.model.feed.EmbeddedChapterImage
import de.danoeh.antennapod.ui.common.ImagePlaceholder
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.ui.common.CircularProgressBar

class ChaptersListAdapter(private val context: Context, private val callback: Callback?) :
        RecyclerView.Adapter<ChaptersListAdapter.ChapterHolder>() {
    private var media: Playable? = null
    private var currentChapterIndex = -1
    private var currentChapterPosition = -1L
    private var hasImages = false

    fun setMedia(media: Playable) {
        this.media = media
        hasImages = false
        if (media.getChapters() != null) {
            for (chapter in media.getChapters()!!) {
                if (!TextUtils.isEmpty(chapter.getImageUrl())) {
                    hasImages = true
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ChapterHolder, position: Int) {
        if (position < 0 || position >= getItemCount()) {
            holder.title.setText("Error")
            return
        }
        val sc = getItem(position)
        if (sc == null) {
            holder.title.setText("Error")
            return
        }
        holder.title.setText(sc.getTitle())
        holder.start.setText(Converter.getDurationStringLong(sc.getStart().toInt()))

        val duration: Long
        if (position + 1 < media!!.getChapters()!!.size) {
            duration = media!!.getChapters()!![position + 1].getStart() - sc.getStart()
        } else {
            duration = media!!.getDuration() - sc.getStart()
        }
        holder.duration.setText(context.getString(R.string.chapter_duration,
                Converter.getDurationStringLocalized(context, duration)))

        if (TextUtils.isEmpty(sc.getLink())) {
            holder.link.setVisibility(View.GONE)
        } else {
            holder.link.setVisibility(View.VISIBLE)
            holder.link.setText(sc.getLink())
            holder.link.setOnClickListener { IntentUtils.openInBrowser(context, sc.getLink()!!) }
        }
        holder.secondaryActionIcon.setImageResource(R.drawable.ic_play_48dp)
        holder.secondaryActionButton.setContentDescription(context.getString(R.string.play_chapter))
        holder.secondaryActionButton.setOnClickListener {
            if (callback != null) {
                callback!!.onPlayChapterButtonClicked(position)
            }
        }

        if (position == currentChapterIndex) {
            val density = context.getResources().getDisplayMetrics().density
            holder.itemView.setBackgroundColor(SurfaceColors.getColorForElevation(context, 32 * density))
            var progress = (currentChapterPosition - sc.getStart()).toFloat() / duration
            progress = Math.max(progress, CircularProgressBar.MINIMUM_PERCENTAGE)
            progress = Math.min(progress, CircularProgressBar.MAXIMUM_PERCENTAGE)
            holder.progressBar.setPercentage(progress, position)
            holder.secondaryActionIcon.setImageResource(R.drawable.ic_replay)
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            holder.progressBar.setPercentage(0f, null)
        }

        if (hasImages) {
            holder.image.setVisibility(View.VISIBLE)

            val radius = 4 * context.getResources().getDisplayMetrics().density
            val options = RequestOptions()
                    .placeholder(ImagePlaceholder.getDrawable(context, radius))
                    .dontAnimate()
                    .transform(FitCenter(), RoundedCorners(radius.toInt()))

            if (TextUtils.isEmpty(sc.getImageUrl())) {
                if (media!!.getImageLocation() == null) {
                    Glide.with(context).clear(holder.image)
                    holder.image.setVisibility(View.GONE)
                } else {
                    Glide.with(context)
                            .load(media!!.getImageLocation())
                            .apply(options)
                            .into(holder.image)
                }
            } else {
                Glide.with(context)
                        .load(EmbeddedChapterImage.getModelFor(media!!, position))
                        .apply(options)
                        .into(holder.image)
            }
        } else {
            holder.image.setVisibility(View.GONE)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterHolder {
        val inflater = LayoutInflater.from(context)
        return ChapterHolder(inflater.inflate(R.layout.simplechapter_item, parent, false))
    }

    override fun getItemCount(): Int {
        if (media == null || media!!.getChapters() == null) {
            return 0
        }
        return media!!.getChapters()!!.size
    }

    class ChapterHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.txtvTitle)
        val start: TextView = itemView.findViewById(R.id.txtvStart)
        val link: TextView = itemView.findViewById(R.id.txtvLink)
        val image: ImageView = itemView.findViewById(R.id.imgvCover)
        val duration: TextView = itemView.findViewById(R.id.txtvDuration)
        val secondaryActionButton: View = itemView.findViewById(R.id.secondaryActionButton)
        val secondaryActionIcon: ImageView = itemView.findViewById(R.id.secondaryActionIcon)
        val progressBar: CircularProgressBar = itemView.findViewById(R.id.secondaryActionProgress)
    }

    fun notifyChapterChanged(newChapterIndex: Int) {
        if (newChapterIndex < 0 || newChapterIndex >= getItemCount()) {
            return
        }
        currentChapterIndex = newChapterIndex
        val chapter = getItem(newChapterIndex)
        if (chapter != null) {
            currentChapterPosition = chapter.getStart()
        }
        notifyDataSetChanged()
    }

    fun notifyTimeChanged(timeMs: Long) {
        if (currentChapterIndex < 0 || currentChapterIndex >= getItemCount()) {
            return
        }
        currentChapterPosition = timeMs
        // Passing an argument prevents flickering.
        // See EpisodeItemListAdapter.notifyItemChangedCompat.
        notifyItemChanged(currentChapterIndex, "foo")
    }

    fun getItem(position: Int): Chapter? {
        if (media == null || media!!.getChapters() == null || position < 0
                || position >= media!!.getChapters()!!.size) {
            return null
        }
        return media!!.getChapters()!![position]
    }

    fun interface Callback {
        fun onPlayChapterButtonClicked(position: Int)
    }
}
