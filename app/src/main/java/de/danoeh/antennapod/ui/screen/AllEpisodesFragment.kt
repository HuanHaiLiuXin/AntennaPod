package de.danoeh.antennapod.ui.screen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.util.Pair

import de.danoeh.antennapod.R
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.AllEpisodesFilterDialog
import de.danoeh.antennapod.ui.screen.feed.ItemSortDialog
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.episodeslist.EpisodesListFragment
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * Shows all episodes (possibly filtered by user).
 */
class AllEpisodesFragment : EpisodesListFragment() {
    companion object {
        const val TAG = "EpisodesFragment"
        const val PREF_NAME = "PrefAllEpisodesFragment"
        private var scrollPosition: Pair<Int, Int>? = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        toolbar!!.inflateMenu(R.menu.episodes)
        toolbar!!.setTitle(R.string.episodes_label)
        updateToolbar()
        updateFilterUi()
        txtvInformation.setOnClickListener {
            AllEpisodesFilterDialog.newInstance(getFilter()).show(getChildFragmentManager(), null)
        }

        val largePadding = displayUpArrow || !UserPreferences.isBottomNavigationEnabled()
        val paddingHorizontal = (getResources().getDisplayMetrics().density * (if (largePadding) 60 else 16)).toInt()
        val paddingVertical = (getResources().getDisplayMetrics().density * 4).toInt()
        txtvInformation.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
        return root
    }

    override fun loadData(): List<FeedItem> {
        return DBReader.getEpisodes(0, page * EPISODES_PER_PAGE, getFilter(),
                UserPreferences.getAllEpisodesSortOrder())
    }

    override fun loadMoreData(page: Int): List<FeedItem> {
        return DBReader.getEpisodes((page - 1) * EPISODES_PER_PAGE, EPISODES_PER_PAGE, getFilter(),
                UserPreferences.getAllEpisodesSortOrder())
    }

    override fun loadTotalItemCount(): Int {
        return DBReader.getTotalEpisodeCount(getFilter())
    }

    override fun getFilter(): FeedItemFilter {
        val filter = FeedItemFilter(UserPreferences.getPrefFilterAllEpisodes())
        if (filter.showIsFavorite) {
            return FeedItemFilter(filter, FeedItemFilter.INCLUDE_ALL_FEED_STATES)
        } else {
            return filter
        }
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
        if (item.getItemId() == R.id.filter_items) {
            AllEpisodesFilterDialog.newInstance(getFilter()).show(getChildFragmentManager(), null)
            return true
        } else if (item.getItemId() == R.id.episodes_sort) {
            AllEpisodesSortDialog().show(getChildFragmentManager().beginTransaction(), "SortDialog")
            return true
        }
        return false
    }

    @Subscribe
    fun onFilterChanged(event: AllEpisodesFilterDialog.AllEpisodesFilterChangedEvent) {
        UserPreferences.setPrefFilterAllEpisodes(StringUtils.join(event.filterValues, ","))
        updateFilterUi()
        page = 1
        loadItems()
    }

    private fun updateFilterUi() {
        swipeActions.setFilter(getFilter())
        if (getFilter().getValues().size == 0) {
            txtvInformation.setVisibility(View.GONE)
            emptyView.setMessage(R.string.no_all_episodes_label)
        } else if (listAdapter.inActionMode()) {
            txtvInformation.setVisibility(View.INVISIBLE)
        } else {
            txtvInformation.setVisibility(View.VISIBLE)
            emptyView.setMessage(R.string.no_all_episodes_filtered_label)
        }
    }

    override fun onStartSelectMode() {
        super.onStartSelectMode()
        updateFilterUi()
    }

    override fun onEndSelectMode() {
        super.onEndSelectMode()
        updateFilterUi()
    }

    class AllEpisodesSortDialog : ItemSortDialog() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sortOrder = UserPreferences.getAllEpisodesSortOrder()
        }

        override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
            if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.DURATION_SHORT_LONG) {
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
            }
        }

        override fun onSelectionChanged() {
            super.onSelectionChanged()
            UserPreferences.setAllEpisodesSortOrder(sortOrder!!)
            EventBus.getDefault().post(FeedListUpdateEvent(0))
        }
    }
}
