package de.danoeh.antennapod.net.sync.gpoddernet.mapper

import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeActionChanges
import de.danoeh.antennapod.net.sync.serviceinterface.SubscriptionChanges
import org.json.JSONArray
import org.json.JSONObject

import java.util.ArrayList
import java.util.LinkedList

class ResponseMapper {
    companion object {
        @JvmStatic
        fun readSubscriptionChangesFromJsonObject(obj: JSONObject): SubscriptionChanges {

            val added = LinkedList<String>()
            val jsonAdded = obj.getJSONArray("add")
            for (i in 0 until jsonAdded.length()) {
                var addedUrl = jsonAdded.getString(i)
                // gpodder escapes colons unnecessarily
                addedUrl = addedUrl.replace("%3A", ":")
                added.add(addedUrl)
            }

            val removed = LinkedList<String>()
            val jsonRemoved = obj.getJSONArray("remove")
            for (i in 0 until jsonRemoved.length()) {
                var removedUrl = jsonRemoved.getString(i)
                // gpodder escapes colons unnecessarily
                removedUrl = removedUrl.replace("%3A", ":")
                removed.add(removedUrl)
            }

            val timestamp = obj.getLong("timestamp")
            return SubscriptionChanges(added, removed, timestamp)
        }

        @JvmStatic
        fun readEpisodeActionsFromJsonObject(obj: JSONObject): EpisodeActionChanges {

            val episodeActions = ArrayList<EpisodeAction>()

            val timestamp = obj.getLong("timestamp")
            val jsonActions = obj.getJSONArray("actions")
            for (i in 0 until jsonActions.length()) {
                val jsonAction = jsonActions.getJSONObject(i)
                val episodeAction = EpisodeAction.readFromJsonObject(jsonAction)
                if (episodeAction != null) {
                    episodeActions.add(episodeAction)
                }
            }
            return EpisodeActionChanges(episodeActions, timestamp)
        }
    }
}
