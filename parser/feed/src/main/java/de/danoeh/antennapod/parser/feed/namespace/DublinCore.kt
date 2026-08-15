package de.danoeh.antennapod.parser.feed.namespace

import de.danoeh.antennapod.parser.feed.HandlerState
import de.danoeh.antennapod.parser.feed.element.SyndElement
import de.danoeh.antennapod.parser.feed.util.DateUtils
import org.xml.sax.Attributes

import de.danoeh.antennapod.model.feed.FeedItem

class DublinCore : Namespace() {
    override fun handleElementStart(localName: String, state: HandlerState,
                                    attributes: Attributes): SyndElement {
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
        if (state.getCurrentItem() != null && state.getContentBuf() != null
                && state.getTagstack() != null && state.getTagstack().size >= 2) {
            val currentItem = state.getCurrentItem()
            val top = state.getTagstack().peek().getName()
            val second = state.getSecondTag().getName()
            if (DATE == top && ITEM == second) {
                val content = state.getContentBuf().toString()
                currentItem!!.setPubDate(DateUtils.parseOrNullIfFuture(content))
            }
        }
    }

    companion object {
        const val NSTAG = "dc"
        const val NSURI = "http://purl.org/dc/elements/1.1/"

        private const val ITEM = "item"
        private const val DATE = "date"
    }
}
