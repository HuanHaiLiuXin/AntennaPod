package de.danoeh.antennapod.ui.preferences.screen

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.util.Consumer
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.ui.preferences.databinding.ReorderDialogEntryBinding
import de.danoeh.antennapod.ui.preferences.databinding.ReorderDialogHeaderBinding

class ReorderDialogAdapter(private val settingsDialogItems: List<ReorderDialogItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val HEADER_VIEW = 0
        private const val ITEM_VIEW = 1
    }

    private var dragListener: Consumer<ItemViewHolder>? = null

    fun setDragListener(dragListener: Consumer<ItemViewHolder>?) {
        this.dragListener = dragListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.getContext())
        if (viewType == HEADER_VIEW) {
            val binding = ReorderDialogHeaderBinding.inflate(inflater, parent, false)
            return HeaderViewHolder(binding.getRoot(), binding.headerLabel)
        }

        val binding = ReorderDialogEntryBinding.inflate(inflater, parent, false)
        return ItemViewHolder(binding.getRoot(), binding.sectionLabel, binding.dragHandle)
    }

    override fun getItemViewType(position: Int): Int {
        val viewType = settingsDialogItems.get(position).getViewType()
        val isHeader = viewType == ReorderDialogItem.ViewType.Header
        return if (isHeader) HEADER_VIEW else ITEM_VIEW
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.categoryLabel.setText(settingsDialogItems.get(position).getTitle())
        } else if (holder is ItemViewHolder) {
            holder.nameLabel.setText(settingsDialogItems.get(position).getTitle())
            holder.dragImage.setOnTouchListener { view, motionEvent ->
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    if (dragListener != null) {
                        dragListener!!.accept(holder)
                    }
                }
                true
            }
        }
    }

    override fun getItemCount(): Int {
        return settingsDialogItems.size
    }

    class ItemViewHolder(itemView: View, internal val nameLabel: TextView, internal val dragImage: ImageView) :
            RecyclerView.ViewHolder(itemView)

    class HeaderViewHolder(itemView: View, internal val categoryLabel: TextView) :
            RecyclerView.ViewHolder(itemView)
}
