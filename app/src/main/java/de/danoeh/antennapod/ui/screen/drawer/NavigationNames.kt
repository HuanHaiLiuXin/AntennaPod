package de.danoeh.antennapod.ui.screen.drawer

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.screen.FavoritesFragment
import de.danoeh.antennapod.ui.screen.AddFeedFragment
import de.danoeh.antennapod.ui.screen.AllEpisodesFragment
import de.danoeh.antennapod.ui.screen.InboxFragment
import de.danoeh.antennapod.ui.screen.PlaybackHistoryFragment
import de.danoeh.antennapod.ui.screen.download.CompletedDownloadsFragment
import de.danoeh.antennapod.ui.screen.home.HomeFragment
import de.danoeh.antennapod.ui.screen.queue.QueueFragment
import de.danoeh.antennapod.ui.screen.subscriptions.SubscriptionFragment
import de.danoeh.antennapod.ui.statistics.StatisticsFragment

abstract class NavigationNames {
    companion object {
        @DrawableRes
        @JvmStatic
        fun getDrawable(tag: String?): Int {
            return when (tag) {
                HomeFragment.TAG -> R.drawable.ic_home
                QueueFragment.TAG -> R.drawable.ic_playlist_play
                InboxFragment.TAG -> R.drawable.ic_inbox
                AllEpisodesFragment.TAG -> R.drawable.ic_feed
                CompletedDownloadsFragment.TAG -> R.drawable.ic_download
                PlaybackHistoryFragment.TAG -> R.drawable.ic_history
                SubscriptionFragment.TAG -> R.drawable.ic_subscriptions
                StatisticsFragment.TAG -> R.drawable.ic_chart_box
                AddFeedFragment.TAG -> R.drawable.ic_add
                FavoritesFragment.TAG -> R.drawable.ic_star
                else -> 0
            }
        }

        @StringRes
        @JvmStatic
        fun getLabel(tag: String?): Int {
            return when (tag) {
                HomeFragment.TAG -> R.string.home_label
                QueueFragment.TAG -> R.string.queue_label
                InboxFragment.TAG -> R.string.inbox_label
                AllEpisodesFragment.TAG -> R.string.episodes_label
                SubscriptionFragment.TAG -> R.string.subscriptions_label
                CompletedDownloadsFragment.TAG -> R.string.downloads_label
                PlaybackHistoryFragment.TAG -> R.string.playback_history_label
                StatisticsFragment.TAG -> R.string.statistics_label
                AddFeedFragment.TAG -> R.string.add_feed_label
                FavoritesFragment.TAG -> R.string.favorite_episodes_label
                NavListAdapter.SUBSCRIPTION_LIST_TAG -> R.string.subscriptions_list_label
                else -> 0
            }
        }

        @StringRes
        @JvmStatic
        fun getShortLabel(tag: String?): Int {
            return when (tag) {
                HomeFragment.TAG -> R.string.home_label_short
                QueueFragment.TAG -> R.string.queue_label_short
                InboxFragment.TAG -> R.string.inbox_label_short
                AllEpisodesFragment.TAG -> R.string.episodes_label_short
                SubscriptionFragment.TAG -> R.string.subscriptions_label_short
                CompletedDownloadsFragment.TAG -> R.string.downloads_label_short
                PlaybackHistoryFragment.TAG -> R.string.playback_history_label_short
                StatisticsFragment.TAG -> R.string.statistics_label_short
                AddFeedFragment.TAG -> R.string.add_feed_label_short
                FavoritesFragment.TAG -> R.string.favorite_episodes_label_short
                NavListAdapter.SUBSCRIPTION_LIST_TAG -> R.string.subscriptions_list_label
                else -> 0
            }
        }

        @JvmStatic
        fun getBottomNavigationItemId(tag: String?): Int {
            return when (tag) {
                QueueFragment.TAG -> R.id.bottom_navigation_queue
                InboxFragment.TAG -> R.id.bottom_navigation_inbox
                AllEpisodesFragment.TAG -> R.id.bottom_navigation_episodes
                CompletedDownloadsFragment.TAG -> R.id.bottom_navigation_downloads
                PlaybackHistoryFragment.TAG -> R.id.bottom_navigation_history
                FavoritesFragment.TAG -> R.id.bottom_navigation_favorites
                AddFeedFragment.TAG -> R.id.bottom_navigation_addfeed
                SubscriptionFragment.TAG -> R.id.bottom_navigation_subscriptions
                StatisticsFragment.TAG -> R.id.bottom_navigation_statistics
                else -> R.id.bottom_navigation_home
            }
        }

        @JvmStatic
        fun getBottomNavigationFragmentTag(id: Int): String? {
            if (id == R.id.bottom_navigation_queue) {
                return QueueFragment.TAG
            } else if (id == R.id.bottom_navigation_inbox) {
                return InboxFragment.TAG
            } else if (id == R.id.bottom_navigation_episodes) {
                return AllEpisodesFragment.TAG
            } else if (id == R.id.bottom_navigation_downloads) {
                return CompletedDownloadsFragment.TAG
            } else if (id == R.id.bottom_navigation_history) {
                return PlaybackHistoryFragment.TAG
            } else if (id == R.id.bottom_navigation_favorites) {
                return FavoritesFragment.TAG
            } else if (id == R.id.bottom_navigation_addfeed) {
                return AddFeedFragment.TAG
            } else if (id == R.id.bottom_navigation_subscriptions) {
                return SubscriptionFragment.TAG
            } else if (id == R.id.bottom_navigation_statistics) {
                return StatisticsFragment.TAG
            } else if (id == R.id.bottom_navigation_home) {
                return HomeFragment.TAG
            }
            return null
        }
    }
}
