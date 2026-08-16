package de.danoeh.antennapod.ui.screen

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.util.Pair

import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.common.ConfirmationDialog
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.event.playback.PlaybackHistoryEvent
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.ui.episodeslist.EpisodesListFragment
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class PlaybackHistoryFragment : EpisodesListFragment() {
    companion object {
        const val TAG = "PlaybackHistoryFragment"
        private val FILTER_HISTORY = FeedItemFilter(
                FeedItemFilter.IS_IN_HISTORY, FeedItemFilter.INCLUDE_ALL_FEED_STATES)
        private var scrollPosition: Pair<Int, Int>? = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        toolbar!!.inflateMenu(R.menu.playback_history)
        toolbar!!.setTitle(R.string.playback_history_label)
        updateToolbar()
        emptyView.setIcon(R.drawable.ic_history)
        emptyView.setTitle(R.string.no_history_head_label)
        emptyView.setMessage(R.string.no_history_label)
        return root
    }

    override fun getFilter(): FeedItemFilter {
        return FeedItemFilter.unfiltered()
    }

    override fun getFragmentTag(): String {
        return TAG
    }

    override fun onPause() {
        super.onPause()
        scrollPosition = recyclerView.getScrollPosition()
    }

    override fun onItemsFirstLoaded() {
        recyclerView.restoreScrollPosition(scrollPosition)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onMenuItemClick(item)) {
            return true
        }
        if (item.getItemId() == R.id.clear_history_item) {
            val conDialog = object : ConfirmationDialog(getActivity()!!,
                    R.string.clear_history_label,
                    R.string.clear_playback_history_msg) {
                override fun onConfirmButtonPressed(dialog: DialogInterface) {
                    dialog.dismiss()
                    DBWriter.clearPlaybackHistory()
                }
            }
            conDialog.createNewDialog().show()
            return true
        }
        return false
    }

    override fun updateToolbar() {
        // Not calling super, as we do not have a refresh button that could be updated
        toolbar!!.getMenu().findItem(R.id.clear_history_item).setVisible(!episodes.isEmpty())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onHistoryUpdated(event: PlaybackHistoryEvent) {
        loadItems()
        updateToolbar()
    }

    override fun loadData(): List<FeedItem> {
        return DBReader.getEpisodes(0, page * EPISODES_PER_PAGE, FILTER_HISTORY,
                SortOrder.COMPLETION_DATE_NEW_OLD)
    }

    override fun loadMoreData(page: Int): List<FeedItem> {
        return DBReader.getEpisodes((page - 1) * EPISODES_PER_PAGE, EPISODES_PER_PAGE, FILTER_HISTORY,
                SortOrder.COMPLETION_DATE_NEW_OLD)
    }

    override fun loadTotalItemCount(): Int {
        return DBReader.getTotalEpisodeCount(FILTER_HISTORY)
    }
}
