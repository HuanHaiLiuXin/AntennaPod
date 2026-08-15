package de.danoeh.antennapod.model.feed

import android.text.TextUtils

import java.io.Serializable
import java.util.HashSet

/**
 * Contains preferences for a single feed.
 */
class FeedPreferences : Serializable {

    private var filter: FeedFilter? = null
    private var feedID: Long = 0
    private var autoDownload: AutoDownloadSetting? = null
    private var keepUpdated: Boolean = false
    private var autoDeleteAction: AutoDeleteAction? = null
    private var volumeAdaptionSetting: VolumeAdaptionSetting? = null
    private var newEpisodesAction: NewEpisodesAction? = null
    private var username: String? = null
    private var password: String? = null
    private var feedPlaybackSpeed: Float = 0f
    private var feedSkipIntro: Int = 0
    private var feedSkipEnding: Int = 0
    private var feedSkipSilence: SkipSilence? = null
    private var showEpisodeNotification: Boolean = false
    private val tags = HashSet<String>()

    constructor(feedID: Long, autoDownload: AutoDownloadSetting, autoDeleteAction: AutoDeleteAction,
                volumeAdaptionSetting: VolumeAdaptionSetting, newEpisodesAction: NewEpisodesAction,
                username: String?, password: String?) : this(feedID, autoDownload, true, autoDeleteAction,
        volumeAdaptionSetting, username, password,
        FeedFilter(), SPEED_USE_GLOBAL, 0, 0, SkipSilence.GLOBAL,
        false, newEpisodesAction, HashSet()) {
    }

    constructor(feedID: Long, autoDownload: AutoDownloadSetting, keepUpdated: Boolean,
                autoDeleteAction: AutoDeleteAction, volumeAdaptionSetting: VolumeAdaptionSetting,
                username: String?, password: String?, filter: FeedFilter,
                feedPlaybackSpeed: Float, feedSkipIntro: Int, feedSkipEnding: Int,
                feedSkipSilence: SkipSilence, showEpisodeNotification: Boolean,
                newEpisodesAction: NewEpisodesAction, tags: Set<String>?) {
        this.feedID = feedID
        this.autoDownload = autoDownload
        this.keepUpdated = keepUpdated
        this.autoDeleteAction = autoDeleteAction
        this.volumeAdaptionSetting = volumeAdaptionSetting
        this.username = username
        this.password = password
        this.filter = filter
        this.feedPlaybackSpeed = feedPlaybackSpeed
        this.feedSkipIntro = feedSkipIntro
        this.feedSkipEnding = feedSkipEnding
        this.feedSkipSilence = feedSkipSilence
        this.showEpisodeNotification = showEpisodeNotification
        this.newEpisodesAction = newEpisodesAction
        this.tags.addAll(tags!!)
    }

    /**
     * @return the filter for this feed
     */
    fun getFilter(): FeedFilter {
        return filter!!
    }

    fun setFilter(filter: FeedFilter) {
        this.filter = filter
    }

    /**
     * @return true if this feed should be refreshed when everything else is being refreshed
     *         if false the feed should only be refreshed if requested directly.
     */
    fun getKeepUpdated(): Boolean {
        return keepUpdated
    }

    fun setKeepUpdated(keepUpdated: Boolean) {
        this.keepUpdated = keepUpdated
    }

    /**
     * Update this FeedPreferences object from another one. The feedID, autoDownload and AutoDeleteAction attributes
     * are excluded from the update.
     */
    fun updateFromOther(other: FeedPreferences?) {
        if (other == null)
            return
        this.username = other.username
        this.password = other.password
    }

    fun getFeedID(): Long {
        return feedID
    }

    fun setFeedID(feedID: Long) {
        this.feedID = feedID
    }

    /**
     * This function returns the calculated auto-download state for the given FeedPreference.
     * By supplying the global default, the returned value will present the actionable state of the
     * download-state choosen by the user. No further checks need to be made.
     * @param globalDefault Global Setting for automatic downloading of items.
     * @return whether this item should be downloaded
     */
    fun isAutoDownload(globalDefault: Boolean): Boolean {
        return when (this.autoDownload!!) {
            AutoDownloadSetting.ENABLED -> true
            AutoDownloadSetting.DISABLED -> false
            else -> globalDefault
        }
    }

    /**
     * @return The autodownload settings value for this item.
     */
    fun getAutoDownload(): AutoDownloadSetting {
        return this.autoDownload!!
    }

    fun setAutoDownload(setting: AutoDownloadSetting) {
        this.autoDownload = setting
    }

    fun getAutoDeleteAction(): AutoDeleteAction {
        return autoDeleteAction!!
    }

    fun getVolumeAdaptionSetting(): VolumeAdaptionSetting {
        return volumeAdaptionSetting!!
    }

    fun getNewEpisodesAction(): NewEpisodesAction {
        return newEpisodesAction!!
    }

    fun setAutoDeleteAction(autoDeleteAction: AutoDeleteAction) {
        this.autoDeleteAction = autoDeleteAction
    }

    fun setVolumeAdaptionSetting(volumeAdaptionSetting: VolumeAdaptionSetting) {
        this.volumeAdaptionSetting = volumeAdaptionSetting
    }

    fun setNewEpisodesAction(newEpisodesAction: NewEpisodesAction) {
        this.newEpisodesAction = newEpisodesAction
    }

    fun getCurrentAutoDelete(): AutoDeleteAction {
        return autoDeleteAction!!
    }

    fun getUsername(): String? {
        return username
    }

    fun setUsername(username: String?) {
        this.username = username
    }

    fun getPassword(): String? {
        return password
    }

    fun setPassword(password: String?) {
        this.password = password
    }

    fun getFeedPlaybackSpeed(): Float {
        return feedPlaybackSpeed
    }

    fun setFeedPlaybackSpeed(playbackSpeed: Float) {
        feedPlaybackSpeed = playbackSpeed
    }

    fun setFeedSkipIntro(skipIntro: Int) {
        feedSkipIntro = skipIntro
    }

    fun getFeedSkipIntro(): Int {
        return feedSkipIntro
    }

    fun setFeedSkipEnding(skipEnding: Int) {
        feedSkipEnding = skipEnding
    }

    fun getFeedSkipEnding(): Int {
        return feedSkipEnding
    }

    fun setFeedSkipSilence(skipSilence: SkipSilence) {
        feedSkipSilence = skipSilence
    }

    fun getFeedSkipSilence(): SkipSilence {
        if (feedPlaybackSpeed == SPEED_USE_GLOBAL) {
            return SkipSilence.GLOBAL
        }
        return feedSkipSilence!!
    }

    fun getTags(): Set<String> {
        return tags
    }

    fun getTagsAsString(): String {
        return TextUtils.join(TAG_SEPARATOR, tags)
    }

    /**
     * getter for preference if notifications should be display for new episodes.
     * @return true for displaying notifications
     */
    fun getShowEpisodeNotification(): Boolean {
        return showEpisodeNotification
    }

    fun setShowEpisodeNotification(showEpisodeNotification: Boolean) {
        this.showEpisodeNotification = showEpisodeNotification
    }

    enum class AutoDeleteAction(@JvmField val code: Int) {
        GLOBAL(0),
        ALWAYS(1),
        NEVER(2);

        companion object {
            @JvmStatic
            fun fromCode(code: Int): AutoDeleteAction {
                for (action in values()) {
                    if (code == action.code) {
                        return action
                    }
                }
                return NEVER
            }
        }
    }

    enum class NewEpisodesAction(@JvmField val code: Int) {
        GLOBAL(0),
        ADD_TO_INBOX(1),
        ADD_TO_QUEUE(3),
        NOTHING(2);

        companion object {
            @JvmStatic
            fun fromCode(code: Int): NewEpisodesAction {
                for (action in values()) {
                    if (code == action.code) {
                        return action
                    }
                }
                return ADD_TO_INBOX
            }
        }
    }

    enum class SkipSilence(@JvmField val code: Int) {
        OFF(0), GLOBAL(1), AGGRESSIVE(2);

        companion object {
            @JvmStatic
            fun fromCode(code: Int): SkipSilence {
                for (s in values()) {
                    if (s.code == code) {
                        return s
                    }
                }
                return GLOBAL
            }
        }
    }

    enum class AutoDownloadSetting(@JvmField val code: Int) {
        DISABLED(0),
        ENABLED(2),
        GLOBAL(1);

        companion object {
            @JvmStatic
            fun fromInteger(code: Int): AutoDownloadSetting {
                for (setting in values()) {
                    if (code == setting.code) {
                        return setting
                    }
                }
                return GLOBAL
            }

            @JvmStatic
            fun fromBoolean(enabled: Boolean): AutoDownloadSetting {
                if (enabled) {
                    return ENABLED
                }
                return DISABLED
            }
        }
    }

    companion object {
        const val SPEED_USE_GLOBAL = -1f
        const val TAG_ROOT = "#root"
        const val TAG_UNTAGGED = "#untagged"
        const val TAG_SEPARATOR = "\u001e"
    }
}
