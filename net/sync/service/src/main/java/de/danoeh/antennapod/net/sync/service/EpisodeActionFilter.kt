package de.danoeh.antennapod.net.sync.service

import android.util.Log

import androidx.collection.ArrayMap
import androidx.core.util.Pair

import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction

class EpisodeActionFilter {

    companion object {
        const val TAG = "EpisodeActionFilter"

        @JvmStatic
        fun getRemoteActionsOverridingLocalActions(
                remoteActions: List<EpisodeAction>,
                queuedEpisodeActions: List<EpisodeAction>): MutableMap<Pair<String, String>, EpisodeAction> {
            // make sure more recent local actions are not overwritten by older remote actions
            val remoteActionsThatOverrideLocalActions = ArrayMap<Pair<String, String>, EpisodeAction>()
            val localMostRecentPlayActions =
                    createUniqueLocalMostRecentPlayActions(queuedEpisodeActions)
            for (remoteAction in remoteActions) {
                val key = Pair(remoteAction.getPodcast(), remoteAction.getEpisode())
                when (remoteAction.getAction()) {
                    EpisodeAction.Action.NEW, EpisodeAction.Action.DOWNLOAD -> {
                    }
                    EpisodeAction.Action.PLAY -> {
                        val localMostRecent = localMostRecentPlayActions[key]
                        if (secondActionOverridesFirstAction(remoteAction, localMostRecent)) {
                            continue
                        }
                        val remoteMostRecentAction = remoteActionsThatOverrideLocalActions[key]
                        if (secondActionOverridesFirstAction(remoteAction, remoteMostRecentAction)) {
                            continue
                        }
                        remoteActionsThatOverrideLocalActions.put(key, remoteAction)
                    }
                    EpisodeAction.Action.DELETE ->
                        // NEVER EVER call DBWriter.deleteFeedMediaOfItem() here, leads to an infinite loop
                        continue
                    else -> Log.e(TAG, "Unknown remoteAction: " + remoteAction)
                }
            }

            return remoteActionsThatOverrideLocalActions
        }

        private fun createUniqueLocalMostRecentPlayActions(
                queuedEpisodeActions: List<EpisodeAction>): MutableMap<Pair<String, String>, EpisodeAction> {
            val localMostRecentPlayAction: MutableMap<Pair<String, String>, EpisodeAction>
            localMostRecentPlayAction = ArrayMap()
            for (action in queuedEpisodeActions) {
                val key = Pair(action.getPodcast(), action.getEpisode())
                val mostRecent = localMostRecentPlayAction[key]
                if (mostRecent == null || mostRecent.getTimestamp() == null) {
                    localMostRecentPlayAction.put(key, action)
                } else if (mostRecent.getTimestamp()!!.before(action.getTimestamp())) {
                    localMostRecentPlayAction.put(key, action)
                }
            }
            return localMostRecentPlayAction
        }

        private fun secondActionOverridesFirstAction(firstAction: EpisodeAction,
                                                     secondAction: EpisodeAction?): Boolean {
            return secondAction != null
                    && secondAction.getTimestamp() != null
                    && (firstAction.getTimestamp() == null
                            || secondAction.getTimestamp()!!.after(firstAction.getTimestamp()))
        }
    }
}
