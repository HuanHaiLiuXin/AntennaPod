package de.danoeh.antennapod.net.sync.gpoddernet.model

import androidx.collection.ArrayMap

import de.danoeh.antennapod.net.sync.serviceinterface.UploadChangesResponse
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import org.json.JSONArray
import org.json.JSONObject

class GpodnetEpisodeActionPostResponse : UploadChangesResponse {
    /**
     * URLs that should be updated. The key of the map is the original URL, the value of the map
     * is the sanitized URL.
     */
    private val updatedUrls: Map<String, String>

    private constructor(timestamp: Long, updatedUrls: Map<String, String>) : super(timestamp) {
        this.updatedUrls = updatedUrls
    }

    override fun toString(): String {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE)
    }

    companion object {
        /**
         * Creates a new GpodnetUploadChangesResponse-object from a JSON object that was
         * returned by an uploadChanges call.
         *
         * @throws org.json.JSONException If the method could not parse the JSONObject.
         */
        @JvmStatic
        fun fromJSONObject(objectString: String): GpodnetEpisodeActionPostResponse {
            val obj = JSONObject(objectString)
            val timestamp = obj.getLong("timestamp")
            val urls = obj.getJSONArray("update_urls")
            val updatedUrls = ArrayMap<String, String>(urls.length())
            for (i in 0 until urls.length()) {
                val urlPair = urls.getJSONArray(i)
                updatedUrls.put(urlPair.getString(0), urlPair.getString(1))
            }
            return GpodnetEpisodeActionPostResponse(timestamp, updatedUrls)
        }
    }
}
