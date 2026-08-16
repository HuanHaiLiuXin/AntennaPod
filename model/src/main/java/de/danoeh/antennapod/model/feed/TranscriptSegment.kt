package de.danoeh.antennapod.model.feed

class TranscriptSegment {
    private val startTime: Long
    private var endTime: Long = 0
    private var words: String? = null
    private val speaker: String?

    constructor(start: Long, end: Long, w: String?, s: String?) {
        startTime = start
        endTime = end
        words = w
        speaker = s
    }

    fun append(newEndTime: Long, wordsToAppend: String?) {
        endTime = newEndTime
        words += " " + wordsToAppend
    }

    fun getStartTime(): Long {
        return startTime
    }

    fun getEndTime(): Long {
        return endTime
    }

    fun getWords(): String? {
        return words
    }

    fun getSpeaker(): String? {
        return speaker
    }
}
