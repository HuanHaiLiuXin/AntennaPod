package de.danoeh.antennapod.parser.feed

import de.danoeh.antennapod.model.feed.Chapter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.util.ArrayList

class PodcastIndexChapterParser {
    companion object {
        @JvmStatic
        fun parse(jsonStr: String): List<Chapter>? {
            try {
                val chapters = ArrayList<Chapter>()
                val obj = JSONObject(jsonStr)
                val objChapters: JSONArray = obj.getJSONArray("chapters")
                for (i in 0 until objChapters.length()) {
                    val jsonObject = objChapters.getJSONObject(i)
                    val startTime = jsonObject.optInt("startTime", 0)
                    val title = jsonObject.optString("title")
                    val link = jsonObject.optString("url")
                    val img = jsonObject.optString("img")
                    chapters.add(Chapter(startTime * 1000L, title, link, img))
                }
                return chapters
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return null
        }
    }
}
