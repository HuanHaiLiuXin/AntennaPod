package de.danoeh.antennapod.net.sync.gpoddernet

import android.util.Log

import de.danoeh.antennapod.net.sync.HostnameParser
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeActionChanges
import de.danoeh.antennapod.net.sync.serviceinterface.ISyncService
import de.danoeh.antennapod.net.sync.serviceinterface.SubscriptionChanges
import de.danoeh.antennapod.net.sync.serviceinterface.SyncServiceException
import de.danoeh.antennapod.net.sync.serviceinterface.UploadChangesResponse
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.charset.Charset
import java.util.ArrayList
import java.util.Locale

import de.danoeh.antennapod.net.sync.gpoddernet.mapper.ResponseMapper
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetDevice
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetEpisodeActionPostResponse
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetUploadChangesResponse
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody

/**
 * Communicates with the gpodder.net service.
 */
class GpodnetService(
    private val httpClient: OkHttpClient,
    baseHostUrl: String?,
    private val deviceId: String,
    private var username: String?,
    private var password: String?
) : ISyncService {
    private var baseScheme: String? = null
    private var basePort: Int = 0
    private val baseHost: String?
    private val baseFolder: String
    private var loggedIn = false

    init {
        val hostname = HostnameParser(baseHostUrl ?: DEFAULT_BASE_HOST)
        this.baseHost = hostname.host
        this.basePort = hostname.port
        this.baseScheme = hostname.scheme
        this.baseFolder = hostname.subfolder
    }

    private fun requireLoggedIn() {
        if (!loggedIn) {
            throw IllegalStateException("Not logged in")
        }
    }

    /**
     * Returns all devices of a given user.
     * <p/>
     * This method requires authentication.
     *
     * @throws GpodnetServiceAuthenticationException If there is an authentication error.
     */
    fun getDevices(): List<GpodnetDevice> {
        requireLoggedIn()
        try {
            val url = URI(baseScheme, null, baseHost, basePort,
                    java.lang.String.format("%s/api/2/devices/%s.json", baseFolder, username), null, null).toURL()
            val request = Request.Builder().url(url)
            val response = executeRequest(request)
            val devicesArray = JSONArray(response)
            return readDeviceListFromJsonArray(devicesArray)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }
    }

    /**
     * Configures the device of a given user.
     * <p/>
     * This method requires authentication.
     *
     * @param deviceId The ID of the device that should be configured.
     * @throws GpodnetServiceAuthenticationException If there is an authentication error.
     */
    fun configureDevice(deviceId: String, caption: String?, type: GpodnetDevice.DeviceType?) {
        requireLoggedIn()
        try {
            val url = URI(baseScheme, null, baseHost, basePort,
                    java.lang.String.format("%s/api/2/devices/%s/%s.json", baseFolder, username, deviceId),
                    null, null).toURL()
            val content: String
            if (caption != null || type != null) {
                val jsonContent = JSONObject()
                if (caption != null) {
                    jsonContent.put("caption", caption)
                }
                if (type != null) {
                    jsonContent.put("type", type.toString())
                }
                content = jsonContent.toString()
            } else {
                content = ""
            }
            val body = content.toRequestBody(JSON)
            val request = Request.Builder().post(body).url(url)
            executeRequest(request)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }
    }

    /**
     * Updates the subscription list of a specific device.
     * <p/>
     * This method requires authentication.
     *
     * @param added    Collection of feed URLs of added feeds. This Collection MUST NOT contain any duplicates
     * @param removed  Collection of feed URLs of removed feeds. This Collection MUST NOT contain any duplicates
     * @return a GpodnetUploadChangesResponse. See {@link GpodnetUploadChangesResponse}
     * for details.
     * @throws GpodnetServiceException            if added or removed contain duplicates or if there
     *                                            is an authentication error.
     */
    override fun uploadSubscriptionChanges(added: List<String>, removed: List<String>): UploadChangesResponse {
        requireLoggedIn()
        try {
            val url = URI(baseScheme, null, baseHost, basePort,
                    java.lang.String.format("%s/api/2/subscriptions/%s/%s.json", baseFolder, username, deviceId),
                    null, null).toURL()

            val requestObject = JSONObject()
            requestObject.put("add", JSONArray(added))
            requestObject.put("remove", JSONArray(removed))

            val body = requestObject.toString().toRequestBody(JSON)
            val request = Request.Builder().post(body).url(url)

            val response = executeRequest(request)
            return GpodnetUploadChangesResponse.fromJSONObject(response)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }

    }

    /**
     * Returns all subscription changes of a specific device.
     * <p/>
     * This method requires authentication.
     *
     * @param timestamp A timestamp that can be used to receive all changes since a
     *                  specific point in time.
     * @throws GpodnetServiceAuthenticationException If there is an authentication error.
     */
    override fun getSubscriptionChanges(timestamp: Long): SubscriptionChanges {
        requireLoggedIn()
        val params = java.lang.String.format(Locale.US, "since=%d", timestamp)
        val path = java.lang.String.format("%s/api/2/subscriptions/%s/%s.json", baseFolder, username, deviceId)
        try {
            val url = URI(baseScheme, null, baseHost, basePort, path, params, null).toURL()
            val request = Request.Builder().url(url)

            val response = executeRequest(request)
            val changes = JSONObject(response)
            return ResponseMapper.readSubscriptionChangesFromJsonObject(changes)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw IllegalStateException(e)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }

    }

    /**
     * Updates the episode actions
     * <p/>
     * This method requires authentication.
     *
     * @param episodeActions Collection of episode actions.
     * @return a GpodnetUploadChangesResponse. See {@link GpodnetUploadChangesResponse}
     * for details.
     * @throws GpodnetServiceException            if added or removed contain duplicates or if there
     *                                            is an authentication error.
     */
    override fun uploadEpisodeActions(episodeActions: List<EpisodeAction>): UploadChangesResponse? {
        requireLoggedIn()
        var response: UploadChangesResponse? = null
        var i = 0
        while (i < episodeActions.size) {
            response = uploadEpisodeActionsPartial(episodeActions,
                    i, Math.min(episodeActions.size, i + UPLOAD_BULK_SIZE))
            i += UPLOAD_BULK_SIZE
        }
        return response
    }

    private fun uploadEpisodeActionsPartial(episodeActions: List<EpisodeAction>, from: Int, to: Int)
            : UploadChangesResponse {
        try {
            Log.d(TAG, "Uploading partial actions " + from + " to " + to + " of " + episodeActions.size)
            val url = URI(baseScheme, null, baseHost, basePort,
                    java.lang.String.format("%s/api/2/episodes/%s.json", baseFolder, username), null, null).toURL()

            val list = JSONArray()
            for (i in from until to) {
                val episodeAction = episodeActions[i]
                val obj = episodeAction.writeToJsonObject()
                if (obj != null) {
                    obj.put("device", deviceId)
                    list.put(obj)
                }
            }

            val body = list.toString().toRequestBody(JSON)
            val request = Request.Builder().post(body).url(url)

            val response = executeRequest(request)
            return GpodnetEpisodeActionPostResponse.fromJSONObject(response)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        }
    }

    /**
     * Returns all subscription changes of a specific device.
     * <p/>
     * This method requires authentication.
     *
     * @param timestamp A timestamp that can be used to receive all changes since a
     *                  specific point in time.
     * @throws SyncServiceException If there is an authentication error.
     */
    override fun getEpisodeActionChanges(timestamp: Long): EpisodeActionChanges {
        requireLoggedIn()
        val params = java.lang.String.format(Locale.US, "since=%d", timestamp)
        val path = java.lang.String.format("%s/api/2/episodes/%s.json", baseFolder, username)
        try {
            val url = URI(baseScheme, null, baseHost, basePort, path, params, null).toURL()
            val request = Request.Builder().url(url)

            val response = executeRequest(request)
            val json = JSONObject(response)
            return ResponseMapper.readEpisodeActionsFromJsonObject(json)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw IllegalStateException(e)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        }

    }

    /**
     * Logs in a specific user. This method must be called if any of the methods
     * that require authentication is used.
     *
     * @throws IllegalArgumentException If username or password is null.
     */
    override fun login() {
        val url: URL
        try {
            url = URI(baseScheme, null, baseHost, basePort,
                    java.lang.String.format("%s/api/2/auth/%s/login.json", baseFolder, username),
                    null, null).toURL()
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }
        val requestBody = "".toRequestBody(TEXT)
        val request = Request.Builder().url(url).post(requestBody).build()
        try {
            val credential = Credentials.basic(username.toString(), password.toString(), Charset.forName("UTF-8"))
            val authRequest = request.newBuilder().header("Authorization", credential).build()
            val response = httpClient.newCall(authRequest).execute()
            checkStatusCode(response)
            response.body!!.close()
            this.loggedIn = true
        } catch (e: Exception) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }
    }

    private fun executeRequest(requestB: Request.Builder): String {
        val request = requestB.build()
        val responseString: String
        val response: Response
        var body: ResponseBody? = null
        try {

            response = httpClient.newCall(request).execute()
            checkStatusCode(response)
            body = response.body
            responseString = getStringFromResponseBody(body!!)
        } catch (e: IOException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } finally {
            if (body != null) {
                body.close()
            }
        }
        return responseString
    }

    private fun getStringFromResponseBody(body: ResponseBody): String {
        val outputStream: ByteArrayOutputStream
        val contentLength = body.contentLength().toInt()
        if (contentLength > 0) {
            outputStream = ByteArrayOutputStream(contentLength)
        } else {
            outputStream = ByteArrayOutputStream()
        }
        try {
            val buffer = ByteArray(8 * 1024)
            val input = body.byteStream()
            var count: Int
            while (input.read(buffer).also { count = it } > 0) {
                outputStream.write(buffer, 0, count)
            }
            return String(outputStream.toByteArray(), Charsets.UTF_8)
        } catch (e: IOException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }
    }

    private fun checkStatusCode(response: Response) {
        val responseCode = response.code
        if (responseCode != HttpURLConnection.HTTP_OK) {
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw GpodnetServiceAuthenticationException("Wrong username or password")
            } else {
                if (BuildConfig.DEBUG) {
                    try {
                        Log.d(TAG, response.body!!.string())
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                if (responseCode >= 500) {
                    throw GpodnetServiceBadStatusCodeException(this.baseHost + " is currently unavailable (code "
                            + responseCode + ")", responseCode)
                } else {
                    throw GpodnetServiceBadStatusCodeException("Unable to connect to " + this.baseHost + " (code "
                            + responseCode + ": " + response.message + ")", responseCode)
                }
            }
        }
    }

    private fun readDeviceListFromJsonArray(array: JSONArray): List<GpodnetDevice> {
        val result = ArrayList<GpodnetDevice>(array.length())
        for (i in 0 until array.length()) {
            result.add(readDeviceFromJsonObject(array.getJSONObject(i)))
        }
        return result
    }

    private fun readDeviceFromJsonObject(obj: JSONObject): GpodnetDevice {
        val id = obj.getString("id")
        val caption = obj.getString("caption")
        val type = obj.getString("type")
        val subscriptions = obj.getInt("subscriptions")
        return GpodnetDevice(id, caption, type, subscriptions)
    }

    override fun logout() {

    }

    fun setCredentials(username: String?, password: String?) {
        this.username = username
        this.password = password
    }

    companion object {
        const val TAG = "GpodnetService"
        private const val DEFAULT_BASE_HOST = "gpodder.net"
        private const val UPLOAD_BULK_SIZE = 30
        private val TEXT = "plain/text; charset=utf-8".toMediaType()
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
