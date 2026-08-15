package de.danoeh.antennapod.model.feed

import android.text.TextUtils

import java.util.Arrays

class SubscriptionsFilter {
    private var properties: Array<String?> = emptyArray()

    @JvmField
    var showIfCounterGreaterZero: Boolean = false

    @JvmField
    var hideNonSubscribedFeeds: Boolean = false

    @JvmField
    var showAutoDownloadEnabled: Boolean = false

    @JvmField
    var showAutoDownloadDisabled: Boolean = false

    @JvmField
    var showUpdatedEnabled: Boolean = false

    @JvmField
    var showUpdatedDisabled: Boolean = false

    @JvmField
    var showEpisodeNotificationEnabled: Boolean = false

    @JvmField
    var showEpisodeNotificationDisabled: Boolean = false

    constructor(properties: String?) : this(TextUtils.split(properties, divider)) {
    }

    constructor(properties: Array<String?>) {
        this.properties = properties
        showIfCounterGreaterZero = hasProperty(COUNTER_GREATER_ZERO)
        showAutoDownloadEnabled = hasProperty(ENABLED_AUTO_DOWNLOAD)
        showAutoDownloadDisabled = hasProperty(DISABLED_AUTO_DOWNLOAD)
        showUpdatedEnabled = hasProperty(ENABLED_UPDATES)
        showUpdatedDisabled = hasProperty(DISABLED_UPDATES)
        showEpisodeNotificationEnabled = hasProperty(EPISODE_NOTIFICATION_ENABLED)
        showEpisodeNotificationDisabled = hasProperty(EPISODE_NOTIFICATION_DISABLED)
        hideNonSubscribedFeeds = !hasProperty(SHOW_NON_SUBSCRIBED_FEEDS)
    }

    private fun hasProperty(property: String?): Boolean {
        return Arrays.asList(*properties).contains(property)
    }

    fun isEnabled(): Boolean {
        return properties.isNotEmpty()
    }

    fun getValues(): Array<String?> {
        return properties.clone()
    }

    fun serialize(): String {
        return TextUtils.join(divider, getValues())
    }

    companion object {
        private const val divider = ","

        const val COUNTER_GREATER_ZERO = "counter_greater_zero"
        const val ENABLED_AUTO_DOWNLOAD = "enabled_auto_download"
        const val DISABLED_AUTO_DOWNLOAD = "disabled_auto_download"
        const val ENABLED_UPDATES = "enabled_updates"
        const val DISABLED_UPDATES = "disabled_updates"
        const val EPISODE_NOTIFICATION_ENABLED = "episode_notification_enabled"
        const val EPISODE_NOTIFICATION_DISABLED = "episode_notification_disabled"
        const val SHOW_NON_SUBSCRIBED_FEEDS = "show_non_subscribed"
    }
}
