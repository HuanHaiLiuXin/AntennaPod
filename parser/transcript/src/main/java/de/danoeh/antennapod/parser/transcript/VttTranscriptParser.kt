package de.danoeh.antennapod.parser.transcript

import org.apache.commons.lang3.StringUtils
import org.jsoup.Jsoup

import java.util.HashSet
import java.util.regex.Pattern

import de.danoeh.antennapod.model.feed.Transcript
import de.danoeh.antennapod.model.feed.TranscriptSegment

class VttTranscriptParser {
    companion object {
        private val TIMESTAMP_PATTERN =
                Pattern.compile("^(?:([0-9]{1,2}):)?([0-9]{2}):([0-9]{2})\\.([0-9]{3})$")

        private val VOICE_SPAN =
                Pattern.compile("<v(?:\\.[^\\t\\n\\r &<>.]+)*[ \\t]([^\\n\\r&>]+)>")

        private class Timings(val start: Long, val end: Long)

        @JvmStatic
        fun parse(str: String?): Transcript? {
            // This is basically a very light WebVTT parser.
            // It uses WebVTT properties to be both exact and very light.
            // We will only be parsing the WebVTT cue blocks.

            if (StringUtils.isBlank(str)) {
                return null
            }

            // WebVTT line terminator can be \r\n, \n or \n, let's use only one
            var str = str!!.replace("\r\n?", "\n")
            val lines: List<String> = str.split("\n")

            val transcript = Transcript()
            val iterator = lines.iterator()
            val speakers = HashSet<String>()
            var speaker = ""
            var segment: TranscriptSegment? = null

            // Iterate through cue blocks
            while (iterator.hasNext()) {
                val line = iterator.next()

                if (!line.contains("-->")) {
                    continue
                }

                val timings = parseCueTimings(line)
                if (timings == null) {
                    return null // Input is broken
                }

                var payload = parseCuePayload(iterator)

                val matcher = VOICE_SPAN.matcher(payload)
                if (matcher.find()) {
                    speaker = matcher.group(1)
                    speakers.add(speaker)
                }

                payload = Jsoup.parse(payload).text() // remove all HTML tags

                // should we merge this segment with the previous one?
                if (segment != null && segment.getSpeaker() == speaker
                        && timings.end - segment.getStartTime() < TranscriptParser.MAX_SPAN) {
                    segment.append(timings.end, payload)
                } else {
                    if (segment != null) {
                        transcript.addSegment(segment)
                    }
                    segment = TranscriptSegment(timings.start, timings.end, payload, speaker)
                }

                // do we have a candidate segment long enough to add it without trying to add more
                if (segment.getEndTime() - segment.getStartTime() >= TranscriptParser.MIN_SPAN) {
                    transcript.addSegment(segment)
                    segment = null
                }
            }

            if (segment != null) {
                transcript.addSegment(segment)
            }

            if (transcript.getSegmentCount() == 0) {
                return null
            }
            transcript.setSpeakers(speakers)
            return transcript
        }

        private fun parseIntOrNull(s: String?): Long {
            return if (StringUtils.isEmpty(s)) 0 else Integer.parseInt(s).toLong()
        }

        private fun parseTimestamp(timestamp: String): Long {
            val matcher = TIMESTAMP_PATTERN.matcher(timestamp)
            if (!matcher.matches()) {
                return -1
            }
            val hours = parseIntOrNull(matcher.group(1))
            val minutes = parseIntOrNull(matcher.group(2))
            val seconds = parseIntOrNull(matcher.group(3))
            val milliseconds = parseIntOrNull(matcher.group(4))
            return (hours * 60 * 60 * 1000) + (minutes * 60 * 1000) + (seconds * 1000) + milliseconds
        }

        private fun parseCueTimings(line: String): Timings? {
            val timestamps = line.split("-->")
            if (timestamps.size < 2) {
                return null
            }
            val start = parseTimestamp(timestamps[0].trim())
            val end = parseTimestamp(timestamps[1].trim().split(Regex("[ \\t]"))[0])
            if (start == -1L || end == -1L) {
                return null
            }
            return Timings(start, end)
        }

        private fun parseCuePayload(iterator: Iterator<String>): String {
            val body = StringBuilder()
            while (iterator.hasNext()) {
                val line = iterator.next()
                if (line.isEmpty()) {
                    break
                }
                body.append(line.trim())
                body.append(" ")
            }
            return body.toString().trim()
        }
    }
}
