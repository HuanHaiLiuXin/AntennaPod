package de.danoeh.antennapod.ui

import android.os.Bundle
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.ui.screen.feed.ItemFilterDialog

class FeedItemFilterDialog : ItemFilterDialog() {

    override fun onFilterChanged(newFilterValues: Set<String>) {
        val feedId = getArguments()!!.getLong(ARGUMENT_FEED_ID)
        DBWriter.setFeedItemsFilter(feedId, newFilterValues)
    }

    companion object {
        private const val ARGUMENT_FEED_ID = "feedId"

        @JvmStatic
        fun newInstance(feed: Feed): FeedItemFilterDialog {
            val dialog = FeedItemFilterDialog()
            val arguments = Bundle()
            arguments.putSerializable(ARGUMENT_FILTER, feed.getItemFilter())
            arguments.putLong(ARGUMENT_FEED_ID, feed.getId())
            dialog.setArguments(arguments)
            return dialog
        }
    }
}
