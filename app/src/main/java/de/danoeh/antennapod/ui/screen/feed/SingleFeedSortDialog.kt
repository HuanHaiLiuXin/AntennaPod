package de.danoeh.antennapod.ui.screen.feed

import android.os.Bundle

import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.SortDialogItemActiveBinding
import de.danoeh.antennapod.databinding.SortDialogItemBinding
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.database.DBWriter

class SingleFeedSortDialog : ItemSortDialog() {
    companion object {
        private const val ARG_FEED_ID = "feedId"
        private const val ARG_FEED_IS_LOCAL = "isLocal"
        private const val ARG_SORT_ORDER = "sortOrder"

        @JvmStatic
        fun newInstance(feed: Feed): SingleFeedSortDialog {
            val bundle = Bundle()
            bundle.putLong(ARG_FEED_ID, feed.getId())
            bundle.putBoolean(ARG_FEED_IS_LOCAL, feed.isLocalFeed())
            if (feed.getSortOrder() == null) {
                bundle.putString(ARG_SORT_ORDER, SortOrder.GLOBAL_DEFAULT.code.toString())
            } else {
                bundle.putString(ARG_SORT_ORDER, feed.getSortOrder()!!.code.toString())
            }
            val dialog = SingleFeedSortDialog()
            dialog.setArguments(bundle)
            return dialog
        }
    }

    override fun populateList() {
        super.populateList()
        if (sortOrder == SortOrder.GLOBAL_DEFAULT) {
            val item = SortDialogItemActiveBinding.inflate(
                    getLayoutInflater(), viewBinding!!.gridLayout, false)
            item.button.setText(R.string.global_default)
            viewBinding!!.gridLayout.addView(item.getRoot())
        } else {
            val item = SortDialogItemBinding.inflate(
                    getLayoutInflater(), viewBinding!!.gridLayout, false)
            item.button.setText(R.string.global_default)
            item.button.setOnClickListener {
                sortOrder = SortOrder.GLOBAL_DEFAULT
                populateList()
                onSelectionChanged()
            }
            viewBinding!!.gridLayout.addView(item.getRoot())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sortOrder = SortOrder.fromCodeString(requireArguments().getString(ARG_SORT_ORDER))
    }

    override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
        if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.DURATION_SHORT_LONG
                || ascending == SortOrder.EPISODE_TITLE_A_Z
                || (requireArguments().getBoolean(ARG_FEED_IS_LOCAL) && ascending == SortOrder.EPISODE_FILENAME_A_Z)) {
            super.onAddItem(title, ascending, descending, ascendingIsDefault)
        }
    }

    override fun onSelectionChanged() {
        super.onSelectionChanged()
        DBWriter.setFeedItemSortOrder(requireArguments().getLong(ARG_FEED_ID), sortOrder!!)
    }
}
