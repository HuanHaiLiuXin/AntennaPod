package de.danoeh.antennapod.parser.media.vorbis

import android.util.Log

import java.io.InputStream
import java.util.ArrayList
import java.util.concurrent.TimeUnit

import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.parser.media.BuildConfig

class VorbisCommentChapterReader(input: InputStream) : VorbisCommentReader(input) {
    private val chapters = ArrayList<Chapter>()

    override fun handles(key: String): Boolean {
        return key.matches(Regex(CHAPTER_KEY))
    }

    override fun onContentVectorValue(key: String, value: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Key: " + key + ", value: " + value)
        }
        val attribute = getAttributeTypeFromKey(key)
        val id = getIdFromKey(key)
        var chapter = getChapterById(id)
        if (attribute == null) {
            if (getChapterById(id) == null) {
                // new chapter
                val start = getStartTimeFromValue(value)
                chapter = Chapter()
                chapter.setChapterId("" + id)
                chapter.setStart(start)
                chapters.add(chapter)
            } else {
                throw VorbisCommentReaderException("Found chapter with duplicate ID (" + key + ", " + value + ")")
            }
        } else if (attribute == CHAPTER_ATTRIBUTE_TITLE) {
            if (chapter != null) {
                chapter.setTitle(value)
            }
        } else if (attribute == CHAPTER_ATTRIBUTE_LINK) {
            if (chapter != null) {
                chapter.setLink(value)
            }
        }
    }

    private fun getChapterById(id: Int): Chapter? {
        for (c in chapters) {
            if (("" + id) == c.getChapterId()) {
                return c
            }
        }
        return null
    }

    fun getChapters(): List<Chapter> {
        return chapters
    }

    companion object {
        private const val TAG = "VorbisCommentChptrReadr"

        private const val CHAPTER_KEY = "chapter\\d\\d\\d.*"
        private const val CHAPTER_ATTRIBUTE_TITLE = "name"
        private const val CHAPTER_ATTRIBUTE_LINK = "url"
        private const val CHAPTERXXX_LENGTH = "chapterxxx".length

        @JvmStatic
        fun getStartTimeFromValue(value: String): Long {
            val parts = value.split(":").toTypedArray()
            if (parts.size >= 3) {
                try {
                    val hours = TimeUnit.MILLISECONDS.convert(
                            java.lang.Long.parseLong(parts[0]), TimeUnit.HOURS)
                    val minutes = TimeUnit.MILLISECONDS.convert(
                            java.lang.Long.parseLong(parts[1]), TimeUnit.MINUTES)
                    if (parts[2].contains("-->")) {
                        parts[2] = parts[2].substring(0, parts[2].indexOf("-->"))
                    }
                    val seconds = TimeUnit.MILLISECONDS.convert(
                            java.lang.Float.parseFloat(parts[2]).toLong(), TimeUnit.SECONDS)
                    return hours + minutes + seconds
                } catch (e: NumberFormatException) {
                    throw VorbisCommentReaderException(e)
                }
            } else {
                throw VorbisCommentReaderException("Invalid time string")
            }
        }

        /**
         * Return the id of a vorbiscomment chapter from a string like CHAPTERxxx*
         *
         * @return the id of the chapter key or -1 if the id couldn't be read.
         * */
        private fun getIdFromKey(key: String): Int {
            if (key.length >= CHAPTERXXX_LENGTH) { // >= CHAPTERxxx
                try {
                    val strId = key.substring(8, 10)
                    return Integer.parseInt(strId)
                } catch (e: NumberFormatException) {
                    throw VorbisCommentReaderException(e)
                }
            }
            throw VorbisCommentReaderException("key is too short (" + key + ")")
        }

        /**
         * Get the string that comes after 'CHAPTERxxx', for example 'name' or
         * 'url'.
         */
        private fun getAttributeTypeFromKey(key: String): String? {
            if (key.length > CHAPTERXXX_LENGTH) {
                return key.substring(CHAPTERXXX_LENGTH)
            }
            return null
        }
    }
}
