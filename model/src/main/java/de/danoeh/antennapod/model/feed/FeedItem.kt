package de.danoeh.antennapod.model.feed

import org.apache.commons.lang3.StringUtils

import java.io.Serializable
import java.util.Date
import java.util.HashSet
import java.util.Objects

/**
 * Item (episode) within a feed.
 *
 * @author daniel
 */
class FeedItem : Serializable {

    private var id: Long = 0
    /**
     * The id/guid that can be found in the rss/atom feed. Might not be set.
     */
    private var itemIdentifier: String? = null
    private var title: String? = null
    /**
     * The description of a feeditem.
     */
    private var description: String? = null

    private var link: String? = null
    private var pubDate: Date? = null
    private var media: FeedMedia? = null

    @Transient
    private var feed: Feed? = null
    private var feedId: Long = 0
    private var podcastIndexChapterUrl: String? = null
    private var socialInteractUrl: String? = null
    private var podcastIndexTranscriptUrl: String? = null
    private var podcastIndexTranscriptType: String? = null
    private var transcript: Transcript? = null

    private var state: Int = 0

    private var paymentLink: String? = null

    /**
     * Is true if the database contains any chapters that belong to this item. This attribute is only
     * written once by DBReader on initialization.
     * The FeedItem might still have a non-null chapters value. In this case, the list of chapters
     * has not been saved in the database yet.
     * */
    private var hasChapters: Boolean = false

    /**
     * The list of chapters of this item. This might be null even if there are chapters of this item
     * in the database. The 'hasChapters' attribute should be used to check if this item has any chapters.
     * */
    @Transient
    private var chapters: List<Chapter>? = null
    private var imageUrl: String? = null

    private var autoDownloadEnabled: Boolean = true

    /**
     * Any tags assigned to this item
     */
    private val tags = HashSet<String?>()

    constructor() {
        this.state = UNPLAYED
        this.hasChapters = false
    }

    /**
     * This constructor is used by DBReader.
     * */
    constructor(id: Long, title: String?, link: String?, pubDate: Date?, paymentLink: String?,
                feedId: Long, hasChapters: Boolean, imageUrl: String?, state: Int,
                itemIdentifier: String?, autoDownloadEnabled: Boolean, podcastIndexChapterUrl: String?,
                transcriptType: String?, transcriptUrl: String?, socialInteractUrl: String?) {
        this.id = id
        this.title = title
        this.link = link
        this.pubDate = pubDate
        this.paymentLink = paymentLink
        this.feedId = feedId
        this.hasChapters = hasChapters
        this.imageUrl = imageUrl
        this.state = state
        this.itemIdentifier = itemIdentifier
        this.autoDownloadEnabled = autoDownloadEnabled
        this.podcastIndexChapterUrl = podcastIndexChapterUrl
        this.socialInteractUrl = socialInteractUrl
        if (transcriptUrl != null) {
            this.podcastIndexTranscriptUrl = transcriptUrl
            this.podcastIndexTranscriptType = transcriptType
        }
    }

    /**
     * This constructor should be used for creating test objects.
     */
    constructor(id: Long, title: String?, itemIdentifier: String?, link: String?, pubDate: Date?,
                state: Int, feed: Feed?) {
        this.id = id
        this.title = title
        this.itemIdentifier = itemIdentifier
        this.link = link
        this.pubDate = if (pubDate != null) pubDate.clone() as Date else null
        this.state = state
        this.feed = feed
        this.hasChapters = false
    }

    /**
     * This constructor should be used for creating test objects involving chapter marks.
     */
    constructor(id: Long, title: String?, itemIdentifier: String?, link: String?, pubDate: Date?,
                state: Int, feed: Feed?, hasChapters: Boolean) {
        this.id = id
        this.title = title
        this.itemIdentifier = itemIdentifier
        this.link = link
        this.pubDate = if (pubDate != null) pubDate.clone() as Date else null
        this.state = state
        this.feed = feed
        this.hasChapters = hasChapters
    }

    fun updateFromOther(other: FeedItem) {
        if (other.imageUrl != null) {
            this.imageUrl = other.imageUrl
        }
        if (other.title != null) {
            title = other.title
        }
        if (other.getDescription() != null) {
            description = other.getDescription()
        }
        if (other.link != null) {
            link = other.link
        }
        if (other.pubDate != null && other.pubDate != pubDate) {
            pubDate = other.pubDate
        }
        if (other.media != null) {
            if (media == null) {
                setMedia(other.media!!)
                // reset to new if feed item did link to a file before
                setNew()
            } else if (media!!.compareWithOther(other.media!!)) {
                media!!.updateFromOther(other.media!!)
            }
        }
        if (other.paymentLink != null) {
            paymentLink = other.paymentLink
        }
        if (other.chapters != null) {
            if (!hasChapters) {
                chapters = other.chapters
            }
        }
        if (other.podcastIndexChapterUrl != null) {
            podcastIndexChapterUrl = other.podcastIndexChapterUrl
        }
        if (other.socialInteractUrl != null) {
            socialInteractUrl = other.socialInteractUrl
        }
        if (other.getTranscriptUrl() != null) {
            podcastIndexTranscriptUrl = other.podcastIndexTranscriptUrl
        }
        if (other.getTranscriptType() != null) {
            podcastIndexTranscriptType = other.podcastIndexTranscriptType
        }
    }

    fun getId(): Long {
        return id
    }

    fun setId(id: Long) {
        this.id = id
        if (this.media != null) {
            media!!.setItemId(id)
        }
    }

    /**
     * Returns the value that uniquely identifies this FeedItem. If the
     * itemIdentifier attribute is not null, it will be returned. Else it will
     * try to return the title. If the title is not given, it will use the link
     * of the entry.
     */
    fun getIdentifyingValue(): String? {
        if (itemIdentifier != null && !itemIdentifier!!.isEmpty()) {
            return itemIdentifier
        } else if (title != null && !title!!.isEmpty()) {
            return title
        } else if (hasMedia() && media!!.getDownloadUrl() != null) {
            return media!!.getDownloadUrl()
        } else {
            return link
        }
    }

    fun getTitle(): String? {
        return title
    }

    fun setTitle(title: String?) {
        this.title = title
    }

    fun getDescription(): String? {
        return description
    }

    fun getLink(): String? {
        return link
    }

    /**
     * Get the link for the feed item for the purpose of Share.
     * It falls backs to the feed's link if the item has no link.
     */
    fun getLinkWithFallback(): String? {
        if (StringUtils.isNotBlank(link)) {
            return link
        } else if (StringUtils.isNotBlank(getFeed()!!.getLink())) {
            return getFeed()!!.getLink()
        }
        return null
    }

    fun setLink(link: String?) {
        this.link = link
    }

    fun getPubDate(): Date? {
        if (pubDate != null) {
            return pubDate!!.clone() as Date
        } else {
            return null
        }
    }

    fun setPubDate(pubDate: Date?) {
        if (pubDate != null) {
            this.pubDate = pubDate.clone() as Date
        } else {
            this.pubDate = null
        }
    }

    fun getMedia(): FeedMedia? {
        return media
    }

    /**
     * Sets the media object of this FeedItem. If the given
     * FeedMedia object is not null, it's 'item'-attribute value
     * will also be set to this item.
     */
    fun setMedia(media: FeedMedia?) {
        this.media = media
        if (media != null && media.getItem() !== this) {
            media.setItem(this)
        }
    }

    fun getFeed(): Feed? {
        return feed
    }

    fun setFeed(feed: Feed?) {
        this.feed = feed
    }

    fun isNew(): Boolean {
        return state == NEW
    }

    fun getPlayState(): Int {
        return state
    }

    fun setPlayState(state: Int) {
        this.state = state
    }

    fun setNew() {
        state = NEW
    }

    fun isPlayed(): Boolean {
        return state == PLAYED
    }

    fun setPlayed(played: Boolean) {
        if (played) {
            state = PLAYED
        } else {
            state = UNPLAYED
        }
    }

    fun isInProgress(): Boolean {
        return (media != null && media!!.isInProgress())
    }

    /**
     * Updates this item's description property if the given argument is longer than the already stored description
     * @param newDescription The new item description, content:encoded, itunes:description, etc.
     */
    fun setDescriptionIfLonger(newDescription: String?) {
        if (newDescription == null) {
            return
        }
        if (this.description == null) {
            this.description = newDescription
        } else if (this.description!!.length < newDescription.length) {
            this.description = newDescription
        }
    }

    fun getPaymentLink(): String? {
        return paymentLink
    }

    fun setPaymentLink(paymentLink: String?) {
        this.paymentLink = paymentLink
    }

    fun getChapters(): List<Chapter>? {
        return chapters
    }

    fun setChapters(chapters: List<Chapter>?) {
        this.chapters = chapters
    }

    fun getItemIdentifier(): String? {
        return itemIdentifier
    }

    fun setItemIdentifier(itemIdentifier: String?) {
        this.itemIdentifier = itemIdentifier
    }

    fun hasMedia(): Boolean {
        return media != null
    }

    fun getImageLocation(): String? {
        if (imageUrl != null) {
            return imageUrl
        } else if (media != null && media!!.hasEmbeddedPicture()) {
            return FeedMedia.FILENAME_PREFIX_EMBEDDED_COVER + media!!.getLocalFileUrl()
        } else if (feed != null) {
            return feed!!.getImageUrl()
        } else {
            return null
        }
    }

    fun getFeedId(): Long {
        return feedId
    }

    fun setFeedId(feedId: Long) {
        this.feedId = feedId
    }

    /**
     * Returns the image of this item, as specified in the feed.
     * To load the image that can be displayed to the user, use {@link #getImageLocation},
     * which also considers embedded pictures or the feed picture if no other picture is present.
     */
    fun getImageUrl(): String? {
        return imageUrl
    }

    fun setImageUrl(imageUrl: String?) {
        this.imageUrl = imageUrl
    }

    fun hasChapters(): Boolean {
        return hasChapters
    }

    fun disableAutoDownload() {
        this.autoDownloadEnabled = false
    }

    fun isAutoDownloadEnabled(): Boolean {
        return this.autoDownloadEnabled
    }

    fun isDownloaded(): Boolean {
        return media != null && media!!.isDownloaded()
    }

    /**
     * @return true if the item has this tag
     */
    fun isTagged(tag: String?): Boolean {
        return tags.contains(tag)
    }

    /**
     * @param tag adds this tag to the item. NOTE: does NOT persist to the database
     */
    fun addTag(tag: String?) {
        tags.add(tag)
    }

    /**
     * @param tag the to remove
     */
    fun removeTag(tag: String?) {
        tags.remove(tag)
    }

    fun getPodcastIndexChapterUrl(): String? {
        return podcastIndexChapterUrl
    }

    fun setPodcastIndexChapterUrl(url: String?) {
        podcastIndexChapterUrl = url
    }

    fun setSocialInteractUrl(url: String?) {
        socialInteractUrl = url
    }

    fun getSocialInteractUrl(): String? {
        return socialInteractUrl
    }

    fun setTranscriptUrl(type: String?, url: String?) {
        updateTranscriptPreferredFormat(type, url)
    }

    fun getTranscriptUrl(): String? {
        return podcastIndexTranscriptUrl
    }

    fun getTranscriptType(): String? {
        return podcastIndexTranscriptType
    }

    fun updateTranscriptPreferredFormat(typeStr: String?, url: String?) {
        if (StringUtils.isEmpty(typeStr) || StringUtils.isEmpty(url)) {
            return
        }
        val type = TranscriptType.fromMime(typeStr)
        val previousType = TranscriptType.fromMime(podcastIndexTranscriptType)
        if (type.priority > previousType.priority) {
            podcastIndexTranscriptUrl = url
            podcastIndexTranscriptType = type.canonicalMime
        }
    }

    fun getTranscript(): Transcript? {
        return transcript
    }

    fun setTranscript(t: Transcript?) {
        transcript = t
    }

    fun hasTranscript(): Boolean {
        return (podcastIndexTranscriptUrl != null)
    }

    override fun toString(): String {
        return "FeedItem [id=" + id + ", title=" + title + ", feedId=" + feedId + ", pubDate=" + pubDate + "]"
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val feedItem = o as FeedItem
        return id == feedItem.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    companion object {
        /** tag that indicates this item is in the queue */
        const val TAG_QUEUE = "Queue"
        /** tag that indicates this item is in favorites */
        const val TAG_FAVORITE = "Favorite"

        const val NEW = -1
        const val UNPLAYED = 0
        const val PLAYED = 1
    }
}
