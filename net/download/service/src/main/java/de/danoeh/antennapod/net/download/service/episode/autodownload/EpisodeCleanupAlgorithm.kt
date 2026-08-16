package de.danoeh.antennapod.net.download.service.episode.autodownload

import android.content.Context
import android.util.Log
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.preferences.UserPreferences

abstract class EpisodeCleanupAlgorithm {

    /**
     * Deletes downloaded episodes that are no longer needed. What episodes are deleted and how many
     * of them depends on the implementation.
     *
     * @param context     Can be used for accessing the database
     * @param numToRemove An additional parameter. This parameter is either returned by getDefaultCleanupParameter
     *                    or getPerformCleanupParameter.
     * @return The number of episodes that were deleted.
     */
    protected abstract fun performCleanup(context: Context, numToRemove: Int): Int

    fun performCleanup(context: Context): Int {
        return performCleanup(context, getDefaultCleanupParameter())
    }

    /**
     * Returns a parameter for performCleanup. The implementation of this interface should decide how much
     * space to free to satisfy the episode cache conditions. If the conditions are already satisfied, this
     * method should not have any effects.
     */
    protected abstract fun getDefaultCleanupParameter(): Int

    /**
     * Cleans up just enough episodes to make room for the requested number
     *
     * @param context            Can be used for accessing the database
     * @param amountOfRoomNeeded the number of episodes we need space for
     * @return The number of epiosdes that were deleted
     */
    fun makeRoomForEpisodes(context: Context, amountOfRoomNeeded: Int): Int {
        return performCleanup(context, getNumEpisodesToCleanup(amountOfRoomNeeded))
    }

    /**
     * @return the number of episodes/items that *could* be cleaned up, if needed
     */
    abstract fun getReclaimableItems(): Int

    /**
     * @param amountOfRoomNeeded the number of episodes we want to download
     * @return the number of episodes to delete in order to make room
     */
    internal fun getNumEpisodesToCleanup(amountOfRoomNeeded: Int): Int {
        if (amountOfRoomNeeded >= 0
                && UserPreferences.getEpisodeCacheSize() != UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED) {
            val downloadedEpisodes = DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.DOWNLOADED))
            if (downloadedEpisodes + amountOfRoomNeeded >= UserPreferences
                    .getEpisodeCacheSize()) {

                return downloadedEpisodes + amountOfRoomNeeded
                        - UserPreferences.getEpisodeCacheSize()
            }
        }
        return 0
    }
}
