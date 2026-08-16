package de.danoeh.antennapod.net.discovery

import de.danoeh.antennapod.net.common.AntennapodHttpClient
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.ArrayList

class FyydPodcastSearcher : PodcastSearcher {
    override fun search(query: String?): Single<List<PodcastSearchResult>> {
        return Single.create<List<PodcastSearchResult>> { subscriber ->
            val encodedQuery = try {
                URLEncoder.encode(query, "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                query
            }
            val formattedUrl = java.lang.String.format(FYYD_API_URL, encodedQuery)

            val client = AntennapodHttpClient.getHttpClient()
            val httpReq = Request.Builder().url(formattedUrl)
            val searchResults = ArrayList<PodcastSearchResult>()
            client.newCall(httpReq.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    subscriber.onError(IOException(response.toString()))
                    return@use
                }
                if (response.body == null) {
                    subscriber.onError(IOException("Null response"))
                    return@use
                }
                val resultString = response.body!!.string()
                val result = JSONObject(resultString)
                val data = result.optJSONArray("data")
                if (data == null) {
                    subscriber.onError(IOException("Null response"))
                    return@use
                }

                for (i in 0 until data.length()) {
                    val podcastJson = data.getJSONObject(i)
                    val title = podcastJson.optString("title", "Unknown")
                    val imageUrl = podcastJson.optString("thumbImageURL", "")
                    val feedUrl = podcastJson.optString("xmlURL", "")
                    val author = podcastJson.optString("author", "Unknown")
                    searchResults.add(PodcastSearchResult(title, imageUrl, feedUrl, author))
                }
            }
            subscriber.onSuccess(searchResults)
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
    }

    override fun lookupUrl(url: String): Single<String> {
        return Single.just(url)
    }

    override fun urlNeedsLookup(url: String): Boolean {
        return false
    }

    override fun getName(): String {
        return "fyyd"
    }

    companion object {
        private const val FYYD_API_URL = "https://api.fyyd.de/0.2/search/podcast?title=%s&count=10"
    }
}
