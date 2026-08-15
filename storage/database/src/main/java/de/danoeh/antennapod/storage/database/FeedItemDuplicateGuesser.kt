package de.danoeh.antennapod.storage.database

import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia

import java.text.DateFormat
import java.util.Locale
import org.apache.commons.lang3.StringUtils

/**
 * Publishers sometimes mess up their feed by adding episodes twice or by changing the ID of existing episodes.
 * This class tries to guess if publishers actually meant another episode,
 * even if their feed explicitly says that the episodes are different.
 */
class FeedItemDuplicateGuesser {
    companion object {
        @JvmStatic
        fun seemDuplicates(item1: FeedItem, item2: FeedItem): Boolean {
            if (sameAndNotEmpty(item1.getItemIdentifier(), item2.getItemIdentifier())) {
                return true
            }
            val media1 = item1.getMedia()
            val media2 = item2.getMedia()
            if (media1 == null || media2 == null) {
                return false
            }
            if (sameAndNotEmpty(media1.getStreamUrl(), media2.getStreamUrl())) {
                return true
            }
            return titlesLookSimilar(item1, item2)
                    && datesLookSimilar(item1, item2)
                    && durationsLookSimilar(media1, media2)
                    && mimeTypeLooksSimilar(media1, media2)
        }

        @JvmStatic
        fun sameAndNotEmpty(string1: String?, string2: String?): Boolean {
            if (StringUtils.isEmpty(string1) || StringUtils.isEmpty(string2)) {
                return false
            }
            return string1 == string2
        }

        private fun datesLookSimilar(item1: FeedItem, item2: FeedItem): Boolean {
            if (item1.getPubDate() == null || item2.getPubDate() == null) {
                return false
            }
            val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.US) // MM/DD/YY
            val dateOriginal = dateFormat.format(item2.getPubDate())
            val dateNew = dateFormat.format(item1.getPubDate())
            return StringUtils.equals(dateOriginal, dateNew) // Same date; time is ignored.
        }

        private fun durationsLookSimilar(media1: FeedMedia, media2: FeedMedia): Boolean {
            return Math.abs(media1.getDuration() - media2.getDuration()) < 10 * 60L * 1000L
        }

        private fun mimeTypeLooksSimilar(media1: FeedMedia, media2: FeedMedia): Boolean {
            var mimeType1 = media1.getMimeType()
            var mimeType2 = media2.getMimeType()
            if (mimeType1 == null || mimeType2 == null) {
                return true
            }
            if (mimeType1.contains("/") && mimeType2.contains("/")) {
                mimeType1 = mimeType1.substring(0, mimeType1.indexOf("/"))
                mimeType2 = mimeType2.substring(0, mimeType2.indexOf("/"))
            }
            return StringUtils.equals(mimeType1, mimeType2)
        }

        private fun titlesLookSimilar(item1: FeedItem, item2: FeedItem): Boolean {
            return sameAndNotEmpty(canonicalizeTitle(item1.getTitle()), canonicalizeTitle(item2.getTitle()))
        }

        @JvmStatic
        fun canonicalizeTitle(title: String?): String {
            if (title == null) {
                return ""
            }
            return title
                    .trim()
                    .replace('“', '"')
                    .replace('”', '"')
                    .replace('„', '"')
                    .replace('—', '-')
        }
    }
}
