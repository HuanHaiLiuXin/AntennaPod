package de.danoeh.antennapod.parser.feed.namespace

import android.text.TextUtils
import android.util.Log

import de.danoeh.antennapod.parser.feed.HandlerState
import de.danoeh.antennapod.parser.feed.element.SyndElement
import org.xml.sax.Attributes

import java.util.concurrent.TimeUnit

import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.parser.feed.element.AtomText
import de.danoeh.antennapod.parser.feed.util.MimeTypeUtils

/** Processes tags from the http://search.yahoo.com/mrss/ namespace. */
class Media : Namespace() {

    override fun handleElementStart(localName: String, state: HandlerState,
                                    attributes: Attributes): SyndElement {
        if (CONTENT == localName && state.getCurrentItem() != null) {
            val url = attributes.getValue(DOWNLOAD_URL)
            val defaultStr = attributes.getValue(DEFAULT)
            val medium = attributes.getValue(MEDIUM)
            var validTypeMedia = false
            var validTypeImage = false
            val isDefault = defaultStr == "true"
            var mimeType = MimeTypeUtils.getMimeType(attributes.getValue(MIME_TYPE), url)

            if (MEDIUM_AUDIO == medium) {
                validTypeMedia = true
                mimeType = "audio/*"
            } else if (MEDIUM_VIDEO == medium) {
                validTypeMedia = true
                mimeType = "video/*"
            } else if (MEDIUM_IMAGE == medium && (mimeType == null
                        || (!mimeType.startsWith("audio/") && !mimeType.startsWith("video/")))) {
                // Apparently, some publishers explicitly specify the audio file as an image
                validTypeImage = true
                mimeType = "image/*"
            } else if (MimeTypeUtils.isMediaFile(mimeType)) {
                validTypeMedia = true
            } else if (MimeTypeUtils.isImageFile(mimeType)) {
                validTypeImage = true
            } else {
                // Workaround for broken feeds
                validTypeMedia = state.getCurrentItem()!!.getMedia() == null
                mimeType = "audio/*"
            }

            if ((state.getCurrentItem()!!.getMedia() == null || isDefault) && url != null && validTypeMedia) {
                var size = 0L
                val sizeStr = attributes.getValue(SIZE)
                if (!TextUtils.isEmpty(sizeStr)) {
                    try {
                        size = java.lang.Long.parseLong(sizeStr)
                    } catch (e: NumberFormatException) {
                        Log.e(TAG, "Size \"" + sizeStr + "\" could not be parsed.")
                    }
                }

                var durationMs = 0
                val durationStr = attributes.getValue(DURATION)
                if (!TextUtils.isEmpty(durationStr)) {
                    try {
                        val duration = java.lang.Long.parseLong(durationStr)
                        durationMs = TimeUnit.MILLISECONDS.convert(duration, TimeUnit.SECONDS).toInt()
                    } catch (e: NumberFormatException) {
                        Log.e(TAG, "Duration \"" + durationStr + "\" could not be parsed")
                    }
                }
                val media = FeedMedia(state.getCurrentItem(), url, size, mimeType)
                if (durationMs > 0) {
                    media.setDuration(durationMs)
                }
                state.getCurrentItem()!!.setMedia(media)
            } else if (state.getCurrentItem() != null && url != null && validTypeImage) {
                state.getCurrentItem()!!.setImageUrl(url)
            }
        } else if (IMAGE == localName) {
            val url = attributes.getValue(IMAGE_URL)
            if (url != null) {
                if (state.getCurrentItem() != null) {
                    state.getCurrentItem()!!.setImageUrl(url)
                } else {
                    if (state.getFeed().getImageUrl() == null) {
                        state.getFeed().setImageUrl(url)
                    }
                }
            }
        } else if (DESCRIPTION == localName) {
            val type = attributes.getValue(DESCRIPTION_TYPE)
            return AtomText(localName, this, type)
        }
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
        if (DESCRIPTION == localName) {
            val content = state.getContentBuf().toString()
            if (state.getCurrentItem() != null) {
                state.getCurrentItem()!!.setDescriptionIfLonger(content)
            }
        }
    }

    companion object {
        private const val TAG = "NSMedia"

        const val NSTAG = "media"
        const val NSURI = "http://search.yahoo.com/mrss/"

        private const val CONTENT = "content"
        private const val DOWNLOAD_URL = "url"
        private const val SIZE = "fileSize"
        private const val MIME_TYPE = "type"
        private const val DURATION = "duration"
        private const val DEFAULT = "isDefault"
        private const val MEDIUM = "medium"

        private const val MEDIUM_IMAGE = "image"
        private const val MEDIUM_AUDIO = "audio"
        private const val MEDIUM_VIDEO = "video"

        private const val IMAGE = "thumbnail"
        private const val IMAGE_URL = "url"

        private const val DESCRIPTION = "description"
        private const val DESCRIPTION_TYPE = "type"
    }
}
