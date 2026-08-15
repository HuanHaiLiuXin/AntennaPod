package de.danoeh.antennapod.parser.media.vorbis

class VorbisCommentHeader {
    private var vendorString: String? = null
    private var userCommentLength: Long = 0

    constructor(vendorString: String?, userCommentLength: Long) {
        this.vendorString = vendorString
        this.userCommentLength = userCommentLength
    }

    override fun toString(): String {
        return "VorbisCommentHeader [vendorString=" + vendorString +
                ", userCommentLength=" + userCommentLength + "]"
    }

    fun getVendorString(): String? {
        return vendorString
    }

    fun getUserCommentLength(): Long {
        return userCommentLength
    }

}
