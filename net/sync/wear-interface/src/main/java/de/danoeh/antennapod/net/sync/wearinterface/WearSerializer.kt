package de.danoeh.antennapod.net.sync.wearinterface

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Date

import de.danoeh.antennapod.model.feed.FeedMedia
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class WearSerializer private constructor() {
    companion object {
        private const val KEY_EPISODE_ID = "episode_id"
        private const val KEY_FEED_ID = "feed_id"
        private const val KEY_TITLE = "title"
        private const val KEY_PUB_DATE = "pub_date"
        private const val KEY_DURATION = "duration"
        private const val KEY_POSITION = "position"
        private const val KEY_IS_PLAYING = "is_playing"

        private fun episodeToJson(item: FeedItem): JSONObject {
            val obj = JSONObject()
            obj.put(KEY_EPISODE_ID, item.getId())
            obj.put(KEY_TITLE, item.getTitle() ?: "")
            obj.put(KEY_PUB_DATE, if (item.getPubDate() != null) item.getPubDate()!!.time else 0)
            obj.put(KEY_DURATION, if (item.getMedia() != null) item.getMedia()!!.getDuration() else 0)
            obj.put(KEY_POSITION, if (item.getMedia() != null) item.getMedia()!!.getPosition() else 0)
            return obj
        }

        private fun episodeFromJson(obj: JSONObject): FeedItem {
            val item = FeedItem()
            item.setId(obj.optLong(KEY_EPISODE_ID, -1))
            item.setTitle(obj.optString(KEY_TITLE, ""))
            val pubDate = obj.optLong(KEY_PUB_DATE, 0)
            if (pubDate != 0L) {
                item.setPubDate(Date(pubDate))
            }
            val duration = obj.optInt(KEY_DURATION, 0)
            val position = obj.optInt(KEY_POSITION, 0)
            val media = FeedMedia(0L, item, duration, position, 0L, null, null, null, 0L, null, 0, 0L)
            item.setMedia(media)
            return item
        }

        @JvmStatic
        fun episodesToBytes(items: List<FeedItem>): ByteArray {
            val array = JSONArray()
            for (item in items) {
                try {
                    array.put(episodeToJson(item))
                } catch (e: JSONException) {
                    // skip malformed item
                }
            }
            return array.toString().toByteArray(StandardCharsets.UTF_8)
        }

        @JvmStatic
        fun episodesFromBytes(data: ByteArray): List<FeedItem> {
            val items = ArrayList<FeedItem>()
            try {
                val array = JSONArray(String(data, StandardCharsets.UTF_8))
                for (i in 0 until array.length()) {
                    items.add(episodeFromJson(array.getJSONObject(i)))
                }
            } catch (e: JSONException) {
                // return whatever we managed to parse
            }
            return items
        }

        @JvmStatic
        fun feedsToBytes(feeds: List<Feed>): ByteArray {
            val array = JSONArray()
            for (feed in feeds) {
                try {
                    val obj = JSONObject()
                    obj.put(KEY_FEED_ID, feed.getId())
                    obj.put(KEY_TITLE, feed.getTitle() ?: "")
                    array.put(obj)
                } catch (e: JSONException) {
                    // skip malformed feed
                }
            }
            return array.toString().toByteArray(StandardCharsets.UTF_8)
        }

        @JvmStatic
        fun feedsFromBytes(data: ByteArray): List<Feed> {
            val feeds = ArrayList<Feed>()
            try {
                val array = JSONArray(String(data, StandardCharsets.UTF_8))
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val feed = Feed(null, null)
                    feed.setId(obj.optLong(KEY_FEED_ID, -1))
                    feed.setTitle(obj.optString(KEY_TITLE, ""))
                    feeds.add(feed)
                }
            } catch (e: JSONException) {
                // return whatever we managed to parse
            }
            return feeds
        }

        @JvmStatic
        fun nowPlayingToBytes(item: FeedItem, isPlaying: Boolean): ByteArray {
            try {
                val obj = episodeToJson(item)
                obj.put(KEY_IS_PLAYING, isPlaying)
                return obj.toString().toByteArray(StandardCharsets.UTF_8)
            } catch (e: JSONException) {
                return ByteArray(0)
            }
        }

        @JvmStatic
        fun nowPlayingFromBytes(data: ByteArray): WearNowPlaying? {
            if (data.isEmpty()) {
                return null
            }
            try {
                val obj = JSONObject(String(data, StandardCharsets.UTF_8))
                val item = episodeFromJson(obj)
                val isPlaying = obj.optBoolean(KEY_IS_PLAYING, false)
                return WearNowPlaying(item, isPlaying)
            } catch (e: JSONException) {
                return null
            }
        }
    }
}
