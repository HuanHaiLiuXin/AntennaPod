package de.danoeh.antennapod.ui

import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem

import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView

import de.danoeh.antennapod.R

import java.util.HashSet

/**
 * Used by Recyclerviews that need to provide ability to select items.
 */
abstract class SelectableAdapter<T : RecyclerView.ViewHolder>(private val activity: FragmentActivity) : RecyclerView.Adapter<T>() {
    private var actionMode: ActionMode? = null
    private val selectedIds = HashSet<Long>()
    private var onSelectModeListener: OnSelectModeListener? = null
    protected var shouldSelectLazyLoadedItems = false
    private var totalNumberOfItems = COUNT_AUTOMATICALLY

    fun startSelectMode(pos: Int) {
        if (inActionMode()) {
            endSelectMode()
        }

        shouldSelectLazyLoadedItems = false
        selectedIds.clear()
        selectedIds.add(getItemId(pos))

        val backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (actionMode != null) {
                    actionMode!!.finish()
                } else {
                    this.remove()
                }
            }
        }

        actionMode = activity.startActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val inflater = mode.getMenuInflater()
                inflater.inflate(R.menu.multi_select_options, menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                onSelectedItemsUpdated()
                toggleSelectAllIcon(menu)
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.getItemId() == R.id.select_toggle) {
                    val selectAll = selectedIds.size != getItemCount()
                    shouldSelectLazyLoadedItems = selectAll
                    setSelected(0, getItemCount(), selectAll)
                    toggleSelectAllIcon(mode.getMenu())
                    onSelectedItemsUpdated()
                    return true
                } else if (item.getItemId() == R.id.select_all_above) {
                    var firstSelectedPosition = 0
                    for (i in 0 until getItemCount()) {
                        if (isSelected(i)) {
                            firstSelectedPosition = i
                            break
                        }
                    }
                    setSelected(0, firstSelectedPosition, true)
                    return true
                } else if (item.getItemId() == R.id.select_all_below) {
                    shouldSelectLazyLoadedItems = true
                    var lastSelectedPosition = 0
                    for (i in getItemCount() - 1 downTo 0) {
                        if (isSelected(i)) {
                            lastSelectedPosition = i
                            break
                        }
                    }
                    setSelected(lastSelectedPosition + 1, getItemCount(), true)
                    return true
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
                shouldSelectLazyLoadedItems = false
                selectedIds.clear()
                callOnEndSelectMode()
                notifyDataSetChanged()
                backPressedCallback.remove()
            }
        })
        activity.onBackPressedDispatcher.addCallback(activity, backPressedCallback)
        onSelectedItemsUpdated()

        if (onSelectModeListener != null) {
            onSelectModeListener!!.onStartSelectMode()
        }
        notifyDataSetChanged()
    }

    /**
     * End action mode if currently in select mode, otherwise do nothing
     */
    fun endSelectMode() {
        if (inActionMode()) {
            actionMode!!.finish()
            actionMode = null
            callOnEndSelectMode()
        }
    }

    fun isSelected(pos: Int): Boolean {
        return selectedIds.contains(getItemId(pos))
    }

    /**
     * Set the selected state of item at given position
     *
     * @param pos      the position to select
     * @param selected true for selected state and false for unselected
     */
    fun setSelected(pos: Int, selected: Boolean) {
        if (selected) {
            selectedIds.add(getItemId(pos))
        } else {
            selectedIds.remove(getItemId(pos))
        }
        onSelectedItemsUpdated()
    }

    /**
     * Set the selected state of item for a given range
     *
     * @param startPos start position of range, inclusive
     * @param endPos   end position of range, inclusive
     * @param selected indicates the selection state
     * @throws IllegalArgumentException if start and end positions are not valid
     */
    @Throws(IllegalArgumentException::class)
    fun setSelected(startPos: Int, endPos: Int, selected: Boolean) {
        for (i in startPos until if (endPos < getItemCount()) endPos else getItemCount()) {
            setSelected(i, selected)
        }
        notifyItemRangeChanged(startPos, endPos - startPos)
    }

    protected open fun toggleSelection(pos: Int) {
        setSelected(pos, !isSelected(pos))
        notifyItemChanged(pos)

        if (selectedIds.isEmpty()) {
            endSelectMode()
        }
    }

    fun inActionMode(): Boolean {
        return actionMode != null
    }

    fun getSelectedCount(): Int {
        return selectedIds.size
    }

    private fun toggleSelectAllIcon(menu: Menu) {
        val allSelected = selectedIds.size == getItemCount()
        menu.findItem(R.id.select_toggle).setTitle(if (allSelected)
            R.string.deselect_all_label else R.string.select_all_label)
    }

    protected open fun onSelectedItemsUpdated() {
        if (actionMode == null) {
            return
        }
        var totalCount = getItemCount()
        var selectedCount = selectedIds.size
        if (totalNumberOfItems != COUNT_AUTOMATICALLY) {
            totalCount = totalNumberOfItems
            if (shouldSelectLazyLoadedItems) {
                selectedCount += totalNumberOfItems - getItemCount()
            }
        }
        actionMode!!.setTitle(activity.getResources()
                .getQuantityString(R.plurals.num_selected_label, selectedIds.size,
                selectedCount, totalCount))
    }

    fun setOnSelectModeListener(onSelectModeListener: OnSelectModeListener) {
        this.onSelectModeListener = onSelectModeListener
    }

    private fun callOnEndSelectMode() {
        if (onSelectModeListener != null) {
            onSelectModeListener!!.onEndSelectMode()
        }
    }

    fun shouldSelectLazyLoadedItems(): Boolean {
        return shouldSelectLazyLoadedItems
    }

    /**
     * Sets the total number of items that could be lazy-loaded.
     * Can also be set to [COUNT_AUTOMATICALLY] to simply use [getItemCount]
     */
    fun setTotalNumberOfItems(totalNumberOfItems: Int) {
        this.totalNumberOfItems = totalNumberOfItems
    }

    interface OnSelectModeListener {
        fun onStartSelectMode()

        fun onEndSelectMode()
    }

    companion object {
        const val COUNT_AUTOMATICALLY = -1
    }
}
