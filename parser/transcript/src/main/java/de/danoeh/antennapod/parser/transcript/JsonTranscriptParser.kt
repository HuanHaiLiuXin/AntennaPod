package de.danoeh.antennapod.parser.transcript

import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.internal.StringUtil

import java.util.HashSet

import de.danoeh.antennapod.model.feed.Transcript
import de.danoeh.antennapod.model.feed.TranscriptSegment

class JsonTranscriptParser {
    companion object {
        @JvmStatic
        fun parse(jsonStr: String?): Transcript? {
            try {
                val transcript = Transcript()
                var startTime = -1L
                var endTime = -1L
                var segmentStartTime = -1L
                var segmentEndTime = -1L
                var duration = 0L
                var speaker = ""
                var prevSpeaker = ""
                var segmentBody = ""
                val objSegments: JSONArray
                val speakers = HashSet<String>()

                try {
                    val obj = JSONObject(jsonStr)
                    objSegments = obj.getJSONArray("segments")
                } catch (e: JSONException) {
                    e.printStackTrace()
                    return null
                }

                for (i in 0 until objSegments.length()) {
                    val jsonObject = objSegments.getJSONObject(i)
                    segmentEndTime = endTime
                    startTime = java.lang.Double.valueOf(jsonObject.optDouble("startTime", -1.0) * 1000L).toLong()
                    endTime = java.lang.Double.valueOf(jsonObject.optDouble("endTime", -1.0) * 1000L).toLong()
                    if (startTime < 0 || endTime < 0) {
                        continue
                    }
                    if (segmentStartTime == -1L) {
                        segmentStartTime = startTime
                    }
                    duration += endTime - startTime

                    prevSpeaker = speaker
                    speaker = jsonObject.optString("speaker")
                    speakers.add(speaker)
                    if (StringUtils.isEmpty(speaker) && StringUtils.isNotEmpty(prevSpeaker)) {
                        speaker = prevSpeaker
                    }
                    val body = jsonObject.optString("body")
                    if (prevSpeaker != speaker) {
                        if (StringUtils.isNotEmpty(segmentBody)) {
                            segmentBody = StringUtils.trim(segmentBody)
                            transcript.addSegment(TranscriptSegment(segmentStartTime,
                                    segmentEndTime,
                                    segmentBody,
                                    prevSpeaker))
                            segmentStartTime = startTime
                            segmentBody = body.toString()
                            duration = 0L
                            continue
                        }
                    }

                    segmentBody += " " + body

                    if (duration >= TranscriptParser.MIN_SPAN) {
                        // Look ahead and make sure the next segment does not start with an alphanumeric character
                        if ((i + 1) < objSegments.length()) {
                            val nextSegmentFirstChar = objSegments.getJSONObject(i + 1)
                                    .optString("body")
                                    .substring(0, 1)
                            if (!StringUtils.isAlphanumeric(nextSegmentFirstChar)
                                    && (duration < TranscriptParser.MAX_SPAN)) {
                                continue
                            }
                        }
                        segmentBody = StringUtils.trim(segmentBody)
                        transcript.addSegment(TranscriptSegment(segmentStartTime, endTime, segmentBody, speaker))
                        duration = 0L
                        segmentBody = ""
                        segmentStartTime = -1L
                    }
                }

                if (!StringUtil.isBlank(segmentBody)) {
                    segmentBody = StringUtils.trim(segmentBody)
                    transcript.addSegment(TranscriptSegment(segmentStartTime, endTime, segmentBody, speaker))
                }

                if (transcript.getSegmentCount() > 0) {
                    transcript.setSpeakers(speakers)
                    return transcript
                } else {
                    return null
                }

            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return null
        }
    }
}
