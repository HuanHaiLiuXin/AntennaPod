package de.danoeh.antennapod.storage.importexport

import android.content.Context
import android.util.Log

import org.apache.commons.io.IOUtils

import java.io.IOException
import java.io.InputStream
import java.io.Writer
import java.util.ArrayList
import java.util.TreeMap

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem

/** Writes saved favorites to file. */
class FavoritesWriter {
    companion object {
        private const val TAG = "FavoritesWriter"
        private const val FAVORITE_TEMPLATE = "html-export-favorites-item-template.html"
        private const val FEED_TEMPLATE = "html-export-feed-template.html"
        private const val UTF_8 = "UTF-8"

        @JvmStatic
        @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
        fun writeDocument(allFavorites: List<FeedItem>, writer: Writer, context: Context) {
            Log.d(TAG, "Starting to write document")

            val templateStream = context.getAssets().open("html-export-template.html")
            var template = IOUtils.toString(templateStream, UTF_8)
            template = template.replace(Regex("\\{TITLE\\}"), "Favorites")
            val templateParts = template.split("\\{FEEDS\\}")

            val favTemplateStream = context.getAssets().open(FAVORITE_TEMPLATE)
            val favTemplate = IOUtils.toString(favTemplateStream, UTF_8)

            val feedTemplateStream = context.getAssets().open(FEED_TEMPLATE)
            val feedTemplate = IOUtils.toString(feedTemplateStream, UTF_8)

            val favoriteByFeed = getFeedMap(allFavorites)

            writer.append(templateParts[0])

            for (feedId in favoriteByFeed.entries) {
                val favorites = feedId.value
                writer.append("<li><div>\n")
                writeFeed(writer, favorites[0].getFeed()!!, feedTemplate)

                writer.append("<ul>\n")
                for (item in favorites) {
                    writeFavoriteItem(writer, item, favTemplate)
                }
                writer.append("</ul></div></li>\n")
            }

            writer.append(templateParts[1])

            Log.d(TAG, "Finished writing document")
        }

        /**
         * Group favorite episodes by feed, sorting them by publishing date in descending order.
         *
         * @param favoritesList `List` of all favorite episodes.
         * @return A `Map` favorite episodes, keyed by feed ID.
         */
        private fun getFeedMap(favoritesList: List<FeedItem>): TreeMap<Long, MutableList<FeedItem>> {
            val feedMap = TreeMap<Long, MutableList<FeedItem>>()

            for (item in favoritesList) {
                var feedEpisodes = feedMap[item.getFeedId()]

                if (feedEpisodes == null) {
                    feedEpisodes = ArrayList()
                    feedMap.put(item.getFeedId(), feedEpisodes)
                }

                feedEpisodes.add(item)
            }

            return feedMap
        }

        @Throws(IOException::class)
        private fun writeFeed(writer: Writer, feed: Feed, feedTemplate: String) {
            val feedInfo = feedTemplate
                    .replace("{FEED_IMG}", feed.getImageUrl()!!)
                    .replace("{FEED_TITLE}", feed.getTitle()!!)
                    .replace("{FEED_LINK}", feed.getLink()!!)
                    .replace("{FEED_WEBSITE}", feed.getDownloadUrl()!!)

            writer.append(feedInfo)
        }

        @Throws(IOException::class)
        private fun writeFavoriteItem(writer: Writer, item: FeedItem, favoriteTemplate: String) {
            var favItem = favoriteTemplate.replace("{FAV_TITLE}", item.getTitle()!!.trim())
            if (item.getLink() != null) {
                favItem = favItem.replace("{FAV_WEBSITE}", item.getLink()!!)
            } else {
                favItem = favItem.replace("{FAV_WEBSITE}", "")
            }
            if (item.getMedia() != null && item.getMedia()!!.getDownloadUrl() != null) {
                favItem = favItem.replace("{FAV_MEDIA}", item.getMedia()!!.getDownloadUrl()!!)
            } else {
                favItem = favItem.replace("{FAV_MEDIA}", "")
            }

            writer.append(favItem)
        }
    }
}
