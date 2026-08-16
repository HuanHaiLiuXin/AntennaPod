package de.danoeh.antennapod.ui.screen.feed

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.SortDialogBinding
import de.danoeh.antennapod.databinding.SortDialogItemActiveBinding
import de.danoeh.antennapod.databinding.SortDialogItemBinding
import de.danoeh.antennapod.model.feed.SortOrder

open class ItemSortDialog : BottomSheetDialogFragment() {
    @JvmField
    protected var sortOrder: SortOrder? = null

    @JvmField
    protected var viewBinding: SortDialogBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        viewBinding = SortDialogBinding.inflate(inflater)
        populateList()
        viewBinding!!.keepSortedCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            this@ItemSortDialog.onSelectionChanged()
        }
        return viewBinding!!.getRoot()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewBinding = null
    }

    protected open fun populateList() {
        viewBinding!!.gridLayout.removeAllViews()
        onAddItem(R.string.episode_title, SortOrder.EPISODE_TITLE_A_Z, SortOrder.EPISODE_TITLE_Z_A, true)
        onAddItem(R.string.feed_title, SortOrder.FEED_TITLE_A_Z, SortOrder.FEED_TITLE_Z_A, true)
        onAddItem(R.string.duration, SortOrder.DURATION_SHORT_LONG, SortOrder.DURATION_LONG_SHORT, true)
        onAddItem(R.string.date, SortOrder.DATE_OLD_NEW, SortOrder.DATE_NEW_OLD, false)
        onAddItem(R.string.size, SortOrder.SIZE_SMALL_LARGE, SortOrder.SIZE_LARGE_SMALL, false)
        onAddItem(R.string.filename, SortOrder.EPISODE_FILENAME_A_Z, SortOrder.EPISODE_FILENAME_Z_A, true)
        onAddItem(R.string.random, SortOrder.RANDOM, SortOrder.RANDOM, true)
        onAddItem(R.string.smart_shuffle, SortOrder.SMART_SHUFFLE_OLD_NEW, SortOrder.SMART_SHUFFLE_NEW_OLD, false)
    }

    protected open fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
        if (sortOrder == ascending || sortOrder == descending) {
            val item = SortDialogItemActiveBinding.inflate(
                    getLayoutInflater(), viewBinding!!.gridLayout, false)
            val other: SortOrder
            if (ascending == descending) {
                item.button.setText(title)
                other = ascending
            } else if (sortOrder == ascending) {
                item.button.setText(getString(title) + "\u00A0▲")
                other = descending
            } else {
                item.button.setText(getString(title) + "\u00A0▼")
                other = ascending
            }
            item.button.setOnClickListener {
                sortOrder = other
                populateList()
                onSelectionChanged()
            }
            viewBinding!!.gridLayout.addView(item.getRoot())
        } else {
            val item = SortDialogItemBinding.inflate(
                    getLayoutInflater(), viewBinding!!.gridLayout, false)
            item.button.setText(title)
            item.button.setOnClickListener {
                sortOrder = if (ascendingIsDefault) ascending else descending
                populateList()
                onSelectionChanged()
            }
            viewBinding!!.gridLayout.addView(item.getRoot())
        }
    }

    protected open fun onSelectionChanged() {
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            setupFullHeight(bottomSheetDialog)
        }
        return dialog
    }

    private fun setupFullHeight(bottomSheetDialog: BottomSheetDialog) {
        val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            val layoutParams = bottomSheet.getLayoutParams()
            bottomSheet.setLayoutParams(layoutParams)
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
    }
}
