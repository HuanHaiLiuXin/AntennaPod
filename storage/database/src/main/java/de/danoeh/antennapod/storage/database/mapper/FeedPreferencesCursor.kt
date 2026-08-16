package de.danoeh.antennapod.storage.database.mapper

import android.database.Cursor
import android.database.CursorWrapper
import android.text.TextUtils
import de.danoeh.antennapod.model.feed.FeedFilter
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import de.danoeh.antennapod.storage.database.PodDBAdapter

import java.util.Arrays
import java.util.HashSet

/**
 * Converts a [Cursor] to a [FeedPreferences] object.
 */
class FeedPreferencesCursor : CursorWrapper {
    private val indexId: Int
    private val indexAutoDownload: Int
    private val indexAutoRefresh: Int
    private val indexAutoDeleteAction: Int
    private val indexVolumeAdaption: Int
    private val indexUsername: Int
    private val indexPassword: Int
    private val indexIncludeFilter: Int
    private val indexExcludeFilter: Int
    private val indexMinimalDurationFilter: Int
    private val indexFeedPlaybackSpeed: Int
    private val indexFeedSkipSilence: Int
    private val indexAutoSkipIntro: Int
    private val indexAutoSkipEnding: Int
    private val indexEpisodeNotification: Int
    private val indexNewEpisodesAction: Int
    private val indexTags: Int

    constructor(cursor: Cursor) : super(cursor) {
        indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.SELECT_KEY_FEED_ID)
        indexAutoDownload = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_AUTO_DOWNLOAD_ENABLED)
        indexAutoRefresh = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_KEEP_UPDATED)
        indexAutoDeleteAction = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_AUTO_DELETE_ACTION)
        indexVolumeAdaption = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEED_VOLUME_ADAPTION)
        indexUsername = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_USERNAME)
        indexPassword = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PASSWORD)
        indexIncludeFilter = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_INCLUDE_FILTER)
        indexExcludeFilter = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_EXCLUDE_FILTER)
        indexMinimalDurationFilter = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_MINIMAL_DURATION_FILTER)
        indexFeedPlaybackSpeed = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEED_PLAYBACK_SPEED)
        indexFeedSkipSilence = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEED_SKIP_SILENCE)
        indexAutoSkipIntro = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEED_SKIP_INTRO)
        indexAutoSkipEnding = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEED_SKIP_ENDING)
        indexEpisodeNotification = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_EPISODE_NOTIFICATION)
        indexNewEpisodesAction = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_NEW_EPISODES_ACTION)
        indexTags = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEED_TAGS)
    }

    /**
     * Create a [FeedPreferences] instance from a database row (cursor).
     */
    fun getFeedPreferences(): FeedPreferences {
        var tagsString = getString(indexTags)
        if (TextUtils.isEmpty(tagsString)) {
            tagsString = FeedPreferences.TAG_ROOT
        }
        return FeedPreferences(
                getLong(indexId),
                FeedPreferences.AutoDownloadSetting.fromInteger(getInt(indexAutoDownload)),
                getInt(indexAutoRefresh) > 0,
                FeedPreferences.AutoDeleteAction.fromCode(getInt(indexAutoDeleteAction)),
                VolumeAdaptionSetting.fromInteger(getInt(indexVolumeAdaption)),
                getString(indexUsername),
                getString(indexPassword),
                FeedFilter(getString(indexIncludeFilter),
                        getString(indexExcludeFilter), getInt(indexMinimalDurationFilter)),
                getFloat(indexFeedPlaybackSpeed),
                getInt(indexAutoSkipIntro),
                getInt(indexAutoSkipEnding),
                FeedPreferences.SkipSilence.fromCode(getInt(indexFeedSkipSilence)),
                getInt(indexEpisodeNotification) > 0,
                FeedPreferences.NewEpisodesAction.fromCode(getInt(indexNewEpisodesAction)),
                HashSet(Arrays.asList(*tagsString.split(FeedPreferences.TAG_SEPARATOR).toTypedArray())))
    }
}
