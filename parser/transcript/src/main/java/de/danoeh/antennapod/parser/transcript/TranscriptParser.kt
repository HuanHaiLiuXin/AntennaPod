package de.danoeh.antennapod.parser.transcript

import de.danoeh.antennapod.model.feed.TranscriptType
import org.apache.commons.lang3.StringUtils

import de.danoeh.antennapod.model.feed.Transcript

class TranscriptParser {
    companion object {
        const val MIN_SPAN = 5000L // Merge short segments together to form a span of 5 seconds
        const val MAX_SPAN = 8000L // Don't go beyond 10 seconds when merging

        @JvmStatic
        fun parse(str: String?, typeStr: String?): Transcript? {
            if (str == null || StringUtils.isBlank(str)) {
                return null
            }
            val type = TranscriptType.fromMime(typeStr)
            return when (type) {
                TranscriptType.JSON -> JsonTranscriptParser.parse(str)
                TranscriptType.VTT -> VttTranscriptParser.parse(str)
                TranscriptType.SRT -> SrtTranscriptParser.parse(str)
                else -> null
            }
        }
    }
}
