package de.danoeh.antennapod.net.sync.serviceinterface

import de.danoeh.antennapod.model.feed.FeedMedia

abstract class SynchronizationQueue {
    companion object {
        private var instance: SynchronizationQueue? = null

        @JvmStatic
        fun getInstance(): SynchronizationQueue? {
            return instance
        }

        @JvmStatic
        fun setInstance(instance: SynchronizationQueue?) {
            SynchronizationQueue.instance = instance
        }
    }

    /**
     * Sync bundled events after some delay to avoid spamming the sync server.
     */
    abstract fun sync()

    abstract fun syncImmediately()

    abstract fun fullSync()

    abstract fun syncIfNotSyncedRecently()

    abstract fun clear()

    abstract fun enqueueFeedAdded(downloadUrl: String?)

    abstract fun enqueueFeedRemoved(downloadUrl: String?)

    abstract fun enqueueEpisodeAction(action: EpisodeAction)

    abstract fun enqueueEpisodePlayed(media: FeedMedia, completed: Boolean)
}
