package de.danoeh.antennapod.model.playback

import android.os.Parcel
import android.os.Parcelable
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia

import java.util.Date

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.builder.HashCodeBuilder

/**
 * Playable implementation for media for which a local version of
 * {@link FeedMedia} hasn't been found.
 * Used for Casting and for previewing unsubscribed feeds.
 */
class RemoteMedia : Playable {
    private var downloadUrl: String? = null
    private var itemIdentifier: String? = null
    private var feedUrl: String? = null
    private var feedTitle: String? = null
    private var episodeTitle: String? = null
    private var episodeLink: String? = null
    private var feedAuthor: String? = null
    private var imageUrl: String? = null
    private var feedLink: String? = null
    private var mimeType: String? = null
    private var pubDate: Date? = null
    private var notes: String? = null
    private var chapters: List<Chapter>? = null
    private var duration: Int = 0
    private var position: Int = 0
    private var lastPlayedTimeStatistics: Long = 0

    constructor(downloadUrl: String?, itemId: String?, feedUrl: String?, feedTitle: String?,
                episodeTitle: String?, episodeLink: String?, feedAuthor: String?,
                imageUrl: String?, feedLink: String?, mimeType: String?, pubDate: Date?,
                notes: String?) {
        this.downloadUrl = downloadUrl
        this.itemIdentifier = itemId
        this.feedUrl = feedUrl
        this.feedTitle = feedTitle
        this.episodeTitle = episodeTitle
        this.episodeLink = episodeLink
        this.feedAuthor = feedAuthor
        this.imageUrl = imageUrl
        this.feedLink = feedLink
        this.mimeType = mimeType
        this.pubDate = pubDate
        this.notes = notes
    }

    constructor(item: FeedItem) {
        this.downloadUrl = item.getMedia()!!.getDownloadUrl()
        this.itemIdentifier = item.getItemIdentifier()
        this.feedUrl = item.getFeed()!!.getDownloadUrl()
        this.feedTitle = item.getFeed()!!.getTitle()
        this.episodeTitle = item.getTitle()
        this.episodeLink = item.getLink()
        this.feedAuthor = item.getFeed()!!.getAuthor()
        if (!StringUtils.isEmpty(item.getImageUrl())) {
            this.imageUrl = item.getImageUrl()
        } else {
            this.imageUrl = item.getFeed()!!.getImageUrl()
        }
        this.feedLink = item.getFeed()!!.getLink()
        this.mimeType = item.getMedia()!!.getMimeType()
        this.pubDate = item.getPubDate()
        this.notes = item.getDescription()
    }

    fun getEpisodeIdentifier(): String? {
        return itemIdentifier
    }

    fun getFeedUrl(): String? {
        return feedUrl
    }

    fun getDownloadUrl(): String? {
        return downloadUrl
    }

    fun getEpisodeLink(): String? {
        return episodeLink
    }

    fun getFeedAuthor(): String? {
        return feedAuthor
    }

    fun getImageUrl(): String? {
        return imageUrl
    }

    fun getFeedLink(): String? {
        return feedLink
    }

    fun getMimeType(): String? {
        return mimeType
    }

    override fun getPubDate(): Date? {
        return pubDate
    }

    fun getNotes(): String? {
        return notes
    }

    override fun getEpisodeTitle(): String? {
        return episodeTitle
    }

    override fun getChapters(): List<Chapter>? {
        return chapters
    }

    override fun getWebsiteLink(): String? {
        if (episodeLink != null) {
            return episodeLink
        } else {
            return feedUrl
        }
    }

    override fun getFeedTitle(): String? {
        return feedTitle
    }

    override fun getIdentifier(): Any? {
        return itemIdentifier + "@" + feedUrl
    }

    override fun getDuration(): Int {
        return duration
    }

    override fun getPosition(): Int {
        return position
    }

    override fun getLastPlayedTimeStatistics(): Long {
        return lastPlayedTimeStatistics
    }

    override fun getMediaType(): MediaType {
        return MediaType.fromMimeType(mimeType)
    }

    override fun getLocalFileUrl(): String? {
        return null
    }

    override fun getStreamUrl(): String? {
        return downloadUrl
    }

    override fun localFileAvailable(): Boolean {
        return false
    }

    override fun setPosition(newPosition: Int) {
        position = newPosition
    }

    override fun setDuration(newDuration: Int) {
        duration = newDuration
    }

    override fun setLastPlayedTimeStatistics(lastPlayedTimestamp: Long) {
        lastPlayedTimeStatistics = lastPlayedTimestamp
    }

    override fun onPlaybackStart() {
        // no-op
    }

    override fun getPlayableType(): Int {
        return PLAYABLE_TYPE_REMOTE_MEDIA
    }

    override fun setChapters(chapters: List<Chapter>?) {
        this.chapters = chapters
    }

    override fun getImageLocation(): String? {
        return imageUrl
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun getDescription(): String? {
        return notes
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(downloadUrl)
        dest.writeString(itemIdentifier)
        dest.writeString(feedUrl)
        dest.writeString(feedTitle)
        dest.writeString(episodeTitle)
        dest.writeString(episodeLink)
        dest.writeString(feedAuthor)
        dest.writeString(imageUrl)
        dest.writeString(feedLink)
        dest.writeString(mimeType)
        dest.writeLong(if (pubDate != null) pubDate!!.time else 0)
        dest.writeString(notes)
        dest.writeInt(duration)
        dest.writeInt(position)
        dest.writeLong(lastPlayedTimeStatistics)
    }

    override fun equals(other: Any?): Boolean {
        if (other is RemoteMedia) {
            val rm = other as RemoteMedia
            return StringUtils.equals(downloadUrl, rm.downloadUrl)
                    && StringUtils.equals(feedUrl, rm.feedUrl)
                    && StringUtils.equals(itemIdentifier, rm.itemIdentifier)
        }
        if (other is FeedMedia) {
            val fm = other as FeedMedia
            if (!StringUtils.equals(downloadUrl, fm.getStreamUrl())) {
                return false
            }
            val fi = fm.getItem()
            if (fi == null || !StringUtils.equals(itemIdentifier, fi.getItemIdentifier())) {
                return false
            }
            val feed = fi.getFeed()
            return feed != null && StringUtils.equals(feedUrl, feed.getDownloadUrl())
        }
        return false
    }

    override fun hashCode(): Int {
        return HashCodeBuilder()
                .append(downloadUrl)
                .append(feedUrl)
                .append(itemIdentifier)
                .toHashCode()
    }

    companion object {
        const val TAG = "RemoteMedia"

        const val PLAYABLE_TYPE_REMOTE_MEDIA = 3

        @JvmField
        val CREATOR: Parcelable.Creator<RemoteMedia> = object : Parcelable.Creator<RemoteMedia> {
            override fun createFromParcel(source: Parcel): RemoteMedia {
                val result = RemoteMedia(source.readString(), source.readString(), source.readString(),
                        source.readString(), source.readString(), source.readString(), source.readString(),
                        source.readString(), source.readString(), source.readString(),
                        Date(source.readLong()), source.readString())
                result.setDuration(source.readInt())
                result.setPosition(source.readInt())
                result.setLastPlayedTimeStatistics(source.readLong())
                return result
            }

            override fun newArray(size: Int) = arrayOfNulls<RemoteMedia>(size)
        }
    }
}
