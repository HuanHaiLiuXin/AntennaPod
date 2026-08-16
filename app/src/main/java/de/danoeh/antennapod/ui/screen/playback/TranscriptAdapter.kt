package de.danoeh.antennapod.ui.screen.playback

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.elevation.SurfaceColors
import de.danoeh.antennapod.databinding.TranscriptItemBinding
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.Transcript
import de.danoeh.antennapod.model.feed.TranscriptSegment
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.transcript.TranscriptViewholder
import org.apache.commons.lang3.ObjectUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jsoup.internal.StringUtil
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

class TranscriptAdapter(private val context: Context, private val segmentClickListener: SegmentClickListener?) :
        RecyclerView.Adapter<TranscriptViewholder>() {
    private var media: FeedMedia? = null
    private var prevHighlightPosition = -1
    private var highlightPosition = -1
    private var inMultiselectMode = false
    private val selectedPositions = HashSet<Int>()

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TranscriptViewholder {
        return TranscriptViewholder(TranscriptItemBinding.inflate(LayoutInflater.from(context), viewGroup, false))
    }

    fun setMedia(media: Playable) {
        if (media !is FeedMedia) {
            return
        }
        this.media = media
        notifyDataSetChanged()
    }

    fun setMultiselectMode(multiselectMode: Boolean) {
        if (this.inMultiselectMode == multiselectMode) {
            return
        }
        this.inMultiselectMode = multiselectMode
        if (!multiselectMode) {
            selectedPositions.clear()
        }
        notifyDataSetChanged()
    }

    fun isMultiselectMode(): Boolean {
        return inMultiselectMode
    }

    override fun onBindViewHolder(holder: TranscriptViewholder, position: Int) {
        if (media == null || media!!.getTranscript() == null) {
            return
        }

        val seg = media!!.getTranscript()!!.getSegmentAt(position)
        holder.viewContent.setOnClickListener {
            if (segmentClickListener != null) {
                segmentClickListener!!.onTranscriptClicked(position, seg)
            }
        }

        holder.viewContent.setOnLongClickListener {
            if (segmentClickListener != null) {
                segmentClickListener!!.onTranscriptLongClicked(position, seg)
            }
            true
        }

        val timecode = Converter.getDurationStringLong(seg.getStartTime().toInt())
        if (!StringUtil.isBlank(seg.getSpeaker())) {
            if (position > 0 && media!!.getTranscript()!!
                            .getSegmentAt(position - 1).getSpeaker() == seg.getSpeaker()) {
                holder.viewTimecode.setVisibility(View.GONE)
                holder.viewContent.setText(seg.getWords())
            } else {
                holder.viewTimecode.setVisibility(View.VISIBLE)
                holder.viewTimecode.setText(timecode + " • " + seg.getSpeaker())
                holder.viewContent.setText(seg.getWords())
            }
        } else {
            val speakers = media!!.getTranscript()!!.getSpeakers()
            if (speakers!!.isEmpty() && position % 5 == 0) {
                holder.viewTimecode.setVisibility(View.VISIBLE)
                holder.viewTimecode.setText(timecode)
            } else {
                holder.viewTimecode.setVisibility(View.GONE)
            }
            holder.viewContent.setText(seg.getWords())
        }

        if (inMultiselectMode) {
            highlightViewHolder(holder, selectedPositions.contains(position))
        } else {
            highlightViewHolder(holder, position == highlightPosition)
        }
    }

    private fun highlightViewHolder(holder: TranscriptViewholder, highlight: Boolean) {
        if (highlight) {
            val density = context.getResources().getDisplayMetrics().density
            holder.viewContent.setBackgroundColor(SurfaceColors.getColorForElevation(context, 32 * density))
            holder.viewContent.setAlpha(1.0f)
            holder.viewTimecode.setAlpha(1.0f)
            holder.viewContent.setAlpha(1.0f)
        } else {
            holder.viewContent.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            holder.viewContent.setAlpha(0.5f)
            holder.viewTimecode.setAlpha(0.5f)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        if (media == null || media!!.getTranscript() == null) {
            return
        }
        val index = media!!.getTranscript()!!.findSegmentIndexBefore(event.getPosition().toLong())
        if (index < 0 || index > media!!.getTranscript()!!.getSegmentCount()) {
            return
        }
        if (prevHighlightPosition != highlightPosition) {
            prevHighlightPosition = highlightPosition
        }
        if (index != highlightPosition) {
            highlightPosition = index
            notifyItemChanged(prevHighlightPosition)
            notifyItemChanged(highlightPosition)
        }
    }

    override fun getItemCount(): Int {
        if (media == null) {
            return 0
        }

        if (media!!.getTranscript() == null) {
            return 0
        }
        return media!!.getTranscript()!!.getSegmentCount()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        EventBus.getDefault().register(this)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        EventBus.getDefault().unregister(this)
    }

    fun toggleSelection(pos: Int) {
        if (selectedPositions.contains(pos)) {
            selectedPositions.remove(pos)
        } else {
            selectedPositions.add(pos)
        }
        notifyItemChanged(pos)
    }

    fun selectAll() {
        if (media == null || media!!.getTranscript() == null) {
            return
        }
        selectedPositions.clear()
        val count = getItemCount()
        for (i in 0 until count) {
            selectedPositions.add(i)
        }
        notifyDataSetChanged()
    }

    fun getSelectedText(): String? {
        if (!inMultiselectMode) {
            return null
        }
        val transcript = media!!.getTranscript()!!
        val ss = StringBuilder()
        var lastSpeaker: String? = null
        if (selectedPositions.isEmpty()) {
            return ""
        }
        val sortedPositions: MutableList<Int> = ArrayList(selectedPositions)
        Collections.sort(sortedPositions)
        var prevIndex = -2
        for (index in sortedPositions) {
            if (prevIndex != -2 && index != prevIndex + 1) {
                ss.append("\n[...]\n")
            }
            val seg = transcript.getSegmentAt(index)
            if (!StringUtil.isBlank(seg.getSpeaker())) {
                if (ObjectUtils.notEqual(lastSpeaker, seg.getSpeaker())) {
                    ss.append("\n").append(seg.getSpeaker()).append(" : ")
                    lastSpeaker = seg.getSpeaker()
                }
            } else {
                lastSpeaker = null
            }
            if (!TextUtils.isEmpty(ss) && ss[ss.length - 1] != ' ') {
                ss.append(' ')
            }
            ss.append(seg.getWords())
            prevIndex = index
        }

        return ss.toString().trim()
    }

    interface SegmentClickListener {
        fun onTranscriptClicked(position: Int, seg: TranscriptSegment)

        fun onTranscriptLongClicked(position: Int, seg: TranscriptSegment)
    }
}
