package de.danoeh.antennapod.ui.screen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.util.Pair

import de.danoeh.antennapod.R
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.episodeslist.EpisodesListFragment

class FavoritesFragment : EpisodesListFragment() {
    companion object {
        const val TAG = "FavoritesFragment"
        private val FILTER_FAVORITES = FeedItemFilter(
                FeedItemFilter.IS_FAVORITE, FeedItemFilter.INCLUDE_ALL_FEED_STATES)
        private var scrollPosition: Pair<Int, Int>? = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        toolbar!!.inflateMenu(R.menu.favorites)
        toolbar!!.setTitle(R.string.favorite_episodes_label)
        updateToolbar()
        emptyView.setIcon(R.drawable.ic_star)
        emptyView.setTitle(R.string.no_fav_episodes_head_label)
        emptyView.setMessage(R.string.no_fav_episodes_label)
        return root
    }

    override fun getFilter(): FeedItemFilter {
        return FILTER_FAVORITES
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

    override fun loadData(): List<FeedItem> {
        return DBReader.getEpisodes(0, page * EPISODES_PER_PAGE, FILTER_FAVORITES,
                UserPreferences.getAllEpisodesSortOrder())
    }

    override fun loadMoreData(page: Int): List<FeedItem> {
        return DBReader.getEpisodes((page - 1) * EPISODES_PER_PAGE, EPISODES_PER_PAGE, FILTER_FAVORITES,
                UserPreferences.getAllEpisodesSortOrder())
    }

    override fun loadTotalItemCount(): Int {
        return DBReader.getTotalEpisodeCount(FILTER_FAVORITES)
    }
}
