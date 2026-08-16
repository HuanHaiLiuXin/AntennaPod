package de.danoeh.antennapod.model.feed

import android.text.TextUtils
import de.danoeh.antennapod.model.playback.Playable
import java.util.regex.Pattern

class EmbeddedChapterImage {
    private val position: Int
    private val length: Int
    private val imageUrl: String
    private val media: Playable

    constructor(media: Playable, imageUrl: String) {
        this.media = media
        this.imageUrl = imageUrl
        val m = EMBEDDED_IMAGE_MATCHER.matcher(imageUrl)
        if (m.find()) {
            this.position = Integer.parseInt(m.group(1))
            this.length = Integer.parseInt(m.group(2))
        } else {
            throw IllegalArgumentException("Not an embedded chapter")
        }
    }

    fun getPosition(): Int {
        return position
    }

    fun getLength(): Int {
        return length
    }

    fun getMedia(): Playable {
        return media
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as EmbeddedChapterImage
        return TextUtils.equals(imageUrl, that.imageUrl)
    }

    override fun hashCode(): Int {
        return imageUrl.hashCode()
    }

    companion object {
        private val EMBEDDED_IMAGE_MATCHER = Pattern.compile("embedded-image://(\\d+)/(\\d+)")

        @JvmStatic
        fun makeUrl(position: Int, length: Int): String {
            return "embedded-image://" + position + "/" + length
        }

        private fun isEmbeddedChapterImage(imageUrl: String?): Boolean {
            return EMBEDDED_IMAGE_MATCHER.matcher(imageUrl).matches()
        }

        @JvmStatic
        fun getModelFor(media: Playable, chapter: Int): Any? {
            val imageUrl = media.getChapters()!![chapter].getImageUrl()
            return if (isEmbeddedChapterImage(imageUrl)) {
                EmbeddedChapterImage(media, imageUrl!!)
            } else {
                imageUrl
            }
        }
    }
}
