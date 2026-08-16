package de.danoeh.antennapod.parser.feed.namespace

import android.text.TextUtils
import android.util.Log

import androidx.core.text.HtmlCompat

import de.danoeh.antennapod.parser.feed.HandlerState
import de.danoeh.antennapod.parser.feed.element.SyndElement
import de.danoeh.antennapod.parser.feed.util.DurationParser
import org.xml.sax.Attributes

class Itunes : Namespace() {

    override fun handleElementStart(localName: String, state: HandlerState,
                                    attributes: Attributes): SyndElement {
        if (IMAGE == localName) {
            val url = attributes.getValue(IMAGE_HREF)

            if (state.getCurrentItem() != null) {
                state.getCurrentItem()!!.setImageUrl(url)
            } else {
                // this is the feed image
                // prefer to all other images
                if (!TextUtils.isEmpty(url)) {
                    state.getFeed().setImageUrl(url)
                }
            }
        }
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
        if (state.getContentBuf() == null) {
            return
        }

        val content = state.getContentBuf().toString()
        if (TextUtils.isEmpty(content)) {
            return
        }

        if (AUTHOR == localName && state.getFeed() != null && state.getTagstack().size <= 3) {
            val contentFromHtml = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
            state.getFeed().setAuthor(contentFromHtml)
        } else if (DURATION == localName) {
            try {
                val durationMs = DurationParser.inMillis(content)
                state.getTempObjects().put(DURATION, durationMs.toInt())
            } catch (e: NumberFormatException) {
                Log.e(NSTAG, java.lang.String.format("Duration '%s' could not be parsed", content))
            }
        } else if (SUBTITLE == localName) {
            if (state.getCurrentItem() != null && TextUtils.isEmpty(state.getCurrentItem()!!.getDescription())) {
                state.getCurrentItem()!!.setDescriptionIfLonger(content)
            } else if (state.getFeed() != null && TextUtils.isEmpty(state.getFeed().getDescription())) {
                state.getFeed().setDescription(content)
            }
        } else if (SUMMARY == localName) {
            if (state.getCurrentItem() != null) {
                state.getCurrentItem()!!.setDescriptionIfLonger(content)
            } else if (Rss20.CHANNEL == state.getSecondTag().getName() && state.getFeed() != null) {
                state.getFeed().setDescription(content)
            }
        } else if (NEW_FEED_URL == localName && content.trim().startsWith("http")) {
            state.redirectUrl = content.trim()
        }
    }

    companion object {

        const val NSTAG = "itunes"
        const val NSURI = "http://www.itunes.com/dtds/podcast-1.0.dtd"

        private const val IMAGE = "image"
        private const val IMAGE_HREF = "href"

        private const val AUTHOR = "author"
        const val DURATION = "duration"
        private const val SUBTITLE = "subtitle"
        private const val SUMMARY = "summary"
        private const val NEW_FEED_URL = "new-feed-url"
    }
}
