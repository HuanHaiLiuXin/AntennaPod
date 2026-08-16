package de.danoeh.antennapod.ui.screen.feed

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup

import java.util.Collections
import java.util.HashSet

import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.FilterDialogBinding
import de.danoeh.antennapod.databinding.FilterDialogRowBinding
import de.danoeh.antennapod.model.feed.FeedItemFilter

abstract class ItemFilterDialog : BottomSheetDialogFragment() {
    companion object {
        const val ARGUMENT_FILTER = "filter"
    }

    private var rows: LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val layout = inflater.inflate(R.layout.filter_dialog, null, false)
        val binding = FilterDialogBinding.bind(layout)
        rows = binding.filterRows
        val filter = getArguments()!!.getSerializable(ARGUMENT_FILTER) as FeedItemFilter

        //add filter rows
        for (item in FeedItemFilterGroup.values()) {
            val rowBinding = FilterDialogRowBinding.inflate(inflater)
            rowBinding.getRoot().addOnButtonCheckedListener { group, checkedId, isChecked ->
                onFilterChanged(getNewFilterValues()) }
            rowBinding.filterButton1.setText(item.values[0].displayName)
            rowBinding.filterButton1.setTag(item.values[0].filterId)
            rowBinding.filterButton2.setText(item.values[1].displayName)
            rowBinding.filterButton2.setTag(item.values[1].filterId)
            rowBinding.filterButton1.setMaxLines(3)
            rowBinding.filterButton1.setSingleLine(false)
            rowBinding.filterButton2.setMaxLines(3)
            rowBinding.filterButton2.setSingleLine(false)
            rows!!.addView(rowBinding.getRoot(), rows!!.getChildCount() - 1)
        }

        binding.confirmFiltermenu.setOnClickListener { dismiss() }
        binding.resetFiltermenu.setOnClickListener {
            onFilterChanged(Collections.emptySet())
            for (i in 0 until rows!!.getChildCount()) {
                if (rows!!.getChildAt(i) is MaterialButtonToggleGroup) {
                    (rows!!.getChildAt(i) as MaterialButtonToggleGroup).clearChecked()
                }
            }
        }

        for (filterId in filter.getValues()) {
            if (!TextUtils.isEmpty(filterId)) {
                val button = layout.findViewWithTag<Button>(filterId)
                if (button != null) {
                    (button.getParent() as MaterialButtonToggleGroup).check(button.getId())
                }
            }
        }
        return layout
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

    protected fun getNewFilterValues(): Set<String> {
        val newFilterValues = HashSet<String>()
        for (i in 0 until rows!!.getChildCount()) {
            if (rows!!.getChildAt(i) !is MaterialButtonToggleGroup) {
                continue
            }
            val group = rows!!.getChildAt(i) as MaterialButtonToggleGroup
            if (group.getCheckedButtonId() == View.NO_ID) {
                continue
            }
            val tag = group.findViewById<View>(group.getCheckedButtonId()).getTag() as String?
            if (tag == null) { // Clear buttons use no tag
                continue
            }
            newFilterValues.add(tag)
        }
        return newFilterValues
    }

    abstract fun onFilterChanged(newFilterValues: Set<String>)
}
