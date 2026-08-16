package de.danoeh.antennapod.ui.screen.feed.preferences

import android.content.Context
import android.content.DialogInterface
import android.text.TextUtils
import android.view.LayoutInflater
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.SimpleChipAdapter
import de.danoeh.antennapod.databinding.EpisodeFilterDialogBinding
import de.danoeh.antennapod.model.feed.FeedFilter
import de.danoeh.antennapod.ui.common.ItemOffsetDecoration

/**
 * Displays a dialog with a text box for filtering episodes and two radio buttons for exclusion/inclusion
 */
abstract class EpisodeFilterDialog(context: Context, filter: FeedFilter) : MaterialAlertDialogBuilder(context) {
    private val viewBinding: EpisodeFilterDialogBinding
    private val termList: List<String>

    init {
        viewBinding = EpisodeFilterDialogBinding.inflate(LayoutInflater.from(context))

        setTitle(R.string.episode_filters_label)
        setView(viewBinding.getRoot())

        viewBinding.durationCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            viewBinding.episodeFilterDurationText.setEnabled(isChecked) }
        if (filter.hasMinimalDurationFilter()) {
            viewBinding.durationCheckBox.setChecked(true)
            // Store minimal duration in seconds, show in minutes
            viewBinding.episodeFilterDurationText
                    .setText((filter.getMinimalDurationFilter() / 60).toString())
        } else {
            viewBinding.episodeFilterDurationText.setEnabled(false)
        }

        if (filter.excludeOnly()) {
            termList = filter.getExcludeFilter()
            viewBinding.excludeRadio.setChecked(true)
        } else {
            termList = filter.getIncludeFilter()
            viewBinding.includeRadio.setChecked(true)
        }
        setupWordsList()

        setNegativeButton(R.string.cancel_label, null)
        setPositiveButton(R.string.confirm_label) { dialog, which -> onConfirmClick(dialog, which) }
    }

    private fun setupWordsList() {
        viewBinding.termsRecycler.setLayoutManager(GridLayoutManager(getContext(), 2))
        viewBinding.termsRecycler.addItemDecoration(ItemOffsetDecoration(getContext()!!, 4))
        val adapter = object : SimpleChipAdapter(getContext()!!) {
            override fun getChips(): List<String> {
                return termList
            }

            override fun onRemoveClicked(position: Int) {
                (termList as ArrayList<String>).removeAt(position)
                notifyDataSetChanged()
            }
        }
        viewBinding.termsRecycler.setAdapter(adapter)
        viewBinding.termsTextInput.setEndIconOnClickListener {
            val newWord = viewBinding.termsTextInput.getEditText()!!.getText().toString().replace("\"", "").trim()
            if (TextUtils.isEmpty(newWord) || termList.contains(newWord)) {
                return@setEndIconOnClickListener
            }
            (termList as ArrayList<String>).add(newWord)
            viewBinding.termsTextInput.getEditText()!!.setText("")
            adapter.notifyDataSetChanged()
        }
    }

    protected abstract fun onConfirmed(filter: FeedFilter)

    private fun onConfirmClick(dialog: DialogInterface, which: Int) {
        var minimalDuration = -1
        if (viewBinding.durationCheckBox.isChecked()) {
            try {
                // Store minimal duration in seconds
                minimalDuration = Integer.parseInt(
                        viewBinding.episodeFilterDurationText.getText().toString()) * 60
            } catch (e: NumberFormatException) {
                // Do not change anything on error
            }
        }
        var excludeFilter = ""
        var includeFilter = ""
        if (viewBinding.includeRadio.isChecked()) {
            includeFilter = toFilterString(termList)
        } else {
            excludeFilter = toFilterString(termList)
        }
        onConfirmed(FeedFilter(includeFilter, excludeFilter, minimalDuration))
    }

    private fun toFilterString(words: List<String>): String {
        val result = StringBuilder()
        for (word in words) {
            result.append("\"").append(word).append("\" ")
        }
        return result.toString()
    }
}
