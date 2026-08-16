package de.danoeh.antennapod.storage.preferences

import android.content.Context
import android.content.SharedPreferences

class SynchronizationSettings {
    companion object {
        const val LAST_SYNC_ATTEMPT_TIMESTAMP = "last_sync_attempt_timestamp"
        private const val PREF_NAME = "synchronization"
        private const val SELECTED_SYNC_PROVIDER = "selected_sync_provider"
        private const val LAST_SYNC_ATTEMPT_SUCCESS = "last_sync_attempt_success"
        private const val LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP = "last_episode_actions_sync_timestamp"
        private const val LAST_SUBSCRIPTION_SYNC_TIMESTAMP = "last_sync_timestamp"

        private var prefs: SharedPreferences? = null

        @JvmStatic
        fun init(context: Context) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }

        @JvmStatic
        fun isProviderConnected(): Boolean {
            return getSelectedSyncProviderKey() != null
        }

        @JvmStatic
        fun resetTimestamps() {
            prefs!!.edit()
                    .putLong(LAST_SUBSCRIPTION_SYNC_TIMESTAMP, 0)
                    .putLong(LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP, 0)
                    .putLong(LAST_SYNC_ATTEMPT_TIMESTAMP, 0)
                    .apply()
        }

        @JvmStatic
        fun isLastSyncSuccessful(): Boolean {
            return prefs!!.getBoolean(LAST_SYNC_ATTEMPT_SUCCESS, false)
        }

        @JvmStatic
        fun getLastSyncAttempt(): Long {
            return prefs!!.getLong(LAST_SYNC_ATTEMPT_TIMESTAMP, 0)
        }

        @JvmStatic
        fun setSelectedSyncProvider(providerIdentifier: String?) {
            prefs!!.edit().putString(SELECTED_SYNC_PROVIDER, providerIdentifier).apply()
        }

        @JvmStatic
        fun getSelectedSyncProviderKey(): String? {
            return prefs!!.getString(SELECTED_SYNC_PROVIDER, null)
        }

        @JvmStatic
        fun updateLastSynchronizationAttempt() {
            prefs!!.edit().putLong(LAST_SYNC_ATTEMPT_TIMESTAMP, System.currentTimeMillis()).apply()
        }

        @JvmStatic
        fun setLastSynchronizationAttemptSuccess(isSuccess: Boolean) {
            prefs!!.edit().putBoolean(LAST_SYNC_ATTEMPT_SUCCESS, isSuccess).apply()
        }

        @JvmStatic
        fun getLastSubscriptionSynchronizationTimestamp(): Long {
            return prefs!!.getLong(LAST_SUBSCRIPTION_SYNC_TIMESTAMP, 0)
        }

        @JvmStatic
        fun setLastSubscriptionSynchronizationAttemptTimestamp(newTimeStamp: Long) {
            prefs!!.edit().putLong(LAST_SUBSCRIPTION_SYNC_TIMESTAMP, newTimeStamp).apply()
        }

        @JvmStatic
        fun getLastEpisodeActionSynchronizationTimestamp(): Long {
            return prefs!!.getLong(LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP, 0)
        }

        @JvmStatic
        fun setLastEpisodeActionSynchronizationAttemptTimestamp(timestamp: Long) {
            prefs!!.edit().putLong(LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP, timestamp).apply()
        }
    }
}
