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
import java.util.regex.Pattern

class ItunesPodcastSearcher() : PodcastSearcher {

    override fun search(query: String?): Single<List<PodcastSearchResult>> {
        return Single.create<List<PodcastSearchResult>> { subscriber ->
            val encodedQuery = try {
                URLEncoder.encode(query, "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                // this won't ever be thrown
                query
            }

            val formattedUrl = java.lang.String.format(ITUNES_API_URL, encodedQuery)

            val client = AntennapodHttpClient.getHttpClient()
            val httpReq = Request.Builder()
                    .url(formattedUrl)
            val podcasts = ArrayList<PodcastSearchResult>()
            try {
                val response = client.newCall(httpReq.build()).execute()

                if (response.isSuccessful) {
                    val resultString = response.body!!.string()
                    val result = JSONObject(resultString)
                    val j = result.getJSONArray("results")

                    for (i in 0 until j.length()) {
                        val podcastJson = j.getJSONObject(i)
                        if (!podcastJson.has("feedUrl")) {
                            continue
                        }
                        val title = podcastJson.optString("collectionName", "Unknown")
                        val imageUrl = podcastJson.optString("artworkUrl100", "")
                        val feedUrl = podcastJson.optString("feedUrl", "")
                        val author = podcastJson.optString("artistName", "Unknown")
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
        val pattern = Pattern.compile(PATTERN_BY_ID)
        val matcher = pattern.matcher(url)
        val lookupUrl = if (matcher.find()) ("https://itunes.apple.com/lookup?id=" + matcher.group(1)) else url
        return Single.create { emitter ->
            val client = AntennapodHttpClient.getHttpClient()
            val httpReq = Request.Builder().url(lookupUrl)
            try {
                val response = client.newCall(httpReq.build()).execute()
                if (response.isSuccessful) {
                    val resultString = response.body!!.string()
                    val result = JSONObject(resultString)
                    val results = result.getJSONArray("results").getJSONObject(0)
                    val feedUrlName = "feedUrl"
                    if (!results.has(feedUrlName)) {
                        val artistName = results.getString("artistName")
                        val trackName = results.getString("trackName")
                        emitter.onError(FeedUrlNotFoundException(artistName, trackName))
                        return@create
                    }
                    val feedUrl = results.getString(feedUrlName)
                    emitter.onSuccess(feedUrl)
                } else {
                    emitter.onError(IOException(response.toString()))
                }
            } catch (e: IOException) {
                emitter.onError(e)
            } catch (e: JSONException) {
                emitter.onError(e)
            }
        }
    }

    override fun urlNeedsLookup(url: String): Boolean {
        return url.contains("itunes.apple.com") || url.matches(Regex(PATTERN_BY_ID))
    }

    override fun getName(): String {
        return "Apple"
    }

    companion object {
        private const val ITUNES_API_URL = "https://itunes.apple.com/search?media=podcast&term=%s"
        private const val PATTERN_BY_ID = ".*/podcasts\\.apple\\.com/.*/podcast/.*/id(\\d+).*"
    }
}
