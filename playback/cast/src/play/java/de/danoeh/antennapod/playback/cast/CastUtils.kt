package de.danoeh.antennapod.playback.cast

import android.content.ContentResolver
import android.util.Log
import android.text.TextUtils
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastSession
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.model.playback.RemoteMedia

/**
 * Helper functions for Cast support.
 */
class CastUtils {
    companion object {
        private const val TAG = "CastUtils"

        const val KEY_MEDIA_ID = "de.danoeh.antennapod.core.cast.MediaId"

        const val KEY_EPISODE_IDENTIFIER = "de.danoeh.antennapod.core.cast.EpisodeId"
        const val KEY_EPISODE_LINK = "de.danoeh.antennapod.core.cast.EpisodeLink"
        const val KEY_STREAM_URL = "de.danoeh.antennapod.core.cast.StreamUrl"
        const val KEY_FEED_URL = "de.danoeh.antennapod.core.cast.FeedUrl"
        const val KEY_FEED_WEBSITE = "de.danoeh.antennapod.core.cast.FeedWebsite"
        const val KEY_EPISODE_NOTES = "de.danoeh.antennapod.core.cast.EpisodeNotes"

        /**
         * The field <code>AntennaPod.FormatVersion</code> specifies which version of MediaMetaData
         * fields we're using. Future implementations should try to be backwards compatible with earlier
         * versions, and earlier versions should be forward compatible until the version indicated by
         * <code>MAX_VERSION_FORWARD_COMPATIBILITY</code>. If an update makes the format unreadable for
         * an earlier version, then its version number should be greater than the
         * <code>MAX_VERSION_FORWARD_COMPATIBILITY</code> value set on the earlier one, so that it
         * doesn't try to parse the object.
         */
        const val KEY_FORMAT_VERSION = "de.danoeh.antennapod.core.cast.FormatVersion"
        const val FORMAT_VERSION_VALUE = 1
        const val MAX_VERSION_FORWARD_COMPATIBILITY = 9999

        @JvmStatic
        fun isCastable(media: Playable?, castSession: CastSession?): Boolean {
            if (media == null || castSession == null || castSession.getCastDevice() == null) {
                return false
            }
            if (media is FeedMedia || media is RemoteMedia) {
                val url = media.getStreamUrl()
                if (url == null || url.isEmpty()) {
                    return false
                }
                if (url.startsWith(ContentResolver.SCHEME_CONTENT)) {
                    return false // Local feed
                }
                when (media.getMediaType()) {
                    MediaType.AUDIO -> return castSession.getCastDevice()!!.hasCapability(CastDevice.CAPABILITY_AUDIO_OUT)
                    MediaType.VIDEO -> return castSession.getCastDevice()!!.hasCapability(CastDevice.CAPABILITY_VIDEO_OUT)
                    else -> return false
                }
            }
            return false
        }

        /**
         * Converts [MediaInfo] objects into the appropriate implementation of [Playable].
         * @return [Playable] object in a format proper for casting.
         */
        @JvmStatic
        fun makeRemoteMedia(media: MediaInfo): Playable? {
            val metadata = media.getMetadata()!!
            val version = metadata.getInt(KEY_FORMAT_VERSION)
            if (version <= 0 || version > MAX_VERSION_FORWARD_COMPATIBILITY) {
                Log.w(TAG, "MediaInfo object obtained from the cast device is not compatible with this" +
                        "version of AntennaPod CastUtils, curVer=" + FORMAT_VERSION_VALUE +
                        ", object version=" + version)
                return null
            }
            val imageList = metadata.getImages()
            var imageUrl: String? = null
            if (!imageList.isEmpty()) {
                imageUrl = imageList[0].getUrl().toString()
            }
            val notes = metadata.getString(KEY_EPISODE_NOTES)
            val result = RemoteMedia(media.getContentId(),
                    metadata.getString(KEY_EPISODE_IDENTIFIER),
                    metadata.getString(KEY_FEED_URL),
                    metadata.getString(MediaMetadata.KEY_SUBTITLE),
                    metadata.getString(MediaMetadata.KEY_TITLE),
                    metadata.getString(KEY_EPISODE_LINK),
                    metadata.getString(MediaMetadata.KEY_ARTIST),
                    imageUrl,
                    metadata.getString(KEY_FEED_WEBSITE),
                    media.getContentType(),
                    metadata.getDate(MediaMetadata.KEY_RELEASE_DATE)!!.getTime(),
                    notes)
            if (result.getDuration() == 0 && media.getStreamDuration() > 0) {
                result.setDuration(media.getStreamDuration().toInt())
            }
            return result
        }

        /**
         * Compares a [MediaInfo] instance with a [FeedMedia] one and evaluates whether they
         * represent the same podcast episode.
         *
         * @param info      the [MediaInfo] object to be compared.
         * @param media     the [FeedMedia] object to be compared.
         * @return <true>true</true> if there's a match, <code>false</code> otherwise.
         *
         * @see RemoteMedia.equals
         */
        @JvmStatic
        fun matches(info: MediaInfo?, media: FeedMedia?): Boolean {
            if (info == null || media == null) {
                return false
            }
            if (!TextUtils.equals(info.getContentId(), media.getStreamUrl())) {
                return false
            }
            val metadata = info.getMetadata()
            val fi = media.getItem()
            if (fi == null || metadata == null
                    || !TextUtils.equals(metadata.getString(KEY_EPISODE_IDENTIFIER), fi.getItemIdentifier())) {
                return false
            }
            val feed = fi.getFeed()
            return feed != null && TextUtils.equals(metadata.getString(KEY_FEED_URL), feed.getDownloadUrl())
        }

        /**
         * Compares a [MediaInfo] instance with a [RemoteMedia] one and evaluates whether they
         * represent the same podcast episode.
         *
         * @param info      the [MediaInfo] object to be compared.
         * @param media     the [RemoteMedia] object to be compared.
         * @return <true>true</true> if there's a match, <code>false</code> otherwise.
         *
         * @see RemoteMedia.equals
         */
        @JvmStatic
        fun matches(info: MediaInfo?, media: RemoteMedia?): Boolean {
            if (info == null || media == null) {
                return false
            }
            if (!TextUtils.equals(info.getContentId(), media.getStreamUrl())) {
                return false
            }
            val metadata = info.getMetadata()
            return metadata != null
                    && TextUtils.equals(metadata.getString(KEY_EPISODE_IDENTIFIER), media.getEpisodeIdentifier())
                    && TextUtils.equals(metadata.getString(KEY_FEED_URL), media.getFeedUrl())
        }

        /**
         * Compares a [MediaInfo] instance with a [Playable] and evaluates whether they
         * represent the same podcast episode. Useful every time we get a MediaInfo from the Cast Device
         * and want to avoid unnecessary conversions.
         *
         * @param info      the [MediaInfo] object to be compared.
         * @param media     the [Playable] object to be compared.
         * @return <true>true</true> if there's a match, <code>false</code> otherwise.
         *
         * @see RemoteMedia.equals
         */
        @JvmStatic
        fun matches(info: MediaInfo?, media: Playable?): Boolean {
            if (info == null || media == null) {
                return false
            }
            if (media is RemoteMedia) {
                return matches(info, media)
            }
            return media is FeedMedia && matches(info, media)
        }
    }
}
