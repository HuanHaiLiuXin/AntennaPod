package de.danoeh.antennapod.playback.base

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.bumptech.glide.Glide
import com.google.common.collect.ImmutableList
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.net.common.HttpCredentialEncoder
import de.danoeh.antennapod.system.utils.ThreadUtils

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class MediaItemAdapter {
    companion object {
        private const val TAG = "MediaItemAdapter"
        const val MEDIA_ID_FEED_PREFIX = "FeedId:"
        const val MEDIA_ID_CONFIRM_STREAMING = "confirm_streaming"
        const val KEY_STREAM_URL = "stream_url"
        const val KEY_AUTHORIZATION_HEADER = "authorization_header"

        /**
         * Create a basic media item without attached metadata.
         * Should be used when initiating playback from outside the service.
         */
        @JvmStatic
        fun fromMediaIdStub(mediaId: Long): MediaItem {
            val metadataBuilder = MediaMetadata.Builder()
            metadataBuilder.setIsPlayable(true)
            metadataBuilder.setIsBrowsable(false)
            return MediaItem.Builder()
                    .setMediaId(mediaId.toString())
                    .setMediaMetadata(metadataBuilder.build())
                    .build()
        }

        /**
         * Create a media item and load all its metadata, including cover art using Glide.
         * Do NOT use this method on the main thread.
         */
        @JvmStatic
        fun fromPlayable(context: Context, playable: Playable, forBrowse: Boolean): MediaItem {
            ThreadUtils.assertNotMainThread()
            val metadataBuilder = MediaMetadata.Builder()
            metadataBuilder.setTitle(playable.getEpisodeTitle())
            metadataBuilder.setIsPlayable(true)
            metadataBuilder.setIsBrowsable(false)
            metadataBuilder.setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            var mediaId = "0"
            if (playable is FeedMedia) {
                val feedMedia = playable
                mediaId = feedMedia.getId().toString()
                metadataBuilder.setSubtitle(feedMedia.getFeedTitle())
                metadataBuilder.setArtist(feedMedia.getFeedTitle())
            }
            if (!forBrowse) {
                val iconSize = (128 * context.getResources().getDisplayMetrics().density).toInt()
                val bitmap = loadArtworkBitmap(context, playable, iconSize)
                if (bitmap != null) {
                    val bos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                    // media3 prefers artworkData over artworkUri for local playback.
                    // Chromecast ignores artworkData and needs artworkUri.
                    metadataBuilder.setArtworkData(bos.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
            if (playable.getImageLocation() != null && playable.getImageLocation()!!.startsWith("http")) {
                metadataBuilder.setArtworkUri(Uri.parse(playable.getImageLocation()))
            }
            val extras = Bundle()
            extras.putString(KEY_STREAM_URL, playable.getStreamUrl())
            metadataBuilder.setExtras(extras)
            val localPlaybackUri: String?
            if (playable.localFileAvailable()) {
                localPlaybackUri = playable.getLocalFileUrl()
            } else {
                localPlaybackUri = playable.getStreamUrl()
            }
            val requestExtras = Bundle()
            if (!playable.localFileAvailable() && playable is FeedMedia) {
                val feedMedia = playable
                if (feedMedia.getItem() != null && feedMedia.getItem()!!.getFeed() != null) {
                    val prefs = feedMedia.getItem()!!.getFeed()!!.getPreferences()
                    if (prefs != null && !TextUtils.isEmpty(prefs.getUsername())
                            && !TextUtils.isEmpty(prefs.getPassword())) {
                        requestExtras.putString(KEY_AUTHORIZATION_HEADER,
                                HttpCredentialEncoder.encode(prefs.getUsername(), prefs.getPassword(), "ISO-8859-1"))
                    }
                }
            }
            return MediaItem.Builder()
                    .setUri(if (localPlaybackUri != null) Uri.parse(localPlaybackUri) else null)
                    .setMediaId(mediaId)
                    .setMediaMetadata(metadataBuilder.build())
                    .setRequestMetadata(MediaItem.RequestMetadata.Builder()
                            .setExtras(requestExtras)
                            .build())
                    .build()
        }

        private fun loadArtworkBitmap(context: Context, playable: Playable, iconSize: Int): Bitmap? {
            try {
                val imageLocation = playable.getImageLocation()
                return Glide.with(context)
                        .asBitmap()
                        .onlyRetrieveFromCache(imageLocation != null && imageLocation.startsWith("http"))
                        .load(imageLocation)
                        .submit(iconSize, iconSize)
                        .get(500, TimeUnit.MILLISECONDS)
            } catch (tr1: Exception) {
                // fall through to try feed image
            }
            if (playable !is FeedMedia) {
                return null
            }
            val feedMedia = playable
            if (feedMedia.getItem() == null || feedMedia.getItem()!!.getFeed() == null) {
                return null
            }
            val fallback = feedMedia.getItem()!!.getFeed()!!.getImageUrl()
            if (fallback == null) {
                return null
            }
            try {
                return Glide.with(context)
                        .asBitmap()
                        .onlyRetrieveFromCache(fallback.startsWith("http"))
                        .load(fallback)
                        .submit(iconSize, iconSize)
                        .get(500, TimeUnit.MILLISECONDS)
            } catch (tr2: Exception) {
                Log.e(TAG, "Skipping to load artwork bitmap: " + tr2.message)
            }
            return null
        }

        @JvmStatic
        fun fromFeed(context: Context, feed: Feed): MediaItem {
            val metadataBuilder = MediaMetadata.Builder()
            metadataBuilder.setTitle(feed.getTitle())

            var bitmap: Bitmap? = null
            try {
                val iconSize = (128 * context.getResources().getDisplayMetrics().density).toInt()
                bitmap = Glide.with(context)
                        .asBitmap()
                        .onlyRetrieveFromCache(true)
                        .load(feed.getImageUrl())
                        .submit(iconSize, iconSize)
                        .get(500, TimeUnit.MILLISECONDS)
            } catch (exception: Exception) {
                Log.e(TAG, "Skipping to load artwork bitmap:" + exception.message)
            }
            if (bitmap != null) {
                val bos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos)
                metadataBuilder.setArtworkData(bos.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            } else if (feed.getImageUrl() != null && feed.getImageUrl()!!.startsWith("http")) {
                metadataBuilder.setArtworkUri(Uri.parse(feed.getImageUrl()))
            }
            metadataBuilder.setSubtitle(feed.getAuthor())
            metadataBuilder.setIsBrowsable(true)
            metadataBuilder.setIsPlayable(false)
            return MediaItem.Builder()
                    .setMediaId(MEDIA_ID_FEED_PREFIX + feed.getId())
                    .setMediaMetadata(metadataBuilder.build())
                    .build()
        }

        @JvmStatic
        fun from(context: Context, id: String, title: String,
                 iconResId: Int, subtitle: String?): MediaItem {
            val iconUri = Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(context.getResources().getResourcePackageName(iconResId))
                    .appendPath(context.getResources().getResourceTypeName(iconResId))
                    .appendPath(context.getResources().getResourceEntryName(iconResId))
                    .build()

            val metadataBuilder = MediaMetadata.Builder()
            metadataBuilder.setTitle(title)
            metadataBuilder.setArtworkUri(iconUri)
            if (subtitle != null) {
                metadataBuilder.setSubtitle(subtitle)
            }
            metadataBuilder.setIsBrowsable(true)
            metadataBuilder.setIsPlayable(false)
            return MediaItem.Builder()
                    .setMediaId(id)
                    .setMediaMetadata(metadataBuilder.build())
                    .build()
        }

        @JvmStatic
        fun buildStreamingConfirmationItem(context: Context,
                                           audioResId: Int,
                                           title: String, description: String): MediaItem {
            val uri = Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(context.getResources().getResourcePackageName(audioResId))
                    .appendPath(context.getResources().getResourceTypeName(audioResId))
                    .appendPath(context.getResources().getResourceEntryName(audioResId))
                    .build()
            val metadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .setDescription(description)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            return MediaItem.Builder()
                    .setMediaId(MEDIA_ID_CONFIRM_STREAMING)
                    .setUri(uri)
                    .setMediaMetadata(metadata)
                    .build()
        }

        @JvmStatic
        fun fromItemList(context: Context, feedItems: List<FeedItem>): ImmutableList<MediaItem> {
            val itemsBuilder = ImmutableList.builder<MediaItem>()
            for (item in feedItems) {
                val media = item.getMedia()
                if (media != null && (media.localFileAvailable() || media.getStreamUrl() != null)) {
                    itemsBuilder.add(fromPlayable(context, media, true))
                }
            }
            return itemsBuilder.build()
        }
    }
}
