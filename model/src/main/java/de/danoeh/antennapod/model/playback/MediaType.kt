package de.danoeh.antennapod.model.playback

import android.text.TextUtils

import java.util.Arrays
import java.util.HashSet
import java.util.Set

enum class MediaType {
    AUDIO, VIDEO, UNKNOWN;

    companion object {
        private val AUDIO_APPLICATION_MIME_STRINGS = HashSet(Arrays.asList(
                "application/ogg",
                "application/opus",
                "application/x-flac"
        ))

        @JvmStatic
        fun fromMimeType(mimeType: String?): MediaType {
            if (TextUtils.isEmpty(mimeType)) {
                return MediaType.UNKNOWN
            } else if (mimeType!!.startsWith("audio")) {
                return MediaType.AUDIO
            } else if (mimeType.startsWith("video")) {
                return MediaType.VIDEO
            } else if (AUDIO_APPLICATION_MIME_STRINGS.contains(mimeType)) {
                return MediaType.AUDIO
            }
            return MediaType.UNKNOWN
        }
    }
}
