package de.danoeh.antennapod.ui.transcript

import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import de.danoeh.antennapod.databinding.TranscriptItemBinding

class TranscriptViewholder(binding: TranscriptItemBinding) : RecyclerView.ViewHolder(binding.getRoot()) {
    @JvmField
    val viewTimecode: TextView
    @JvmField
    val viewContent: TextView

    init {
        viewTimecode = binding.speaker
        viewContent = binding.content
    }

    override fun toString(): String {
        return super.toString() + " '" + viewContent.getText() + "'"
    }
}
