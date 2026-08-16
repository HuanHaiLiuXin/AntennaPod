package de.danoeh.antennapod.ui.screen.subscriptions

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
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.FilterDialogBinding
import de.danoeh.antennapod.databinding.FilterDialogRowBinding
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.model.feed.SubscriptionsFilter
import de.danoeh.antennapod.storage.preferences.UserPreferences
import org.greenrobot.eventbus.EventBus

import java.util.Arrays
import java.util.Collections
import java.util.HashSet

class SubscriptionsFilterDialog : BottomSheetDialogFragment() {
    private var rows: LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?): View? {
        val subscriptionsFilter = UserPreferences.getSubscriptionsFilter()
        val dialogBinding = FilterDialogBinding.inflate(inflater)
        rows = dialogBinding.filterRows

        for (item in SubscriptionsFilterGroup.values()) {
            val binding = FilterDialogRowBinding.inflate(inflater)
            binding.getRoot().addOnButtonCheckedListener(
                    { group, checkedId, isChecked -> updateFilter(getFilterValues()) })
            binding.buttonGroup.setWeightSum(item.values.size.toFloat())
            binding.filterButton1.setText(item.values[0].displayName)
            binding.filterButton1.setTag(item.values[0].filterId)
            if (item.values.size == 2) {
                binding.filterButton2.setText(item.values[1].displayName)
                binding.filterButton2.setTag(item.values[1].filterId)
            } else {
                binding.filterButton2.setVisibility(View.GONE)
            }
            binding.filterButton1.setMaxLines(3)
            binding.filterButton1.setSingleLine(false)
            binding.filterButton2.setMaxLines(3)
            binding.filterButton2.setSingleLine(false)
            rows!!.addView(binding.getRoot(), rows!!.getChildCount() - 1)
        }

        val filterValues = HashSet(Arrays.asList(*subscriptionsFilter.getValues()))
        for (filterId in filterValues) {
            if (!TextUtils.isEmpty(filterId)) {
                val button = dialogBinding.getRoot().findViewWithTag<Button>(filterId)
                if (button != null) {
                    (button.getParent() as MaterialButtonToggleGroup).check(button.getId())
                }
            }
        }

        dialogBinding.confirmFiltermenu.setOnClickListener {
            updateFilter(getFilterValues())
            dismiss()
        }
        dialogBinding.resetFiltermenu.setOnClickListener {
            updateFilter(Collections.emptySet())
            for (i in 0 until rows!!.getChildCount()) {
                if (rows!!.getChildAt(i) is MaterialButtonToggleGroup) {
                    (rows!!.getChildAt(i) as MaterialButtonToggleGroup).clearChecked()
                }
            }
        }
        return dialogBinding.getRoot()
    }

    private fun getFilterValues(): Set<String> {
        val filterValues = HashSet<String>()
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
            filterValues.add(tag)
        }
        return filterValues
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

    companion object {
        private fun updateFilter(filterValues: Set<String>) {
            val subscriptionsFilter = SubscriptionsFilter(filterValues.toTypedArray())
            UserPreferences.setSubscriptionsFilter(subscriptionsFilter)
            EventBus.getDefault().post(FeedItemEvent(Collections.emptyList(), true))
        }
    }
}
