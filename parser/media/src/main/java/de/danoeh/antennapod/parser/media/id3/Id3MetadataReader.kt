package de.danoeh.antennapod.parser.media.id3

import de.danoeh.antennapod.parser.media.id3.model.FrameHeader
import org.apache.commons.io.input.CountingInputStream

/**
 * Reads general ID3 metadata like comment, which Android's MediaMetadataReceiver does not support.
 */
class Id3MetadataReader(input: CountingInputStream) : ID3Reader(input) {
    private var comment: String? = null

    override fun readFrame(frameHeader: FrameHeader) {
        if (FRAME_ID_COMMENT == frameHeader.getId()) {
            val frameStart = getPosition()
            val encoding = readByte().toInt()
            skipBytes(3) // Language
            val shortDescription = readEncodedString(encoding, frameHeader.getSize() - 4)
            val longDescription = readEncodedString(encoding,
                    (frameHeader.getSize() - (getPosition() - frameStart)).toInt())
            comment = if (shortDescription.length > longDescription.length) shortDescription else longDescription
        } else if (FRAME_ID_CUSTOM_TEXT == frameHeader.getId()) {
            val frameStart = getPosition()
            val encoding = readByte().toInt()
            val description = readEncodedString(encoding, frameHeader.getSize() - 1)
            val value = readEncodedString(encoding, (frameHeader.getSize() - (getPosition() - frameStart)).toInt())
            if (CUSTOM_TEXT_COMMENT == description) {
                comment = value
            }
        } else {
            super.readFrame(frameHeader)
        }
    }

    fun getComment(): String? {
        return comment
    }

    companion object {
        const val FRAME_ID_COMMENT = "COMM"
        const val FRAME_ID_CUSTOM_TEXT = "TXXX"
        const val CUSTOM_TEXT_COMMENT = "comment"
    }
}
