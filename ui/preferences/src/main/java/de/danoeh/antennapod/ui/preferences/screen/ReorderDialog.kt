package de.danoeh.antennapod.ui.preferences.screen

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.ui.preferences.R
import de.danoeh.antennapod.ui.preferences.databinding.ReorderDialogBinding

import java.util.ArrayList
import java.util.Collections

abstract class ReorderDialog(@JvmField protected val context: Context) {
    protected val dialogItems: List<ReorderDialogItem>
    private val adapter: ReorderDialogAdapter

    init {
        dialogItems = getInitialItems()
        adapter = ReorderDialogAdapter(dialogItems)
    }

    fun show() {
        val layoutInflater = LayoutInflater.from(context)
        val viewBinding = ReorderDialogBinding.inflate(layoutInflater)

        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(getTitle())
        builder.setView(viewBinding.getRoot())
        configureRecyclerView(viewBinding.recyclerView)

        builder.setPositiveButton(android.R.string.ok) { dialog, which -> onConfirmed() }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.setNeutralButton(R.string.reset) { dialog, which -> onReset() }
        builder.show()
    }

    private fun configureRecyclerView(recyclerView: RecyclerView) {
        val itemMoveCallback = object : ReorderItemTouchCallback() {
            override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
                return this@ReorderDialog.onItemMove(fromPosition, toPosition)
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemMoveCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
        adapter.setDragListener { itemTouchHelper.startDrag(it) }
        val layoutManager = LinearLayoutManager(context)
        recyclerView.setLayoutManager(layoutManager)
        recyclerView.setAdapter(adapter)
        recyclerView.addItemDecoration(DividerItemDecoration(context, layoutManager.getOrientation()))
    }

    protected open fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(dialogItems, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(dialogItems, i, i - 1)
            }
        }
        adapter.notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun getTagsWithoutHeaders(): List<String> {
        val orderedSectionTags: MutableList<String> = ArrayList()
        for (item in dialogItems) {
            if (item.getViewType() == ReorderDialogItem.ViewType.Header) {
                continue
            }
            orderedSectionTags.add(item.getTag())
        }
        return orderedSectionTags
    }

    fun getTagsAfterHeader(tag: String): List<String> {
        val itemsAfterHeader: MutableList<String> = ArrayList()
        var i = 0
        while (dialogItems.get(i).getViewType() != ReorderDialogItem.ViewType.Header
                || dialogItems.get(i).getTag() != tag) {
            i++
        }
        i++
        while (i < dialogItems.size && dialogItems.get(i).getViewType() != ReorderDialogItem.ViewType.Header) {
            itemsAfterHeader.add(dialogItems.get(i).getTag())
            i++
        }
        return itemsAfterHeader
    }

    @StringRes
    protected abstract fun getTitle(): Int

    protected abstract fun getInitialItems(): List<ReorderDialogItem>

    protected abstract fun onReset()

    protected abstract fun onConfirmed()
}
