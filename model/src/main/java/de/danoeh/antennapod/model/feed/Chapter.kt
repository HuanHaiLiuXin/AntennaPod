package de.danoeh.antennapod.model.feed

import java.util.Objects

class Chapter {
    private var id: Long = 0
    /** The start time of the chapter in milliseconds */
    private var start: Long = 0
    private var title: String? = null
    private var link: String? = null
    private var imageUrl: String? = null
    private var chapterId: String? = null

    constructor() {
    }

    constructor(start: Long, title: String?, link: String?, imageUrl: String?) {
        this.start = start
        this.title = title
        this.link = link
        this.imageUrl = imageUrl
    }

    fun getStart(): Long {
        return start
    }

    fun getTitle(): String? {
        return title
    }

    fun getLink(): String? {
        return link
    }

    fun setStart(start: Long) {
        this.start = start
    }

    fun setTitle(title: String?) {
        this.title = title
    }

    fun setLink(link: String?) {
        this.link = link
    }

    fun getImageUrl(): String? {
        return imageUrl
    }

    fun setImageUrl(imageUrl: String?) {
        this.imageUrl = imageUrl
    }

    /**
     * ID from the chapter source, not the database ID.
     */
    fun getChapterId(): String? {
        return chapterId
    }

    fun setChapterId(chapterId: String?) {
        this.chapterId = chapterId
    }

    override fun toString(): String {
        return "Chapter [title=" + getTitle() + ", start=" + getStart() + ", url=" + getLink() + "]"
    }

    fun getId(): Long {
        return id
    }

    fun setId(id: Long) {
        this.id = id
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val chapter = o as Chapter
        return id == chapter.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    companion object {
        @JvmStatic
        fun getAfterPosition(chapters: List<Chapter>?, playbackPosition: Int): Int {
            if (chapters == null || chapters.isEmpty()) {
                return -1
            }
            for (i in 0 until chapters.size) {
                if (chapters[i].getStart() > playbackPosition) {
                    return i - 1
                }
            }
            return chapters.size - 1
        }
    }
}
