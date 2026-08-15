package de.danoeh.antennapod.net.download.service.episode.autodownload

import de.danoeh.antennapod.storage.preferences.UserPreferences

abstract class EpisodeCleanupAlgorithmFactory {
    companion object {
        @JvmStatic
        fun build(): EpisodeCleanupAlgorithm {
            val cleanupValue = UserPreferences.getEpisodeCleanupValue()
            when (cleanupValue) {
                UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE -> return ExceptFavoriteCleanupAlgorithm()
                UserPreferences.EPISODE_CLEANUP_QUEUE -> return APQueueCleanupAlgorithm()
                UserPreferences.EPISODE_CLEANUP_NULL -> return APNullCleanupAlgorithm()
                else -> return APCleanupAlgorithm(cleanupValue)
            }
        }
    }
}
