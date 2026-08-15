package de.danoeh.antennapod.parser.media.id3.model

class TagHeader : Header {
    private var version: Short = 0
    private var flags: Byte = 0

    constructor(id: String, size: Int, version: Short, flags: Byte) : super(id, size) {
        this.version = version
        this.flags = flags
    }

    override fun toString(): String {
        return "TagHeader [version=" + version + ", flags=" + flags + ", id=" +
                getId() + ", size=" + getSize() + "]"
    }

    fun getVersion(): Short {
        return version
    }
}
