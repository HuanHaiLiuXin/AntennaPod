package de.danoeh.antennapod.net.sync.nextcloud

import de.danoeh.antennapod.net.sync.HostnameParser
import de.danoeh.antennapod.net.sync.gpoddernet.mapper.ResponseMapper
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetUploadChangesResponse
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeActionChanges
import de.danoeh.antennapod.net.sync.serviceinterface.ISyncService
import de.danoeh.antennapod.net.sync.serviceinterface.SubscriptionChanges
import de.danoeh.antennapod.net.sync.serviceinterface.SyncServiceException
import de.danoeh.antennapod.net.sync.serviceinterface.UploadChangesResponse
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.io.IOException
import java.net.MalformedURLException
import java.util.HashMap

class NextcloudSyncService(
    private val httpClient: OkHttpClient,
    baseHosturl: String?,
    private val username: String,
    private val password: String
) : ISyncService {
    private val hostname = HostnameParser(baseHosturl)

    override fun login() {
    }

    override fun getSubscriptionChanges(lastSync: Long): SubscriptionChanges {
        try {
            val url = makeUrl("/index.php/apps/gpoddersync/subscriptions")
            url.addQueryParameter("since", "" + lastSync)
            val responseString = performRequest(url, "GET", null)
            val json = JSONObject(responseString)
            return ResponseMapper.readSubscriptionChangesFromJsonObject(json)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: Exception) {
            e.printStackTrace()
            throw SyncServiceException(e)
        }
    }

    override fun uploadSubscriptionChanges(addedFeeds: List<String>,
                                           removedFeeds: List<String>): UploadChangesResponse {
        try {
            val url = makeUrl("/index.php/apps/gpoddersync/subscription_change/create")
            val requestObject = JSONObject()
            requestObject.put("add", JSONArray(addedFeeds))
            requestObject.put("remove", JSONArray(removedFeeds))
            val requestBody = requestObject.toString().toRequestBody("application/json".toMediaType())
            performRequest(url, "POST", requestBody)
        } catch (e: Exception) {
            e.printStackTrace()
            throw NextcloudSynchronizationServiceException(e)
        }

        return GpodnetUploadChangesResponse(System.currentTimeMillis() / 1000, HashMap())
    }

    override fun getEpisodeActionChanges(timestamp: Long): EpisodeActionChanges {
        try {
            val uri = makeUrl("/index.php/apps/gpoddersync/episode_action")
            uri.addQueryParameter("since", "" + timestamp)
            val responseString = performRequest(uri, "GET", null)
            val json = JSONObject(responseString)
            return ResponseMapper.readEpisodeActionsFromJsonObject(json)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: Exception) {
            e.printStackTrace()
            throw SyncServiceException(e)
        }
    }

    override fun uploadEpisodeActions(queuedEpisodeActions: List<EpisodeAction>): UploadChangesResponse {
        var i = 0
        while (i < queuedEpisodeActions.size) {
            uploadEpisodeActionsPartial(queuedEpisodeActions,
                    i, Math.min(queuedEpisodeActions.size, i + UPLOAD_BULK_SIZE))
            i += UPLOAD_BULK_SIZE
        }
        return NextcloudGpodderEpisodeActionPostResponse(System.currentTimeMillis() / 1000)
    }

    private fun uploadEpisodeActionsPartial(queuedEpisodeActions: List<EpisodeAction>, from: Int, to: Int) {
        try {
            val list = JSONArray()
            for (i in from until to) {
                val episodeAction = queuedEpisodeActions[i]
                val obj = episodeAction.writeToJsonObject()
                if (obj != null) {
                    list.put(obj)
                }
            }
            val url = makeUrl("/index.php/apps/gpoddersync/episode_action/create")
            val requestBody = list.toString().toRequestBody("application/json".toMediaType())
            performRequest(url, "POST", requestBody)
        } catch (e: Exception) {
            e.printStackTrace()
            throw NextcloudSynchronizationServiceException(e)
        }
    }

    private fun performRequest(url: HttpUrl.Builder, method: String, body: RequestBody?): String {
        val request = Request.Builder()
                .url(url.build())
                .header("Authorization", Credentials.basic(username, password))
                .header("Accept", "application/json")
                .method(method, body)
                .build()
        val response = httpClient.newCall(request).execute()
        if (response.code != 200) {
            throw IOException("Response code: " + response.code)
        }
        return response.body!!.string()
    }

    private fun makeUrl(path: String): HttpUrl.Builder {
        return HttpUrl.Builder()
                .scheme(hostname.scheme!!)
                .host(hostname.host!!)
                .port(hostname.port)
                .addPathSegments(StringUtils.stripStart(hostname.subfolder + path, "/"))
    }

    override fun logout() {
    }

    private class NextcloudGpodderEpisodeActionPostResponse(epochSecond: Long) : UploadChangesResponse(epochSecond)

    companion object {
        private const val UPLOAD_BULK_SIZE = 30
    }
}
