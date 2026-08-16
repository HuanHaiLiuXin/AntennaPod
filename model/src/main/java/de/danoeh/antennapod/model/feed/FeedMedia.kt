package de.danoeh.antennapod.model.feed

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import de.danoeh.antennapod.model.MediaMetadataRetrieverCompat
import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.model.playback.RemoteMedia
import org.apache.commons.lang3.StringUtils

import java.io.File
import java.util.Date

class FeedMedia : Playable {

    private var id: Long = 0
    private var localFileUrl: String? = null
    private var downloadUrl: String? = null
    private var downloadDate: Long = 0
    private var duration: Int = 0
    private var position: Int = 0 // Current position in file
    private var lastPlayedTimeStatistics: Long = 0 // Last time this media was played (in ms)
    private var playedDuration: Int = 0 // How many ms of this file have been played
    private var size: Long = 0 // File size in Byte
    private var mimeType: String? = null
    @Volatile
    private var item: FeedItem? = null
    private var lastPlayedTimeHistory: Date? = null
    private var startPosition: Int = -1
    private var playedDurationWhenStarted: Int = 0

    // if null: unknown, will be checked
    private var hasEmbeddedPicture: Boolean? = null

    /* Used for loading item when restoring from parcel. */
    private var itemID: Long = 0

    constructor(i: FeedItem?, downloadUrl: String?, size: Long,
                mimeType: String?) {
        this.localFileUrl = null
        this.downloadUrl = downloadUrl
        this.downloadDate = 0
        this.item = i
        this.itemID = if (i != null) i.getId() else 0
        this.size = size
        this.mimeType = mimeType
    }

    constructor(id: Long, item: FeedItem?, duration: Int, position: Int,
                size: Long, mimeType: String?, localFileUrl: String?, downloadUrl: String?,
                downloadDate: Long, lastPlayedTimeHistory: Date?, playedDuration: Int,
                lastPlayedTimeStatistics: Long) {
        this.localFileUrl = localFileUrl
        this.downloadUrl = downloadUrl
        this.downloadDate = downloadDate
        this.id = id
        this.item = item
        this.itemID = if (item != null) item.getId() else 0
        this.duration = duration
        this.position = position
        this.playedDuration = playedDuration
        this.playedDurationWhenStarted = playedDuration
        this.size = size
        this.mimeType = mimeType
        this.lastPlayedTimeHistory = if (lastPlayedTimeHistory == null)
            null else lastPlayedTimeHistory.clone() as Date
        this.lastPlayedTimeStatistics = lastPlayedTimeStatistics
    }

    constructor(id: Long, item: FeedItem?, duration: Int, position: Int,
                size: Long, mimeType: String?, localFileUrl: String?, downloadUrl: String?,
                downloadDate: Long, lastPlayedTimeHistory: Date?, playedDuration: Int,
                hasEmbeddedPicture: Boolean?, lastPlayedTimeStatistics: Long) : this(id, item, duration,
        position, size, mimeType, localFileUrl, downloadUrl, downloadDate,
        lastPlayedTimeHistory, playedDuration, lastPlayedTimeStatistics) {
        this.hasEmbeddedPicture = hasEmbeddedPicture
    }

    fun getHumanReadableIdentifier(): String? {
        if (item != null && item!!.getTitle() != null) {
            return item!!.getTitle()
        } else {
            return downloadUrl
        }
    }

    /**
     * Returns a MediaItem representing the FeedMedia object.
     * This is used by the MediaBrowserService
     */
    fun getMediaItem(): MediaBrowserCompat.MediaItem {
        val p: Playable = this
        val builder = MediaDescriptionCompat.Builder()
                .setMediaId(java.lang.String.valueOf(id))
                .setTitle(p.getEpisodeTitle())
                .setDescription(p.getFeedTitle())
                .setSubtitle(p.getFeedTitle())
        if (item != null) {
            // getImageLocation() also loads embedded images, which we can not send to external devices
            if (item!!.getImageUrl() != null) {
                builder.setIconUri(Uri.parse(item!!.getImageUrl()))
            } else if (item!!.getFeed() != null && item!!.getFeed()!!.getImageUrl() != null) {
                builder.setIconUri(Uri.parse(item!!.getFeed()!!.getImageUrl()))
            }
        }
        return MediaBrowserCompat.MediaItem(builder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    /**
     * Uses mimetype to determine the type of media.
     */
    override fun getMediaType(): MediaType {
        return MediaType.fromMimeType(mimeType)
    }

    fun updateFromOther(other: FeedMedia) {
        this.downloadUrl = other.downloadUrl
        if (other.size > 0) {
            size = other.size
        }
        if (other.duration > 0 && duration <= 0) { // Do not overwrite duration that we measured after downloading
            duration = other.duration
        }
        if (other.mimeType != null) {
            mimeType = other.mimeType
        }
    }

    /**
     * Compare's this FeedFile's attribute values with another FeedFile's
     * attribute values. This method will only compare attributes which were
     * read from the feed.
     *
     * @return true if attribute values are different, false otherwise
     */
    fun compareWithOther(other: FeedMedia): Boolean {
        if (!StringUtils.equals(downloadUrl, other.downloadUrl)) {
            return true
        }
        if (other.mimeType != null) {
            if (mimeType == null || mimeType != other.mimeType) {
                return true
            }
        }
        if (other.size > 0 && other.size != size) {
            return true
        }
        if (other.duration > 0 && duration <= 0) {
            return true
        }
        return false
    }

    override fun getDuration(): Int {
        return duration
    }

    override fun setDuration(duration: Int) {
        this.duration = duration
    }

    override fun setLastPlayedTimeStatistics(lastPlayedTimeStatistics: Long) {
        this.lastPlayedTimeStatistics = lastPlayedTimeStatistics
    }

    fun getPlayedDuration(): Int {
        return playedDuration
    }

    fun getPlayedDurationWhenStarted(): Int {
        return playedDurationWhenStarted
    }

    fun setPlayedDuration(playedDuration: Int) {
        this.playedDuration = playedDuration
    }

    override fun getPosition(): Int {
        return position
    }

    override fun getLastPlayedTimeStatistics(): Long {
        return lastPlayedTimeStatistics
    }

    override fun setPosition(position: Int) {
        this.position = position
        if (position > 0 && item != null && item!!.isNew()) {
            this.item!!.setPlayed(false)
        }
    }

    fun getSize(): Long {
        return size
    }

    fun setSize(size: Long) {
        this.size = size
    }

    override fun getDescription(): String? {
        if (item != null) {
            return item!!.getDescription()
        }
        return null
    }

    /**
     * Indicates we asked the service what the size was, but didn't
     * get a valid answer and we shoudln't check using the network again.
     */
    fun setCheckedOnSizeButUnknown() {
        this.size = CHECKED_ON_SIZE_BUT_UNKNOWN.toLong()
    }

    fun checkedOnSizeButUnknown(): Boolean {
        return (CHECKED_ON_SIZE_BUT_UNKNOWN.toLong() == this.size)
    }

    fun getMimeType(): String? {
        return mimeType
    }

    fun getItem(): FeedItem? {
        return item
    }

    /**
     * Sets the item object of this FeedMedia. If the given
     * FeedItem object is not null, it's 'media'-attribute value
     * will also be set to this media object.
     */
    fun setItem(item: FeedItem?) {
        this.item = item
        this.itemID = if (item != null) item.getId() else 0
        if (item != null && item.getMedia() !== this) {
            item.setMedia(this)
        }
    }

    fun getLastPlayedTimeHistory(): Date? {
        return if (lastPlayedTimeHistory == null)
            null else lastPlayedTimeHistory!!.clone() as Date
    }

    fun setLastPlayedTimeHistory(lastPlayedTimeHistory: Date?) {
        this.lastPlayedTimeHistory = if (lastPlayedTimeHistory == null)
            null else lastPlayedTimeHistory.clone() as Date
    }

    fun isInProgress(): Boolean {
        return (this.position > 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    fun hasEmbeddedPicture(): Boolean {
        if (hasEmbeddedPicture == null) {
            checkEmbeddedPicture()
        }
        return hasEmbeddedPicture!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeLong(if (item != null) item!!.getId() else 0L)

        dest.writeInt(duration)
        dest.writeInt(position)
        dest.writeLong(size)
        dest.writeString(mimeType)
        dest.writeString(localFileUrl)
        dest.writeString(downloadUrl)
        dest.writeLong(downloadDate)
        dest.writeLong(if (lastPlayedTimeHistory != null) lastPlayedTimeHistory!!.time else 0)
        dest.writeInt(playedDuration)
        dest.writeLong(lastPlayedTimeStatistics)
    }

    override fun getEpisodeTitle(): String? {
        if (item == null) {
            return null
        }
        if (item!!.getTitle() != null) {
            return item!!.getTitle()
        } else {
            return item!!.getIdentifyingValue()
        }
    }

    override fun getChapters(): List<Chapter>? {
        if (item == null) {
            return null
        }
        return item!!.getChapters()
    }

    override fun getWebsiteLink(): String? {
        if (item == null) {
            return null
        }
        return item!!.getLink()
    }

    override fun getFeedTitle(): String? {
        if (item == null || item!!.getFeed() == null) {
            return null
        }
        return item!!.getFeed()!!.getTitle()
    }

    override fun getIdentifier(): Any? {
        return id
    }

    override fun getLocalFileUrl(): String? {
        return localFileUrl
    }

    override fun getStreamUrl(): String? {
        return downloadUrl
    }

    fun getDownloadUrl(): String? {
        return downloadUrl
    }

    fun getStartPosition(): Int {
        return startPosition
    }

    override fun getPubDate(): Date? {
        if (item == null) {
            return null
        }
        if (item!!.getPubDate() != null) {
            return item!!.getPubDate()
        } else {
            return null
        }
    }

    override fun localFileAvailable(): Boolean {
        return isDownloaded() && localFileUrl != null
    }

    fun fileExists(): Boolean {
        if (localFileUrl == null) {
            return false
        } else {
            val f = File(localFileUrl)
            return f.exists()
        }
    }

    fun getId(): Long {
        return id
    }

    fun setId(id: Long) {
        this.id = id
    }

    fun isDownloaded(): Boolean {
        return downloadDate > 0 || (item != null && item!!.getFeed() != null && item!!.getFeed()!!.isLocalFeed())
    }

    fun getItemId(): Long {
        return itemID
    }

    fun setItemId(id: Long) {
        itemID = id
    }

    override fun onPlaybackStart() {
        startPosition = Math.max(position, 0)
        playedDurationWhenStarted = playedDuration
    }

    override fun getPlayableType(): Int {
        return PLAYABLE_TYPE_FEEDMEDIA
    }

    override fun setChapters(chapters: List<Chapter>?) {
        if (item != null) {
            item!!.setChapters(chapters)
        }
    }

    override fun getImageLocation(): String? {
        if (item != null) {
            return item!!.getImageLocation()
        } else if (hasEmbeddedPicture()) {
            return FILENAME_PREFIX_EMBEDDED_COVER + getLocalFileUrl()
        } else {
            return null
        }
    }

    fun setHasEmbeddedPicture(hasEmbeddedPicture: Boolean?) {
        this.hasEmbeddedPicture = hasEmbeddedPicture
    }

    fun setDownloaded(downloaded: Boolean, `when`: Long) {
        this.downloadDate = if (downloaded) `when` else 0
        if (item != null && downloaded && item!!.isNew()) {
            item!!.setPlayed(false)
        }
    }

    fun getDownloadDate(): Long {
        return downloadDate
    }

    fun setLocalFileUrl(fileUrl: String?) {
        this.localFileUrl = fileUrl
        if (fileUrl == null) {
            downloadDate = 0
        }
    }

    fun checkEmbeddedPicture() {
        if (!localFileAvailable()) {
            hasEmbeddedPicture = java.lang.Boolean.FALSE
            return
        }
        MediaMetadataRetrieverCompat().use { mmr ->
            mmr.setDataSource(getLocalFileUrl())
            val image = mmr.embeddedPicture
            if (image != null) {
                hasEmbeddedPicture = java.lang.Boolean.TRUE
            } else {
                hasEmbeddedPicture = java.lang.Boolean.FALSE
            }
        }
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null) {
            return false
        }
        if (o is RemoteMedia) {
            return o.equals(this)
        }

        if (javaClass != o.javaClass) {
            return false
        }

        val feedMedia = o as FeedMedia
        return id == feedMedia.id
    }

    fun getTranscriptFileUrl(): String? {
        if (getLocalFileUrl() == null) {
            return null
        }
        return getLocalFileUrl() + ".transcript"
    }

    fun setTranscript(t: Transcript?) {
        if (item == null) {
            return
        }
        item!!.setTranscript(t)
    }

    fun getTranscript(): Transcript? {
        if (item == null) {
            return null
        }
        return item!!.getTranscript()
    }

    fun hasTranscript(): Boolean {
        if (item == null) {
            return false
        }
        return item!!.hasTranscript()
    }

    companion object {
        const val FEEDFILETYPE_FEEDMEDIA = 2
        const val PLAYABLE_TYPE_FEEDMEDIA = 1
        const val FILENAME_PREFIX_EMBEDDED_COVER = "metadata-retriever:"

        /**
         * Indicates we've checked on the size of the item via the network
         * and got an invalid response. Using Integer.MIN_VALUE because
         * 1) we'll still check on it in case it gets downloaded (it's <= 0)
         * 2) By default all FeedMedia have a size of 0 if we don't know it,
         *    so this won't conflict with existing practice.
         */
        private const val CHECKED_ON_SIZE_BUT_UNKNOWN = Int.MIN_VALUE

        @JvmField
        val CREATOR: Parcelable.Creator<FeedMedia> = object : Parcelable.Creator<FeedMedia> {
            override fun createFromParcel(source: Parcel): FeedMedia {
                val id = source.readLong()
                val itemID = source.readLong()
                val result = FeedMedia(id, null, source.readInt(), source.readInt(), source.readLong(),
                        source.readString(), source.readString(), source.readString(), source.readLong(),
                        Date(source.readLong()), source.readInt(), source.readLong())
                result.itemID = itemID
                return result
            }

            override fun newArray(size: Int) = arrayOfNulls<FeedMedia>(size)
        }
    }
}
