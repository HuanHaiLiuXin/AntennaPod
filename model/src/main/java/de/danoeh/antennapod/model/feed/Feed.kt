package de.danoeh.antennapod.model.feed

import java.io.Serializable
import java.util.ArrayList
import java.util.Date
import java.util.Objects

import org.apache.commons.lang3.StringUtils

/**
 * Data Object for a whole feed.
 *
 * @author daniel
 */
class Feed : Serializable {

    private var id: Long = 0
    private var localFileUrl: String? = null
    private var downloadUrl: String? = null
    /**
     * title as defined by the feed.
     */
    private var feedTitle: String? = null

    /**
     * custom title set by the user.
     */
    private var customTitle: String? = null

    /**
     * Contains 'id'-element in Atom feed.
     */
    private var feedIdentifier: String? = null
    /**
     * Link to the website.
     */
    private var link: String? = null
    private var description: String? = null
    private var language: String? = null
    /**
     * Name of the author.
     */
    private var author: String? = null
    private var imageUrl: String? = null
    private var items: List<FeedItem>? = null

    /**
     * String that identifies the last update (adopted from Last-Modified or ETag header).
     */
    private var lastModified: String? = null
    private var lastRefreshAttempt: Long = 0

    private var fundingList: ArrayList<FeedFunding>? = null
    /**
     * Feed type, for example RSS 2 or Atom.
     */
    private var type: String? = null

    /**
     * Feed preferences.
     */
    private var preferences: FeedPreferences? = null

    /**
     * The page number that this feed is on. Only feeds with page number "0" should be stored in the
     * database, feed objects with a higher page number only exist temporarily and should be merged
     * into feeds with page number "0".
     * <p/>
     * This attribute's value is not saved in the database
     */
    private var pageNr: Int = 0

    /**
     * True if this is a "paged feed", i.e. there exist other feed files that belong to the same
     * logical feed.
     */
    private var paged: Boolean = false

    /**
     * Link to the next page of this feed. If this feed object represents a logical feed (i.e. a feed
     * that is saved in the database) this might be null while still being a paged feed.
     */
    private var nextPageLink: String? = null

    private var lastUpdateFailed: Boolean = false

    /**
     * Contains property strings. If such a property applies to a feed item, it is not shown in the feed list
     */
    private var itemfilter: FeedItemFilter? = null

    /**
     * User-preferred sortOrder for display.
     * Only those of scope {@link SortOrder.Scope#INTRA_FEED} is allowed.
     */
    private var sortOrder: SortOrder? = null
    private var state: Int = 0

    /**
     * This constructor is used for restoring a feed from the database.
     */
    constructor(id: Long, lastModified: String?, title: String?, customTitle: String?, link: String?,
                description: String?, paymentLinks: String?, author: String?, language: String?,
                type: String?, feedIdentifier: String?, imageUrl: String?, fileUrl: String?,
                downloadUrl: String?, lastRefreshAttempt: Long, paged: Boolean, nextPageLink: String?,
                filter: String?, sortOrder: SortOrder?, lastUpdateFailed: Boolean, state: Int) {
        this.localFileUrl = fileUrl
        this.downloadUrl = downloadUrl
        this.lastRefreshAttempt = lastRefreshAttempt
        this.id = id
        this.feedTitle = title
        this.customTitle = customTitle
        this.lastModified = lastModified
        this.link = link
        this.description = description
        this.fundingList = FeedFunding.extractPaymentLinks(paymentLinks)
        this.author = author
        this.language = language
        this.type = type
        this.feedIdentifier = feedIdentifier
        this.imageUrl = imageUrl
        this.paged = paged
        this.nextPageLink = nextPageLink
        this.items = ArrayList()
        if (filter != null) {
            this.itemfilter = FeedItemFilter(filter)
        } else {
            this.itemfilter = FeedItemFilter()
        }
        setSortOrder(sortOrder)
        this.lastUpdateFailed = lastUpdateFailed
        this.state = state
    }

    /**
     * This constructor is used for test purposes.
     */
    constructor(id: Long, lastModified: String?, title: String?, link: String?, description: String?,
                paymentLink: String?, author: String?, language: String?, type: String?,
                feedIdentifier: String?, imageUrl: String?, fileUrl: String?,
                downloadUrl: String?, lastRefreshAttempt: Long) : this(id, lastModified, title, null,
            link, description, paymentLink, author, language, type, feedIdentifier,
            imageUrl, fileUrl, downloadUrl, lastRefreshAttempt, false, null, null, null, false,
            STATE_SUBSCRIBED) {
    }

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!).
     * It should NOT be used if the title of the feed is already known.
     */
    constructor(url: String, lastModified: String?) {
        this.localFileUrl = null
        this.downloadUrl = url
        this.lastRefreshAttempt = 0
        this.lastModified = lastModified
    }

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should be
     * used if the title of the feed is already known.
     */
    constructor(url: String, lastModified: String?, title: String?) : this(url, lastModified) {
        this.feedTitle = title
    }

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should be
     * used if the title of the feed is already known.
     */
    constructor(url: String, lastModified: String?, title: String?, username: String?, password: String?)
            : this(url, lastModified, title) {
        preferences = FeedPreferences(0, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, username, password)
    }

    /**
     * Returns the item at the specified index.
     *
     */
    fun getItemAtIndex(position: Int): FeedItem? {
        return items!![position]
    }

    /**
     * Returns the value that uniquely identifies this Feed. If the
     * feedIdentifier attribute is not null, it will be returned. Else it will
     * try to return the title. If the title is not given, it will use the link
     * of the feed.
     */
    fun getIdentifyingValue(): String? {
        if (feedIdentifier != null && !feedIdentifier!!.isEmpty()) {
            return feedIdentifier
        } else if (downloadUrl != null && !downloadUrl!!.isEmpty()) {
            return downloadUrl
        } else if (feedTitle != null && !feedTitle!!.isEmpty()) {
            return feedTitle
        } else {
            return link
        }
    }

    fun getHumanReadableIdentifier(): String? {
        if (!StringUtils.isEmpty(customTitle)) {
            return customTitle
        } else if (!StringUtils.isEmpty(feedTitle)) {
            return feedTitle
        } else {
            return downloadUrl
        }
    }

    fun updateFromOther(other: Feed) {
        // don't update feed's download_url, we do that manually if redirected
        // see AntennapodHttpClient
        if (other.imageUrl != null) {
            this.imageUrl = other.imageUrl
        }
        if (other.feedTitle != null) {
            feedTitle = other.feedTitle
        }
        if (other.feedIdentifier != null) {
            feedIdentifier = other.feedIdentifier
        }
        if (other.link != null) {
            link = other.link
        }
        if (other.description != null) {
            description = other.description
        }
        if (other.language != null) {
            language = other.language
        }
        if (other.author != null) {
            author = other.author
        }
        if (other.fundingList != null) {
            fundingList = other.fundingList
        }
        if (other.lastRefreshAttempt > lastRefreshAttempt) {
            lastRefreshAttempt = other.lastRefreshAttempt
        }
        // this feed's nextPage might already point to a higher page, so we only update the nextPage value
        // if this feed is not paged and the other feed is.
        if (!this.paged && other.paged) {
            this.paged = other.paged
            this.nextPageLink = other.nextPageLink
        }
    }

    fun getMostRecentItem(): FeedItem? {
        // we could sort, but we don't need to, a simple search is fine...
        var mostRecentDate = Date(0)
        var mostRecentItem: FeedItem? = null
        for (item in items!!) {
            if (item.getPubDate() != null && item.getPubDate()!!.after(mostRecentDate)) {
                mostRecentDate = item.getPubDate()!!
                mostRecentItem = item
            }
        }
        return mostRecentItem
    }

    fun getTitle(): String? {
        return if (!StringUtils.isEmpty(customTitle)) customTitle else feedTitle
    }

    fun setTitle(title: String?) {
        this.feedTitle = title
    }

    fun getFeedTitle(): String? {
        return this.feedTitle
    }

    fun getCustomTitle(): String? {
        return this.customTitle
    }

    fun setCustomTitle(customTitle: String?) {
        if (customTitle == null || customTitle == feedTitle) {
            this.customTitle = null
        } else {
            this.customTitle = customTitle
        }
    }

    fun getLink(): String? {
        return link
    }

    fun setLink(link: String?) {
        this.link = link
    }

    fun getDescription(): String? {
        return description
    }

    fun setDescription(description: String?) {
        this.description = description
    }

    fun getImageUrl(): String? {
        return imageUrl
    }

    fun setImageUrl(imageUrl: String?) {
        this.imageUrl = imageUrl
    }

    fun getItems(): List<FeedItem>? {
        return items
    }

    fun setItems(list: List<FeedItem>?) {
        this.items = list
    }

    fun getLastModified(): String? {
        return lastModified
    }

    fun setLastModified(lastModified: String?) {
        this.lastModified = lastModified
    }

    fun getFeedIdentifier(): String? {
        return feedIdentifier
    }

    fun setFeedIdentifier(feedIdentifier: String?) {
        this.feedIdentifier = feedIdentifier
    }

    fun addPayment(funding: FeedFunding) {
        if (fundingList == null) {
            fundingList = ArrayList<FeedFunding>()
        }
        fundingList!!.add(funding)
    }

    fun getPaymentLinks(): ArrayList<FeedFunding>? {
        return fundingList
    }

    fun getLanguage(): String? {
        return language
    }

    fun setLanguage(language: String?) {
        this.language = language
    }

    fun getAuthor(): String? {
        return author
    }

    fun setAuthor(author: String?) {
        this.author = author
    }

    fun getType(): String? {
        return type
    }

    fun setType(type: String?) {
        this.type = type
    }

    fun setPreferences(preferences: FeedPreferences?) {
        this.preferences = preferences
    }

    fun getPreferences(): FeedPreferences? {
        return preferences
    }

    fun setId(id: Long) {
        this.id = id
        if (preferences != null) {
            preferences!!.setFeedID(id)
        }
    }

    fun getId(): Long {
        return id
    }

    fun getLocalFileUrl(): String? {
        return localFileUrl
    }

    fun setLocalFileUrl(fileUrl: String?) {
        this.localFileUrl = fileUrl
    }

    fun getDownloadUrl(): String? {
        return downloadUrl
    }

    fun setDownloadUrl(downloadUrl: String?) {
        this.downloadUrl = downloadUrl
    }

    fun getLastRefreshAttempt(): Long {
        return lastRefreshAttempt
    }

    fun setLastRefreshAttempt(lastRefreshAttempt: Long) {
        this.lastRefreshAttempt = lastRefreshAttempt
    }

    fun getPageNr(): Int {
        return pageNr
    }

    fun setPageNr(pageNr: Int) {
        this.pageNr = pageNr
    }

    fun isPaged(): Boolean {
        return paged
    }

    fun setPaged(paged: Boolean) {
        this.paged = paged
    }

    fun getNextPageLink(): String? {
        return nextPageLink
    }

    fun setNextPageLink(nextPageLink: String?) {
        this.nextPageLink = nextPageLink
    }

    fun getItemFilter(): FeedItemFilter? {
        return itemfilter
    }

    fun getSortOrder(): SortOrder? {
        return sortOrder
    }

    fun setSortOrder(sortOrder: SortOrder?) {
        if (sortOrder != null && sortOrder.scope != SortOrder.Scope.INTRA_FEED) {
            throw IllegalArgumentException("The specified sortOrder " + sortOrder
                    + " is invalid. Only those with INTRA_FEED scope are allowed.")
        }
        this.sortOrder = sortOrder
    }

    fun hasLastUpdateFailed(): Boolean {
        return this.lastUpdateFailed
    }

    fun setLastUpdateFailed(lastUpdateFailed: Boolean) {
        this.lastUpdateFailed = lastUpdateFailed
    }

    fun isLocalFeed(): Boolean {
        return downloadUrl!!.startsWith(PREFIX_LOCAL_FOLDER)
    }

    fun getState(): Int {
        return state
    }

    fun setState(state: Int) {
        this.state = state
    }

    fun hasEpisodeInApp(): Boolean {
        if (items == null) {
            return false
        }
        for (item in items!!) {
            if (item.isTagged(FeedItem.TAG_FAVORITE)
                    || item.isTagged(FeedItem.TAG_QUEUE)
                    || item.isDownloaded()) {
                return true
            }
        }
        return false
    }

    fun hasInteractedWithEpisode(): Boolean {
        if (items == null) {
            return false
        }
        for (item in items!!) {
            if (item.isTagged(FeedItem.TAG_FAVORITE)
                    || item.isTagged(FeedItem.TAG_QUEUE)
                    || item.isDownloaded()
                    || item.isPlayed()) {
                return true
            }
            if (item.getMedia() != null && item.getMedia()!!.getPosition() > 0) {
                return true
            }
        }
        return false
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val feed = o as Feed
        return id == feed.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    companion object {
        const val FEEDFILETYPE_FEED = 0
        const val STATE_SUBSCRIBED = 0
        const val STATE_NOT_SUBSCRIBED = 1
        const val STATE_ARCHIVED = 2
        const val TYPE_RSS2 = "rss"
        const val TYPE_ATOM1 = "atom"
        const val PREFIX_LOCAL_FOLDER = "antennapod_local:"
        const val PREFIX_GENERATIVE_COVER = "antennapod_generative_cover:"
    }
}
