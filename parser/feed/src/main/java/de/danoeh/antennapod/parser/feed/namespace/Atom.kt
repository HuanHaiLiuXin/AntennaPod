package de.danoeh.antennapod.parser.feed.namespace

import android.text.TextUtils
import android.util.Log

import de.danoeh.antennapod.model.feed.FeedFunding
import de.danoeh.antennapod.parser.feed.HandlerState
import de.danoeh.antennapod.parser.feed.element.AtomText
import de.danoeh.antennapod.parser.feed.util.DateUtils
import de.danoeh.antennapod.parser.feed.util.SyndStringUtils
import org.xml.sax.Attributes

import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.parser.feed.element.SyndElement
import de.danoeh.antennapod.parser.feed.util.MimeTypeUtils

class Atom : Namespace() {

    override fun handleElementStart(localName: String, state: HandlerState,
                                    attributes: Attributes): SyndElement {
        if (ENTRY == localName) {
            state.setCurrentItem(FeedItem())
            state.getItems().add(state.getCurrentItem()!!)
            state.getCurrentItem()!!.setFeed(state.getFeed())
        } else if (localName.matches(Regex(isText))) {
            val type = attributes.getValue(TEXT_TYPE)
            return AtomText(localName, this, type)
        } else if (LINK == localName) {
            val href = attributes.getValue(LINK_HREF)
            val rel = attributes.getValue(LINK_REL)
            val parent = state.getTagstack().peek()
            if (parent.getName()!!.matches(Regex(isFeedItem)) && state.getCurrentItem() != null) {
                if (rel == null || LINK_REL_ALTERNATE == rel) {
                    state.getCurrentItem()!!.setLink(href)
                } else if (LINK_REL_ENCLOSURE == rel) {
                    val strSize = attributes.getValue(LINK_LENGTH)
                    var size = 0L
                    try {
                        if (!TextUtils.isEmpty(strSize)) {
                            size = java.lang.Long.parseLong(strSize)
                        }
                    } catch (e: NumberFormatException) {
                        Log.d(TAG, "Length attribute could not be parsed.")
                    }
                    val mimeType = MimeTypeUtils.getMimeType(attributes.getValue(LINK_TYPE), href)
                    var isValidMedia = MimeTypeUtils.isMediaFile(mimeType)
                    if (!isValidMedia && state.getCurrentItem()!!.getMedia() == null
                            && !MimeTypeUtils.isImageFile(mimeType)) {
                        isValidMedia = true
                    }

                    if (isValidMedia && !state.getCurrentItem()!!.hasMedia()) {
                        state.getCurrentItem()!!.setMedia(
                                FeedMedia(state.getCurrentItem(), href, size, mimeType))
                    }
                } else if (LINK_REL_PAYMENT == rel) {
                    state.getCurrentItem()!!.setPaymentLink(href)
                }
            } else if (parent.getName()!!.matches(Regex(isFeed))) {
                if (rel == null || LINK_REL_ALTERNATE == rel) {
                    val type = attributes.getValue(LINK_TYPE)
                    /*
                     * Use as link if a) no type-attribute is given and
                     * feed-object has no link yet b) type of link is
                     * LINK_TYPE_HTML or LINK_TYPE_XHTML
                     */
                    if (state.getFeed() != null
                            && ((type == null && state.getFeed().getLink() == null)
                                || (LINK_TYPE_HTML == type || LINK_TYPE_XHTML == type))) {
                        state.getFeed().setLink(href)
                    } else if (LINK_TYPE_ATOM == type || LINK_TYPE_RSS == type) {
                        // treat as podlove alternate feed
                        var title = attributes.getValue(LINK_TITLE)
                        if (TextUtils.isEmpty(title)) {
                            title = href
                        }
                        state.addAlternateFeedUrl(title, href)
                    }
                } else if (LINK_REL_ARCHIVES == rel && state.getFeed() != null) {
                    val type = attributes.getValue(LINK_TYPE)
                    if (LINK_TYPE_ATOM == type || LINK_TYPE_RSS == type) {
                        var title = attributes.getValue(LINK_TITLE)
                        if (TextUtils.isEmpty(title)) {
                            title = href
                        }
                        state.addAlternateFeedUrl(title, href)
                    } else if (LINK_TYPE_HTML == type || LINK_TYPE_XHTML == type) {
                        //A Link such as to a directory such as iTunes
                    }
                } else if (LINK_REL_PAYMENT == rel && state.getFeed() != null) {
                    state.getFeed().addPayment(FeedFunding(href, ""))
                } else if (LINK_REL_NEXT == rel && state.getFeed() != null) {
                    state.getFeed().setPaged(true)
                    state.getFeed().setNextPageLink(href)
                }
            }
        }
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
        if (ENTRY == localName) {
            if (state.getCurrentItem() != null &&
                    state.getTempObjects().containsKey(Itunes.DURATION)) {
                val currentItem = state.getCurrentItem()
                if (currentItem!!.hasMedia()) {
                    val duration = state.getTempObjects()[Itunes.DURATION] as Int
                    currentItem.getMedia()!!.setDuration(duration)
                }
                state.getTempObjects().remove(Itunes.DURATION)
            }
            state.setCurrentItem(null)
        }

        if (state.getTagstack().size >= 2) {
            var textElement: AtomText? = null
            val contentRaw: String
            if (state.getContentBuf() != null) {
                contentRaw = state.getContentBuf().toString()
            } else {
                contentRaw = ""
            }
            val content = SyndStringUtils.trimAllWhitespace(contentRaw)
            val topElement = state.getTagstack().peek()
            val top = topElement.getName()
            val secondElement = state.getSecondTag()
            val second = secondElement.getName()

            if (top!!.matches(Regex(isText))) {
                textElement = topElement as AtomText
                textElement.setContent(content)
            }

            if (ID == top) {
                if (FEED == second && state.getFeed() != null) {
                    state.getFeed().setFeedIdentifier(contentRaw)
                } else if (ENTRY == second && state.getCurrentItem() != null) {
                    state.getCurrentItem()!!.setItemIdentifier(contentRaw)
                }
            } else if (TITLE == top && textElement != null) {
                if (FEED == second && state.getFeed() != null) {
                    state.getFeed().setTitle(textElement.getProcessedContent())
                } else if (ENTRY == second && state.getCurrentItem() != null) {
                    state.getCurrentItem()!!.setTitle(textElement.getProcessedContent())
                }
            } else if (SUBTITLE == top && FEED == second && textElement != null
                    && state.getFeed() != null) {
                state.getFeed().setDescription(textElement.getProcessedContent())
            } else if (CONTENT == top && ENTRY == second && textElement != null
                    && state.getCurrentItem() != null) {
                state.getCurrentItem()!!.setDescriptionIfLonger(textElement.getProcessedContent())
            } else if (SUMMARY == top && ENTRY == second && textElement != null
                    && state.getCurrentItem() != null) {
                state.getCurrentItem()!!.setDescriptionIfLonger(textElement.getProcessedContent())
            } else if (UPDATED == top && ENTRY == second && state.getCurrentItem() != null
                    && state.getCurrentItem()!!.getPubDate() == null) {
                state.getCurrentItem()!!.setPubDate(DateUtils.parseOrNullIfFuture(content))
            } else if (PUBLISHED == top && ENTRY == second && state.getCurrentItem() != null) {
                state.getCurrentItem()!!.setPubDate(DateUtils.parseOrNullIfFuture(content))
            } else if (IMAGE_LOGO == top && state.getFeed() != null && state.getFeed().getImageUrl() == null) {
                state.getFeed().setImageUrl(content)
            } else if (IMAGE_ICON == top && state.getFeed() != null) {
                state.getFeed().setImageUrl(content)
            } else if (AUTHOR_NAME == top && AUTHOR == second
                    && state.getFeed() != null && state.getCurrentItem() == null) {
                val currentName = state.getFeed().getAuthor()
                if (currentName == null) {
                    state.getFeed().setAuthor(content)
                } else {
                    state.getFeed().setAuthor(currentName + ", " + content)
                }
            }
        }
    }

    companion object {
        private const val TAG = "NSAtom"
        const val NSTAG = "atom"
        const val NSURI = "http://www.w3.org/2005/Atom"

        private const val FEED = "feed"
        private const val ID = "id"
        private const val TITLE = "title"
        private const val ENTRY = "entry"
        private const val LINK = "link"
        private const val UPDATED = "updated"
        private const val AUTHOR = "author"
        private const val AUTHOR_NAME = "name"
        private const val CONTENT = "content"
        private const val SUMMARY = "summary"
        private const val IMAGE_LOGO = "logo"
        private const val IMAGE_ICON = "icon"
        private const val SUBTITLE = "subtitle"
        private const val PUBLISHED = "published"

        private const val TEXT_TYPE = "type"
        // Link
        private const val LINK_HREF = "href"
        private const val LINK_REL = "rel"
        private const val LINK_TYPE = "type"
        private const val LINK_TITLE = "title"
        private const val LINK_LENGTH = "length"
        // rel-values
        private const val LINK_REL_ALTERNATE = "alternate"
        private const val LINK_REL_ARCHIVES = "archives"
        private const val LINK_REL_ENCLOSURE = "enclosure"
        private const val LINK_REL_PAYMENT = "payment"
        private const val LINK_REL_NEXT = "next"
        // type-values
        private const val LINK_TYPE_ATOM = "application/atom+xml"
        private const val LINK_TYPE_HTML = "text/html"
        private const val LINK_TYPE_XHTML = "application/xml+xhtml"

        private const val LINK_TYPE_RSS = "application/rss+xml"

        /**
         * Regexp to test whether an Element is a Text Element.
         */
        private const val isText = TITLE + "|" + CONTENT + "|" +
                SUBTITLE + "|" + SUMMARY

        private const val isFeed = FEED + "|" + Rss20.CHANNEL
        private const val isFeedItem = ENTRY + "|" + Rss20.ITEM
    }
}
