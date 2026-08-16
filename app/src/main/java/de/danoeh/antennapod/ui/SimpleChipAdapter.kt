package de.danoeh.antennapod.ui

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import de.danoeh.antennapod.R

abstract class SimpleChipAdapter(private val context: Context) : RecyclerView.Adapter<SimpleChipAdapter.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    protected abstract fun getChips(): List<String>

    protected abstract fun onRemoveClicked(position: Int)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val chip = Chip(context)
        chip.setCloseIconVisible(true)
        chip.setCloseIconResource(R.drawable.ic_delete)
        return ViewHolder(chip)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.chip.setText(getChips().get(position))
        holder.chip.setOnCloseIconClickListener { onRemoveClicked(position) }
    }

    override fun getItemCount(): Int {
        return getChips().size
    }

    override fun getItemId(position: Int): Long {
        return getChips().get(position).hashCode().toLong()
    }

    class ViewHolder(itemView: Chip) : RecyclerView.ViewHolder(itemView) {
        val chip: Chip = itemView
    }
}
