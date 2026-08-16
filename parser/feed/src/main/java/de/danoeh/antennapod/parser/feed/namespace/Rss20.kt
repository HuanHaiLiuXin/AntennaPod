package de.danoeh.antennapod.parser.feed.namespace

import android.text.TextUtils
import android.util.Log

import androidx.core.text.HtmlCompat
import de.danoeh.antennapod.parser.feed.HandlerState
import de.danoeh.antennapod.parser.feed.element.SyndElement
import de.danoeh.antennapod.parser.feed.util.DateUtils
import de.danoeh.antennapod.parser.feed.util.SyndStringUtils
import org.xml.sax.Attributes

import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.parser.feed.util.MimeTypeUtils

import java.util.Locale

/**
 * SAX-Parser for reading RSS-Feeds.
 */
class Rss20 : Namespace() {

    override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
        if (ITEM == localName && CHANNEL == state.getTagstack().lastElement().getName()) {
            state.setCurrentItem(FeedItem())
            state.getItems().add(state.getCurrentItem()!!)
            state.getCurrentItem()!!.setFeed(state.getFeed())
        } else if (ENCLOSURE == localName && ITEM == state.getTagstack().peek().getName()
                && state.getCurrentItem() != null) {
            val url = attributes.getValue(ENC_URL)
            var mimeType = MimeTypeUtils.getMimeType(attributes.getValue(ENC_TYPE), url)
            var isValidMedia = MimeTypeUtils.isMediaFile(mimeType)
            if (!isValidMedia && !MimeTypeUtils.isImageFile(mimeType) && state.getCurrentItem()!!.getMedia() == null) {
                isValidMedia = true
                mimeType = "audio/*"
            }

            if (state.getCurrentItem()!!.getMedia() == null && isValidMedia && !TextUtils.isEmpty(url)) {
                var size = 0L
                try {
                    val sizeStr = attributes.getValue(ENC_LEN)
                    if (!TextUtils.isEmpty(sizeStr)) {
                        size = java.lang.Long.parseLong(sizeStr)
                    }
                    if (size < 16384) {
                        // less than 16kb is suspicious, check manually
                        size = 0
                    }
                } catch (e: NumberFormatException) {
                    Log.d(TAG, "Length attribute could not be parsed.")
                }
                val media = FeedMedia(state.getCurrentItem(), url, size, mimeType)
                state.getCurrentItem()!!.setMedia(media)
            }
        }
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
        if (ITEM == localName) {
            if (state.getCurrentItem() != null) {
                val currentItem = state.getCurrentItem()
                // the title tag is optional in RSS 2.0. The description is used
                // as a title if the item has no title-tag.
                if (currentItem!!.getTitle() == null) {
                    currentItem.setTitle(currentItem.getDescription())
                }

                if (state.getTempObjects().containsKey(Itunes.DURATION)) {
                    if (currentItem.hasMedia()) {
                        val duration = state.getTempObjects()[Itunes.DURATION] as Int
                        currentItem.getMedia()!!.setDuration(duration)
                    }
                    state.getTempObjects().remove(Itunes.DURATION)
                }
            }
            state.setCurrentItem(null)
        } else if (state.getTagstack().size >= 2 && state.getContentBuf() != null) {
            val contentRaw = state.getContentBuf().toString()
            val content = SyndStringUtils.trimAllWhitespace(contentRaw)
            val topElement = state.getTagstack().peek()
            val top = topElement.getName()
            val secondElement = state.getSecondTag()
            val second = secondElement.getName()
            var third: String? = null
            if (state.getTagstack().size >= 3) {
                third = state.getThirdTag().getName()
            }
            if (GUID == top && ITEM == second) {
                // some feed creators include an empty or non-standard guid-element in their feed,
                // which should be ignored
                if (!TextUtils.isEmpty(contentRaw) && state.getCurrentItem() != null) {
                    state.getCurrentItem()!!.setItemIdentifier(contentRaw)
                }
            } else if (TITLE == top) {
                // Calling fromHtml only if needed because it is slow for huge feeds
                val contentFromHtml = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                if (ITEM == second && state.getCurrentItem() != null) {
                    state.getCurrentItem()!!.setTitle(contentFromHtml)
                } else if (CHANNEL == second && state.getFeed() != null) {
                    state.getFeed().setTitle(contentFromHtml)
                }
            } else if (LINK == top) {
                if (CHANNEL == second && state.getFeed() != null) {
                    state.getFeed().setLink(content)
                } else if (ITEM == second && state.getCurrentItem() != null) {
                    state.getCurrentItem()!!.setLink(content)
                }
            } else if (PUBDATE == top && ITEM == second && state.getCurrentItem() != null) {
                state.getCurrentItem()!!.setPubDate(DateUtils.parseOrNullIfFuture(content))
            } else if (URL == top && IMAGE == second && CHANNEL == third) {
                // prefer itunes:image
                if (state.getFeed() != null && state.getFeed().getImageUrl() == null) {
                    state.getFeed().setImageUrl(content)
                }
            } else if (DESCR == localName) {
                if (CHANNEL == second && state.getFeed() != null) {
                    // Calling fromHtml only if needed because it is slow for huge feeds
                    val contentFromHtml = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                    state.getFeed().setDescription(contentFromHtml)
                } else if (ITEM == second && state.getCurrentItem() != null) {
                    state.getCurrentItem()!!.setDescriptionIfLonger(content) // fromHtml here breaks \n when not html
                }
            } else if (LANGUAGE == localName && state.getFeed() != null) {
                state.getFeed().setLanguage(content!!.lowercase(Locale.US))
            }
        }
    }

    companion object {

        private const val TAG = "NSRSS20"

        const val CHANNEL = "channel"
        const val ITEM = "item"
        private const val GUID = "guid"
        private const val TITLE = "title"
        private const val LINK = "link"
        private const val DESCR = "description"
        private const val PUBDATE = "pubDate"
        private const val ENCLOSURE = "enclosure"
        private const val IMAGE = "image"
        private const val URL = "url"
        private const val LANGUAGE = "language"

        private const val ENC_URL = "url"
        private const val ENC_LEN = "length"
        private const val ENC_TYPE = "type"
    }
}
