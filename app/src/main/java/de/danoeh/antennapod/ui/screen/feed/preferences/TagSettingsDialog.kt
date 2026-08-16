package de.danoeh.antennapod.ui.screen.feed.preferences

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.EditTagsDialogBinding
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedCounter
import de.danoeh.antennapod.model.feed.FeedOrder
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.database.NavDrawerData
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.SimpleChipAdapter
import de.danoeh.antennapod.ui.common.Keyboard
import de.danoeh.antennapod.ui.common.ItemOffsetDecoration
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.util.ArrayList
import java.util.HashSet

class TagSettingsDialog : DialogFragment() {
    companion object {
        const val TAG = "TagSettingsDialog"
        private const val ARG_FEED_PREFERENCES = "feed_preferences"

        @JvmStatic
        fun newInstance(preferencesList: List<FeedPreferences>): TagSettingsDialog {
            val fragment = TagSettingsDialog()
            val args = Bundle()
            args.putSerializable(ARG_FEED_PREFERENCES, ArrayList(preferencesList))
            fragment.setArguments(args)
            return fragment
        }
    }

    private var displayedTags: MutableList<String>? = null
    private var viewBinding: EditTagsDialogBinding? = null
    private var adapter: SimpleChipAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val feedPreferencesList =
                requireArguments().getSerializable(ARG_FEED_PREFERENCES) as ArrayList<FeedPreferences>
        val commonTags = HashSet(feedPreferencesList.get(0).getTags())

        for (preference in feedPreferencesList) {
            commonTags.retainAll(preference.getTags()!!)
        }
        displayedTags = ArrayList(commonTags)
        displayedTags!!.remove(FeedPreferences.TAG_ROOT)

        viewBinding = EditTagsDialogBinding.inflate(getLayoutInflater())
        viewBinding!!.tagsRecycler.setLayoutManager(GridLayoutManager(getContext(), 2))
        viewBinding!!.tagsRecycler.addItemDecoration(ItemOffsetDecoration(requireContext(), 4))
        adapter = object : SimpleChipAdapter(requireContext()) {
            override fun getChips(): List<String> {
                return displayedTags!!
            }

            override fun onRemoveClicked(position: Int) {
                displayedTags!!.removeAt(position)
                notifyDataSetChanged()
            }
        }
        viewBinding!!.tagsRecycler.setAdapter(adapter)
        viewBinding!!.rootFolderCheckbox.setChecked(commonTags.contains(FeedPreferences.TAG_ROOT))
        viewBinding!!.rootFolderCheckbox.setVisibility(if (UserPreferences.isBottomNavigationEnabled())
            View.GONE else View.VISIBLE)

        viewBinding!!.newTagTextInput.setEndIconOnClickListener {
            addTag(viewBinding!!.newTagEditText.getText().toString().trim())
        }

        loadTags()
        viewBinding!!.newTagEditText.setThreshold(1)
        viewBinding!!.newTagEditText.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                viewBinding!!.newTagEditText.showDropDown()
                viewBinding!!.newTagEditText.requestFocus()
                return false
            }
        })

        if (feedPreferencesList.size > 1) {
            viewBinding!!.commonTagsInfo.setVisibility(View.VISIBLE)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
        dialog.setView(viewBinding!!.getRoot())
        dialog.setTitle(R.string.feed_tags_label)
        dialog.setPositiveButton(android.R.string.ok) { d, input ->
            addTag(viewBinding!!.newTagEditText.getText().toString().trim())
            updatePreferencesTags(feedPreferencesList, commonTags)
        }
        dialog.setNegativeButton(R.string.cancel_label, null)
        return dialog.create()
    }

    private fun loadTags() {
        Observable.fromCallable<List<String>>(
                {
                    val data = DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE,
                            Feed.STATE_SUBSCRIBED)
                    val folders: MutableList<String> = ArrayList()
                    for (item in data.tags) {
                        if (FeedPreferences.TAG_ROOT != item.getTitle()
                                && FeedPreferences.TAG_UNTAGGED != item.getTitle()) {
                            folders.add(item.getTitle())
                        }
                    }
                    folders
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { result ->
                            val acAdapter = ArrayAdapter(requireContext(),
                                    R.layout.single_tag_text_view, result)
                            viewBinding!!.newTagEditText.setAdapter(acAdapter)
                        }, { error ->
                            Log.e(TAG, Log.getStackTraceString(error))
                        })
    }

    private fun addTag(name: String) {
        if (TextUtils.isEmpty(name) || displayedTags!!.contains(name) || FeedPreferences.TAG_UNTAGGED == name) {
            viewBinding!!.newTagEditText.requestFocus()
            Keyboard.show(requireContext(), viewBinding!!.newTagEditText)
            return
        }
        displayedTags!!.add(name)
        viewBinding!!.newTagEditText.setText("")
        adapter!!.notifyDataSetChanged()
    }

    private fun updatePreferencesTags(feedPreferencesList: List<FeedPreferences>, commonTags: Set<String>) {
        if (viewBinding!!.rootFolderCheckbox.isChecked()) {
            displayedTags!!.add(FeedPreferences.TAG_ROOT)
        }
        for (preferences in feedPreferencesList) {
            (preferences.getTags() as HashSet<String>).removeAll(commonTags)
            (preferences.getTags() as HashSet<String>).addAll(displayedTags!!)
            DBWriter.setFeedPreferences(preferences)
        }
    }
}
