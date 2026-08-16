package de.danoeh.antennapod.net.sync.serviceinterface

import android.text.TextUtils
import android.util.Log

import org.json.JSONException
import org.json.JSONObject

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.TimeZone

import de.danoeh.antennapod.model.feed.FeedItem

class EpisodeAction {
    companion object {
        private const val TAG = "EpisodeAction"
        private const val PATTERN_ISO_DATEFORMAT = "yyyy-MM-dd'T'HH:mm:ss"

        @JvmField
        val NEW: Action = Action.NEW
        @JvmField
        val DOWNLOAD: Action = Action.DOWNLOAD
        @JvmField
        val PLAY: Action = Action.PLAY
        @JvmField
        val DELETE: Action = Action.DELETE

        /**
         * Create an episode action object from JSON representation. Mandatory fields are "podcast",
         * "episode" and "action".
         *
         * @param object JSON representation
         * @return episode action object, or null if mandatory values are missing
         */
        @JvmStatic
        fun readFromJsonObject(`object`: JSONObject): EpisodeAction? {
            val podcast = `object`.optString("podcast", null)
            val episode = `object`.optString("episode", null)
            val actionString = `object`.optString("action", null)
            if (TextUtils.isEmpty(podcast) || TextUtils.isEmpty(episode) || TextUtils.isEmpty(actionString)) {
                return null
            }
            val action: Action
            try {
                action = Action.valueOf(actionString!!.uppercase(Locale.US))
            } catch (e: IllegalArgumentException) {
                return null
            }
            val builder = Builder(podcast, episode, action)
            val utcTimestamp = `object`.optString("timestamp", null)
            if (!TextUtils.isEmpty(utcTimestamp)) {
                try {
                    val parser = SimpleDateFormat(PATTERN_ISO_DATEFORMAT, Locale.US)
                    parser.setTimeZone(TimeZone.getTimeZone("UTC"))
                    builder.timestamp(parser.parse(utcTimestamp))
                } catch (e: ParseException) {
                    e.printStackTrace()
                }
            }
            val guid = `object`.optString("guid", null)
            if (!TextUtils.isEmpty(guid)) {
                builder.guid(guid)
            }
            if (action == Action.PLAY) {
                val started = `object`.optInt("started", -1)
                val position = `object`.optInt("position", -1)
                val total = `object`.optInt("total", -1)
                if (started >= 0 && position > 0 && total > 0) {
                    builder
                            .started(started)
                            .position(position)
                            .total(total)
                }
            }
            return builder.build()
        }
    }

    private val podcast: String
    private val episode: String
    private val guid: String?
    private val action: Action
    private val timestamp: Date?
    private val started: Int
    private val position: Int
    private val total: Int

    private constructor(builder: Builder) {
        this.podcast = builder.podcast
        this.episode = builder.episode
        this.guid = builder.guid
        this.action = builder.action
        this.timestamp = builder.timestamp
        this.started = builder.started
        this.position = builder.position
        this.total = builder.total
    }

    fun getPodcast(): String {
        return this.podcast
    }

    fun getEpisode(): String {
        return this.episode
    }

    fun getGuid(): String? {
        return this.guid
    }

    fun getAction(): Action {
        return this.action
    }

    private fun getActionString(): String {
        return this.action.name.lowercase(Locale.US)
    }

    fun getTimestamp(): Date? {
        return this.timestamp
    }

    /**
     * Returns the position (in seconds) at which the client started playback.
     *
     * @return start position (in seconds)
     */
    fun getStarted(): Int {
        return this.started
    }

    /**
     * Returns the position (in seconds) at which the client stopped playback.
     *
     * @return stop position (in seconds)
     */
    fun getPosition(): Int {
        return this.position
    }

    /**
     * Returns the total length of the file in seconds.
     *
     * @return total length in seconds
     */
    fun getTotal(): Int {
        return this.total
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is EpisodeAction) {
            return false
        }

        val that = o
        return started == that.started
                && position == that.position
                && total == that.total
                && action !== that.action
                && Objects.equals(podcast, that.podcast)
                && Objects.equals(episode, that.episode)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(guid, that.guid)
    }

    override fun hashCode(): Int {
        var result = if (podcast != null) podcast.hashCode() else 0
        result = 31 * result + if (episode != null) episode.hashCode() else 0
        result = 31 * result + if (guid != null) guid.hashCode() else 0
        result = 31 * result + if (action != null) action.hashCode() else 0
        result = 31 * result + if (timestamp != null) timestamp.hashCode() else 0
        result = 31 * result + started
        result = 31 * result + position
        result = 31 * result + total
        return result
    }

    /**
     * Returns a JSON object representation of this object.
     *
     * @return JSON object representation, or null if the object is invalid
     */
    fun writeToJsonObject(): JSONObject? {
        val obj = JSONObject()
        try {
            obj.putOpt("podcast", this.podcast)
            obj.putOpt("episode", this.episode)
            obj.putOpt("guid", this.guid)
            obj.put("action", this.getActionString())
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"))
            obj.put("timestamp", formatter.format(this.timestamp))
            if (this.getAction() == Action.PLAY) {
                obj.put("started", this.started)
                obj.put("position", this.position)
                obj.put("total", this.total)
            }
        } catch (e: JSONException) {
            Log.e(TAG, "writeToJSONObject(): " + e.message)
            return null
        }
        return obj
    }

    override fun toString(): String {
        return "EpisodeAction{" +
                "podcast='" + podcast + '\'' +
                ", episode='" + episode + '\'' +
                ", guid='" + guid + '\'' +
                ", action=" + action +
                ", timestamp=" + timestamp +
                ", started=" + started +
                ", position=" + position +
                ", total=" + total +
                '}'
    }

    enum class Action {
        NEW, DOWNLOAD, PLAY, DELETE
    }

    class Builder {

        // mandatory
        internal val podcast: String
        internal val episode: String
        internal val action: Action

        // optional
        internal var timestamp: Date? = null
        internal var started = -1
        internal var position = -1
        internal var total = -1
        internal var guid: String? = null

        constructor(item: FeedItem?, action: Action) {
            this.podcast = item!!.getFeed()!!.getDownloadUrl()!!
            this.episode = item.getMedia()!!.getDownloadUrl()!!
            this.action = action
            this.guid(item.getItemIdentifier())
        }

        constructor(podcast: String, episode: String, action: Action) {
            this.podcast = podcast
            this.episode = episode
            this.action = action
        }

        fun timestamp(timestamp: Date?): Builder {
            this.timestamp = timestamp
            return this
        }

        fun guid(guid: String?): Builder {
            this.guid = guid
            return this
        }

        fun currentTimestamp(): Builder {
            return timestamp(Date())
        }

        fun started(seconds: Int): Builder {
            if (action == Action.PLAY) {
                this.started = seconds
            }
            return this
        }

        fun position(seconds: Int): Builder {
            if (action == Action.PLAY) {
                this.position = seconds
            }
            return this
        }

        fun total(seconds: Int): Builder {
            if (action == Action.PLAY) {
                this.total = seconds
            }
            return this
        }

        fun build(): EpisodeAction {
            return EpisodeAction(this)
        }

    }
}
