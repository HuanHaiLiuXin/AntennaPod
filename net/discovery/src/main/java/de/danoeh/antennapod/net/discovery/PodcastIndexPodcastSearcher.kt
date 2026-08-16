package de.danoeh.antennapod.net.discovery

import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.net.common.UserAgentInterceptor
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request

class PodcastIndexPodcastSearcher() : PodcastSearcher {

    override fun search(query: String?): Single<List<PodcastSearchResult>> {
        return Single.create<List<PodcastSearchResult>> { subscriber ->
            val encodedQuery = try {
                URLEncoder.encode(query, "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                // this won't ever be thrown
                query
            }
            val formattedUrl = java.lang.String.format(SEARCH_API_URL, encodedQuery)
            val podcasts = ArrayList<PodcastSearchResult>()
            try {
                val client = AntennapodHttpClient.getHttpClient()
                val response = client.newCall(buildAuthenticatedRequest(formattedUrl)).execute()

                if (response.isSuccessful) {
                    val resultString = response.body!!.string()
                    val result = JSONObject(resultString)
                    val j = result.getJSONArray("feeds")

                    for (i in 0 until j.length()) {
                        val podcastJson = j.getJSONObject(i)
                        if (!podcastJson.has("url")) {
                            continue
                        }
                        val title = podcastJson.optString("title", "Unknown")
                        val imageUrl = podcastJson.optString("image", "")
                        val feedUrl = podcastJson.optString("url", "")
                        val author = podcastJson.optString("author", "Unknown")
                        podcasts.add(PodcastSearchResult(title, imageUrl, feedUrl, author))
                    }
                } else {
                    subscriber.onError(IOException(response.toString()))
                }
            } catch (e: IOException) {
                subscriber.onError(e)
            } catch (e: JSONException) {
                subscriber.onError(e)
            }
            subscriber.onSuccess(podcasts)
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    override fun lookupUrl(url: String): Single<String> {
        return Single.just(url)
    }

    override fun urlNeedsLookup(url: String): Boolean {
        return false
    }

    override fun getName(): String {
        return "Podcast Index"
    }

    private fun buildAuthenticatedRequest(url: String): Request {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        val now = Date()
        calendar.time = now
        val secondsSinceEpoch = calendar.timeInMillis / 1000L
        val apiHeaderTime = java.lang.String.valueOf(secondsSinceEpoch)
        val data4Hash = BuildConfig.PODCASTINDEX_API_KEY + BuildConfig.PODCASTINDEX_API_SECRET + apiHeaderTime
        val hashString = sha1(data4Hash)

        val httpReq = Request.Builder()
                .addHeader("X-Auth-Date", apiHeaderTime)
                .addHeader("X-Auth-Key", BuildConfig.PODCASTINDEX_API_KEY)
                .addHeader("Authorization", hashString!!)
                .addHeader("User-Agent", UserAgentInterceptor.USER_AGENT)
                .url(url)
        return httpReq.build()
    }

    companion object {
        private const val SEARCH_API_URL = "https://api.podcastindex.org/api/1.0/search/byterm?q=%s"

        private fun sha1(clearString: String): String? {
            try {
                val messageDigest = MessageDigest.getInstance("SHA-1")
                messageDigest.update(clearString.toByteArray(Charset.forName("UTF-8")))
                return toHex(messageDigest.digest())
            } catch (ignored: Exception) {
                ignored.printStackTrace()
                return null
            }
        }

        private fun toHex(bytes: ByteArray): String {
            val buffer = StringBuilder()
            for (b in bytes) {
                buffer.append(java.lang.String.format(Locale.getDefault(), "%02x", b))
            }
            return buffer.toString()
        }
    }
}
