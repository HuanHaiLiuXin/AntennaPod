package de.danoeh.antennapod.playback.cast

import android.content.Context
import android.os.Bundle
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import de.danoeh.antennapod.playback.base.MediaItemAdapter

class CastPlayerWrapper {
    companion object {
        private const val KEY_MEDIA_ID = "media_id"
        private const val KEY_LOCAL_FILE_URL = "local_file_url"

        @OptIn(UnstableApi::class)
        @JvmStatic
        fun wrap(player: Player, context: Context): Player {
            val remotePlayer = RemoteCastPlayer.Builder(context)
                    .setMediaItemConverter(ApMediaItemConverter())
                    .build()
            return CastPlayer.Builder(context)
                    .setLocalPlayer(player)
                    .setRemotePlayer(remotePlayer)
                    .build()
        }

        @JvmStatic
        fun hasPlaybackJustFinished(context: Context): Boolean {
            // When Cast finishes, it unloads the media session, so the Media3 CastPlayer reports IDLE, not ENDED.
            // Media3 never reads the Cast SDK's idle reason, so "finished naturally" and "stopped by user" look identical.
            // This method reads the idle reason directly from the Cast SDK to make that distinction.
            // It only gives a meaningful result while the idle status is still current, i.e. when called synchronously
            // from a Player listener callback reacting to the unload.
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
                return false
            }
            try {
                val castSession: CastSession? = CastContext.getSharedInstance(context)
                        .getSessionManager().getCurrentCastSession()
                if (castSession == null) {
                    return false
                }
                val remoteMediaClient: RemoteMediaClient? = castSession.getRemoteMediaClient()
                return remoteMediaClient != null
                        && remoteMediaClient.getPlayerState() == MediaStatus.PLAYER_STATE_IDLE
                        && remoteMediaClient.getIdleReason() == MediaStatus.IDLE_REASON_FINISHED
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
    }

    /**
     * Alias name to avoid specifying the full package import due to the MediaMetadata name collision
     */
    private class CastMediaMetadata : com.google.android.gms.cast.MediaMetadata {
        constructor(type: Int) : super(type) {
        }
    }

    @UnstableApi
    class ApMediaItemConverter : MediaItemConverter {
        override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
            val metadata = CastMediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_GENERIC)
            if (mediaItem.mediaMetadata.title != null) {
                metadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE,
                        mediaItem.mediaMetadata.title.toString())
            }
            if (mediaItem.mediaMetadata.subtitle != null) {
                metadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE,
                        mediaItem.mediaMetadata.subtitle.toString())
            }
            val artworkUri = mediaItem.mediaMetadata.artworkUri
            if (artworkUri != null) {
                metadata.addImage(WebImage(artworkUri))
            }
            if (!mediaItem.mediaId.isEmpty()) {
                metadata.putString(KEY_MEDIA_ID, mediaItem.mediaId)
            }
            if (mediaItem.localConfiguration != null) {
                metadata.putString(KEY_LOCAL_FILE_URL, mediaItem.localConfiguration!!.uri.toString())
            }
            var streamUrl: String? = null
            if (mediaItem.mediaMetadata.extras != null) {
                streamUrl = mediaItem.mediaMetadata.extras!!.getString(MediaItemAdapter.KEY_STREAM_URL)
            }
            val mediaInfo = MediaInfo.Builder(if (streamUrl != null) streamUrl else "")
                    .setMetadata(metadata)
                    .build()
            return MediaQueueItem.Builder(mediaInfo).build()
        }

        override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
            val mediaInfo = mediaQueueItem.getMedia()
            val metadataBuilder = MediaMetadata.Builder()
            val builder = MediaItem.Builder()

            if (mediaInfo == null) {
                return builder.build()
            }
            if (mediaInfo.getContentUrl() != null) {
                builder.setUri(mediaInfo.getContentUrl())
                val extras = Bundle()
                extras.putString(MediaItemAdapter.KEY_STREAM_URL, mediaInfo.getContentUrl())
                metadataBuilder.setExtras(extras)
            }
            val castMetadata = mediaInfo.getMetadata()
            if (castMetadata == null) {
                return builder.build()
            }
            if (castMetadata.containsKey(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)) {
                metadataBuilder.setTitle(castMetadata.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE))
            }
            if (castMetadata.containsKey(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE)) {
                metadataBuilder.setSubtitle(castMetadata.getString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE))
            }
            if (!castMetadata.getImages().isEmpty()) {
                metadataBuilder.setArtworkUri(castMetadata.getImages()[0].getUrl())
            }
            if (castMetadata.containsKey(KEY_MEDIA_ID)) {
                builder.setMediaId(castMetadata.getString(KEY_MEDIA_ID)!!)
            }
            if (castMetadata.containsKey(KEY_LOCAL_FILE_URL)) {
                builder.setUri(castMetadata.getString(KEY_LOCAL_FILE_URL)!!)
            }
            builder.setMediaMetadata(metadataBuilder.build())
            return builder.build()
        }
    }
}
