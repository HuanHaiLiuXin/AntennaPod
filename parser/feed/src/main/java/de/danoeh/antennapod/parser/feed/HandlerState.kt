package de.danoeh.antennapod.parser.feed

import java.util.ArrayList
import java.util.HashMap
import java.util.Stack

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedFunding
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.parser.feed.namespace.Namespace
import de.danoeh.antennapod.parser.feed.element.SyndElement

/**
 * Contains all relevant information to describe the current state of a
 * SyndHandler.
 */
class HandlerState {

    /**
     * Feed that the Handler is currently processing.
     */
    @JvmField
    var feed: Feed

    /**
     * Contains links to related feeds, e.g. feeds with enclosures in other formats. The key of the map is the
     * URL of the feed, the value is the title
     */
    @JvmField
    val alternateUrls: HashMap<String?, String?>

    @JvmField
    var redirectUrl: String? = null
    private val items: ArrayList<FeedItem>
    private var currentItem: FeedItem? = null
    private var currentFunding: FeedFunding? = null
    internal val tagstack: Stack<SyndElement>

    /**
     * Namespaces that have been defined so far.
     */
    internal val namespaces: HashMap<String, Namespace>
    internal val defaultNamespaces: Stack<Namespace>

    /**
     * Buffer for saving characters.
     */
    @JvmField
    var contentBuf: StringBuilder? = null

    /**
     * Temporarily saved objects.
     */
    private val tempObjects: HashMap<String, Any>

    constructor(feed: Feed) {
        this.feed = feed
        alternateUrls = HashMap<String?, String?>()
        items = ArrayList()
        tagstack = Stack()
        namespaces = HashMap<String, Namespace>()
        defaultNamespaces = Stack()
        tempObjects = HashMap<String, Any>()
    }

    fun getFeed(): Feed {
        return feed
    }

    fun getItems(): ArrayList<FeedItem> {
        return items
    }

    fun getCurrentItem(): FeedItem? {
        return currentItem
    }

    fun getTagstack(): Stack<SyndElement> {
        return tagstack
    }

    fun setFeed(feed: Feed) {
        this.feed = feed
    }

    fun setCurrentItem(currentItem: FeedItem?) {
        this.currentItem = currentItem
    }

    fun getCurrentFunding(): FeedFunding? {
        return currentFunding
    }

    fun setCurrentFunding(currentFunding: FeedFunding?) {
        this.currentFunding = currentFunding
    }

    /**
     * Returns the SyndElement that comes after the top element of the tagstack.
     */
    fun getSecondTag(): SyndElement {
        val top = tagstack.pop()
        val second = tagstack.peek()
        tagstack.push(top)
        return second
    }

    fun getThirdTag(): SyndElement {
        val top = tagstack.pop()
        val second = tagstack.pop()
        val third = tagstack.peek()
        tagstack.push(second)
        tagstack.push(top)
        return third
    }

    fun getContentBuf(): StringBuilder? {
        return contentBuf
    }

    fun addAlternateFeedUrl(title: String?, url: String?) {
        alternateUrls.put(url, title)
    }

    fun getTempObjects(): HashMap<String, Any> {
        return tempObjects
    }
}
