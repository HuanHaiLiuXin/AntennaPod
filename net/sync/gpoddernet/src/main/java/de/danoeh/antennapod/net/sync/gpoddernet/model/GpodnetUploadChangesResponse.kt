package de.danoeh.antennapod.net.sync.gpoddernet.model

import androidx.collection.ArrayMap

import de.danoeh.antennapod.net.sync.serviceinterface.UploadChangesResponse
import org.json.JSONArray
import org.json.JSONObject

/**
 * Object returned by {@link de.danoeh.antennapod.net.sync.gpoddernet.GpodnetService} in uploadChanges method.
 */
class GpodnetUploadChangesResponse : UploadChangesResponse {
    /**
     * URLs that should be updated. The key of the map is the original URL, the value of the map
     * is the sanitized URL.
     */
    @JvmField
    val updatedUrls: Map<String, String>

    constructor(timestamp: Long, updatedUrls: Map<String, String>) : super(timestamp) {
        this.updatedUrls = updatedUrls
    }

    override fun toString(): String {
        return "GpodnetUploadChangesResponse{" +
                "timestamp=" + timestamp +
                ", updatedUrls=" + updatedUrls +
                '}'
    }

    companion object {
        /**
         * Creates a new GpodnetUploadChangesResponse-object from a JSON object that was
         * returned by an uploadChanges call.
         *
         * @throws org.json.JSONException If the method could not parse the JSONObject.
         */
        @JvmStatic
        fun fromJSONObject(objectString: String): GpodnetUploadChangesResponse {
            val obj = JSONObject(objectString)
            val timestamp = obj.getLong("timestamp")
            val updatedUrls = ArrayMap<String, String>()
            val urls = obj.getJSONArray("update_urls")
            for (i in 0 until urls.length()) {
                val urlPair = urls.getJSONArray(i)
                updatedUrls.put(urlPair.getString(0), urlPair.getString(1))
            }
            return GpodnetUploadChangesResponse(timestamp, updatedUrls)
        }
    }
}
