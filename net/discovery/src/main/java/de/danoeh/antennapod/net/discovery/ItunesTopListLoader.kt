package de.danoeh.antennapod.net.discovery

import android.content.Context
import android.util.Log
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.io.IOException
import java.util.ArrayList
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.TimeUnit

class ItunesTopListLoader(private val context: Context) {

    fun loadToplist(country: String?, limit: Int, subscribed: List<Feed>): List<PodcastSearchResult> {
        val client = AntennapodHttpClient.getHttpClient()
        var loadCountry = country
        if (COUNTRY_CODE_UNSET == country) {
            loadCountry = Locale.getDefault().country
        }
        val feedString = try {
            getTopListFeed(client, loadCountry!!)
        } catch (e: IOException) {
            if (COUNTRY_CODE_UNSET == country) {
                getTopListFeed(client, "US")
            } else {
                throw e
            }
        }
        return removeSubscribed(parseFeed(feedString), subscribed, limit)
    }

    private fun getTopListFeed(client: OkHttpClient, country: String): String {
        val url = "https://itunes.apple.com/%s/rss/toppodcasts/limit=" + NUM_LOADED + "/explicit=true/json"
        Log.d(TAG, "Feed URL " + java.lang.String.format(url, country))
        val httpReq = Request.Builder()
                .cacheControl(CacheControl.Builder().maxStale(1, TimeUnit.DAYS).build())
                .url(java.lang.String.format(url, country))

        client.newCall(httpReq.build()).execute().use { response ->
            if (response.isSuccessful) {
                return response.body!!.string()
            }
            if (response.code == 400) {
                throw IOException("iTunes does not have data for the selected country.")
            }
            val prefix = context.getString(R.string.error_msg_prefix)
            throw IOException(prefix + response)
        }
    }

    private fun parseFeed(jsonString: String): List<PodcastSearchResult> {
        val result = JSONObject(jsonString)
        val feed: JSONObject
        val entries: JSONArray
        try {
            feed = result.getJSONObject("feed")
            entries = feed.getJSONArray("entry")
        } catch (e: JSONException) {
            return ArrayList()
        }

        val results = ArrayList<PodcastSearchResult>()
        for (i in 0 until entries.length()) {
            val json = entries.getJSONObject(i)
            results.add(toSearchResult(json))
        }

        return results
    }

    companion object {
        private const val TAG = "ITunesTopListLoader"
        const val PREF_KEY_COUNTRY_CODE = "country_code"
        const val PREF_KEY_HIDDEN_DISCOVERY_COUNTRY = "hidden_discovery_country"
        const val PREF_KEY_NEEDS_CONFIRM = "needs_confirm"
        const val PREFS = "CountryRegionPrefs"
        const val COUNTRY_CODE_UNSET = "99"
        private const val NUM_LOADED = 25

        private fun removeSubscribed(
                suggestedPodcasts: List<PodcastSearchResult>, subscribedFeeds: List<Feed>,
                limit: Int): List<PodcastSearchResult> {
            val subscribedPodcastsSet = HashSet<String>()
            for (subscribedFeed in subscribedFeeds) {
                if (subscribedFeed.getTitle() != null && subscribedFeed.getAuthor() != null
                        && subscribedFeed.getState() != Feed.STATE_NOT_SUBSCRIBED) {
                    subscribedPodcastsSet.add(subscribedFeed.getTitle()!!.trim())
                }
            }
            val suggestedNotSubscribed = ArrayList<PodcastSearchResult>()
            for (suggested in suggestedPodcasts) {
                if (!subscribedPodcastsSet.contains(suggested.title.trim())) {
                    suggestedNotSubscribed.add(suggested)
                }
                if (suggestedNotSubscribed.size == limit) {
                    return suggestedNotSubscribed
                }
            }
            return suggestedNotSubscribed
        }

        private fun toSearchResult(json: JSONObject): PodcastSearchResult {
            val title = json.getJSONObject("im:name").getString("label")
            var imageUrl: String? = null
            val images = json.getJSONArray("im:image")
            var i = 0
            while (imageUrl == null && i < images.length()) {
                val image = images.getJSONObject(i)
                val height = image.getJSONObject("attributes").getString("height")
                if (Integer.parseInt(height) >= 100) {
                    imageUrl = image.getString("label")
                }
                i++
            }
            val feedUrl = "https://itunes.apple.com/lookup?id=" +
                    json.getJSONObject("id").getJSONObject("attributes").getString("im:id")

            var author: String? = null
            try {
                author = json.getJSONObject("im:artist").getString("label")
            } catch (e: Exception) {
                // Some feeds have empty artist
            }
            return PodcastSearchResult(title, imageUrl, feedUrl, author)
        }
    }
}
