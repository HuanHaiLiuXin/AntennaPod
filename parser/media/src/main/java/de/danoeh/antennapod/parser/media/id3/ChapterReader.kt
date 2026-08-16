package de.danoeh.antennapod.parser.media.id3

import android.text.TextUtils
import android.util.Log
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.EmbeddedChapterImage
import de.danoeh.antennapod.parser.media.id3.model.FrameHeader
import org.apache.commons.io.input.CountingInputStream

import java.net.URLDecoder
import java.util.ArrayList

/**
 * Reads ID3 chapters.
 * See https://id3.org/id3v2-chapters-1.0
 */
class ChapterReader(input: CountingInputStream) : ID3Reader(input) {
    private val chapters = ArrayList<Chapter>()

    override fun readFrame(frameHeader: FrameHeader) {
        if (FRAME_ID_CHAPTER == frameHeader.getId()) {
            Log.d(TAG, "Handling frame: " + frameHeader.toString())
            val chapter = readChapter(frameHeader)
            Log.d(TAG, "Chapter done: " + chapter)
            chapters.add(chapter)
        } else {
            super.readFrame(frameHeader)
        }
    }

    fun readChapter(frameHeader: FrameHeader): Chapter {
        val chapterStartedPosition = getPosition()
        val elementId = readIsoStringNullTerminated(100)
        val startTime = readInt().toLong()
        skipBytes(12) // Ignore end time, start offset, end offset

        val chapter = Chapter()
        chapter.setStart(startTime)
        chapter.setChapterId(elementId)

        // Read sub-frames
        while (getPosition() < chapterStartedPosition + frameHeader.getSize()) {
            val subFrameHeader = readFrameHeader()
            readChapterSubFrame(subFrameHeader, chapter)
        }
        return chapter
    }

    fun readChapterSubFrame(frameHeader: FrameHeader, chapter: Chapter) {
        Log.d(TAG, "Handling subframe: " + frameHeader.toString())
        val frameStartPosition = getPosition()
        when (frameHeader.getId()) {
            FRAME_ID_TITLE -> {
                chapter.setTitle(readEncodingAndString(frameHeader.getSize()))
                Log.d(TAG, "Found title: " + chapter.getTitle())
            }
            FRAME_ID_LINK -> {
                readEncodingAndString(frameHeader.getSize()) // skip description
                val url = readIsoStringNullTerminated(frameStartPosition + frameHeader.getSize() - getPosition())
                try {
                    val decodedLink = URLDecoder.decode(url, "ISO-8859-1")
                    chapter.setLink(decodedLink)
                    Log.d(TAG, "Found link: " + chapter.getLink())
                } catch (iae: IllegalArgumentException) {
                    Log.w(TAG, "Bad URL found in ID3 data")
                }
            }
            FRAME_ID_PICTURE -> {
                val encoding = readByte()
                val mime = readIsoStringNullTerminated(frameHeader.getSize())
                val type = readByte()
                val description = readEncodedString(encoding.toInt(), frameHeader.getSize())
                Log.d(TAG, "Found apic: " + mime + "," + description)
                if (MIME_IMAGE_URL == mime) {
                    val link = readIsoStringNullTerminated(frameHeader.getSize())
                    Log.d(TAG, "Link: " + link)
                    if (TextUtils.isEmpty(chapter.getImageUrl()) || type.toInt() == IMAGE_TYPE_COVER) {
                        chapter.setImageUrl(link)
                    }
                } else {
                    val alreadyConsumed = getPosition() - frameStartPosition
                    val rawImageDataLength = frameHeader.getSize() - alreadyConsumed
                    if (TextUtils.isEmpty(chapter.getImageUrl()) || type.toInt() == IMAGE_TYPE_COVER) {
                        chapter.setImageUrl(EmbeddedChapterImage.makeUrl(getPosition(), rawImageDataLength))
                    }
                }
            }
            else -> {
                Log.d(TAG, "Unknown chapter sub-frame.")
            }
        }

        // Skip garbage to fill frame completely
        // This also asserts that we are not reading too many bytes from this frame.
        val alreadyConsumed = getPosition() - frameStartPosition
        skipBytes(frameHeader.getSize() - alreadyConsumed)
    }

    fun getChapters(): List<Chapter> {
        return chapters
    }

    companion object {
        private const val TAG = "ID3ChapterReader"

        const val FRAME_ID_CHAPTER = "CHAP"
        const val FRAME_ID_TITLE = "TIT2"
        const val FRAME_ID_LINK = "WXXX"
        const val FRAME_ID_PICTURE = "APIC"
        const val MIME_IMAGE_URL = "-->"
        const val IMAGE_TYPE_COVER = 3
    }
}
