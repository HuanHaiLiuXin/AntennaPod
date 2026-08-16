package de.danoeh.antennapod.parser.media.id3.model

abstract class Header {
    private var id: String = ""
    private var size: Int = 0

    constructor(id: String, size: Int) {
        this.id = id
        this.size = size
    }

    fun getId(): String {
        return id
    }

    fun getSize(): Int {
        return size
    }

    override fun toString(): String {
        return "Header [id=" + id + ", size=" + size + "]"
    }
}
