package de.danoeh.antennapod.storage.database.mapper

import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.database.PodDBAdapter
import de.danoeh.antennapod.storage.preferences.UserPreferences

class FeedItemSortQuery {
    companion object {
        @JvmStatic
        fun generateFrom(sortOrder: SortOrder?): String {
            var sortOrder = sortOrder
            if (sortOrder == null || SortOrder.GLOBAL_DEFAULT == sortOrder) {
                sortOrder = UserPreferences.getPrefGlobalSortedOrder()
            }
            when (sortOrder) {
                SortOrder.EPISODE_TITLE_A_Z -> return PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_TITLE + " " + "ASC"
                SortOrder.EPISODE_TITLE_Z_A -> return PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_TITLE + " " + "DESC"
                SortOrder.DURATION_SHORT_LONG -> return PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_DURATION + " " + "ASC"
                SortOrder.DURATION_LONG_SHORT -> return PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_DURATION + " " + "DESC"
                SortOrder.SIZE_SMALL_LARGE -> return PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_SIZE + " " + "ASC"
                SortOrder.SIZE_LARGE_SMALL -> return PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_SIZE + " " + "DESC"
                SortOrder.COMPLETION_DATE_NEW_OLD -> return PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." +
                        PodDBAdapter.KEY_LAST_PLAYED_TIME_HISTORY + " " + "DESC"
                SortOrder.DATE_OLD_NEW -> return PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_PUBDATE + " " + "ASC"
                SortOrder.EPISODE_FILENAME_A_Z -> return PodDBAdapter.KEY_LINK + " " + "ASC"
                SortOrder.EPISODE_FILENAME_Z_A -> return PodDBAdapter.KEY_LINK + " " + "DESC"
                else -> return PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_PUBDATE + " " + "DESC"
            }
        }
    }
}
