package de.danoeh.antennapod.parser.transcript

import org.apache.commons.lang3.StringUtils
import org.jsoup.internal.StringUtil

import java.util.HashSet
import java.util.regex.Pattern

import de.danoeh.antennapod.model.feed.Transcript
import de.danoeh.antennapod.model.feed.TranscriptSegment

class SrtTranscriptParser {
    companion object {
        private val TIMECODE_PATTERN = Pattern.compile("^([0-9]{2}):([0-9]{2}):([0-9]{2}),([0-9]{3})$")

        @JvmStatic
        fun parse(str: String?): Transcript? {
            if (StringUtils.isBlank(str)) {
                return null
            }
            var str = str!!.replace("\r\n", "\n")

            val transcript = Transcript()
            val lines: List<String> = str.split("\n")
            val iter = lines.iterator()
            var speaker = ""
            var prevSpeaker = ""
            var line: String
            var segmentBody = ""
            var startTimecode = -1L
            var spanStartTimecode = -1L
            var spanEndTimecode = -1L
            var endTimecode = -1L
            var duration = 0L
            val speakers = HashSet<String>()

            while (iter.hasNext()) {
                var body = StringBuilder()
                line = iter.next()

                if (line.isEmpty()) {
                    continue
                }

                spanEndTimecode = endTimecode
                if (line.contains("-->")) {
                    val timecodes = line.split("-->")
                    if (timecodes.size < 2) {
                        continue
                    }
                    startTimecode = parseTimecode(timecodes[0].trim())
                    endTimecode = parseTimecode(timecodes[1].trim())
                    if (startTimecode == -1L || endTimecode == -1L) {
                        continue
                    }

                    if (spanStartTimecode == -1L) {
                        spanStartTimecode = startTimecode
                    }
                    duration += endTimecode - startTimecode
                    do {
                        line = iter.next()
                        if (StringUtil.isBlank(line)) {
                            break
                        }
                        body.append(line.trim())
                        body.append(" ")
                    } while (iter.hasNext())
                }

                if (body.indexOf(": ") != -1) {
                    val parts = body.toString().trim().split(":")
                    if (parts.size < 2) {
                        continue
                    }
                    prevSpeaker = speaker
                    speaker = parts[0]
                    speakers.add(speaker)
                    body = StringBuilder(parts[1].trim())
                    if (StringUtils.isNotEmpty(prevSpeaker) && !StringUtils.equals(speaker, prevSpeaker)) {
                        if (StringUtils.isNotEmpty(segmentBody)) {
                            transcript.addSegment(TranscriptSegment(spanStartTimecode,
                                    spanEndTimecode, segmentBody, prevSpeaker))
                            duration = 0L
                            spanStartTimecode = startTimecode
                            segmentBody = body.toString()
                            continue
                        }
                    }
                } else {
                    if (StringUtils.isNotEmpty(prevSpeaker) && StringUtils.isEmpty(speaker)) {
                        speaker = prevSpeaker
                    }
                }

                segmentBody += " " + body
                segmentBody = StringUtils.trim(segmentBody)
                if (duration >= TranscriptParser.MIN_SPAN && endTimecode > spanStartTimecode) {
                    transcript.addSegment(TranscriptSegment(spanStartTimecode, endTimecode, segmentBody, speaker))
                    duration = 0L
                    spanStartTimecode = -1L
                    segmentBody = ""
                }
            }

            if (!StringUtil.isBlank(segmentBody) && endTimecode > spanStartTimecode) {
                segmentBody = StringUtils.trim(segmentBody)
                transcript.addSegment(TranscriptSegment(spanStartTimecode, endTimecode, segmentBody, speaker))
            }
            if (transcript.getSegmentCount() > 0) {
                transcript.setSpeakers(speakers)
                return transcript
            } else {
                return null
            }
        }

        fun parseTimecode(timecode: String): Long {
            val matcher = TIMECODE_PATTERN.matcher(timecode)
            if (!matcher.matches()) {
                return -1
            }
            val hours = Integer.parseInt(matcher.group(1)).toLong()
            val minutes = Integer.parseInt(matcher.group(2)).toLong()
            val seconds = Integer.parseInt(matcher.group(3)).toLong()
            val milliseconds = Integer.parseInt(matcher.group(4)).toLong()
            return (hours * 60 * 60 * 1000) + (minutes * 60 * 1000) + (seconds * 1000) + milliseconds
        }
    }
}
