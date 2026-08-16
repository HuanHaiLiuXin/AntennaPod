package de.danoeh.antennapod.parser.feed.namespace

import android.text.TextUtils
import de.danoeh.antennapod.parser.feed.HandlerState
import de.danoeh.antennapod.parser.feed.element.SyndElement
import org.xml.sax.Attributes
import de.danoeh.antennapod.model.feed.FeedFunding

class PodcastIndex : Namespace() {

    override fun handleElementStart(localName: String, state: HandlerState,
                                    attributes: Attributes): SyndElement {
        if (FUNDING == localName) {
            val href = attributes.getValue(URL)
            val funding = FeedFunding(href, "")
            state.setCurrentFunding(funding)
            state.getFeed().addPayment(state.getCurrentFunding()!!)
        } else if (CHAPTERS == localName) {
            val href = attributes.getValue(URL)
            if (!TextUtils.isEmpty(href)) {
                state.getCurrentItem()!!.setPodcastIndexChapterUrl(href)
            }
        } else if (SOCIAL_INTERACT == localName) {
            val href = attributes.getValue(URI)
            if (!TextUtils.isEmpty(href) && state.getCurrentItem() != null) {
                state.getCurrentItem()!!.setSocialInteractUrl(href)
            }
        } else if (TRANSCRIPT == localName) {
            val href = attributes.getValue(URL)
            val type = attributes.getValue(TYPE)
            if (!TextUtils.isEmpty(href) && !TextUtils.isEmpty(type)) {
                state.getCurrentItem()!!.setTranscriptUrl(type, href)
            }
        }
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
        if (state.getContentBuf() == null) {
            return
        }
        val content = state.getContentBuf().toString()
        if (FUNDING == localName && state.getCurrentFunding() != null && !TextUtils.isEmpty(content)) {
            state.getCurrentFunding()!!.setContent(content)
        }
    }

    companion object {

        const val NSTAG = "podcast"
        const val NSURI = "https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/1.0.md"
        const val NSURI2 = "https://podcastindex.org/namespace/1.0"
        private const val URL = "url"
        private const val URI = "uri"
        private const val FUNDING = "funding"
        private const val CHAPTERS = "chapters"
        private const val SOCIAL_INTERACT = "socialInteract"
        private const val TRANSCRIPT = "transcript"
        private const val TYPE = "type"
    }
}
