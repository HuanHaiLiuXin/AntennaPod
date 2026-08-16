package de.danoeh.antennapod.ui.screen.subscriptions

import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.SubscriptionsFilter

enum class SubscriptionsFilterGroup(vararg val values: ItemProperties) {
    COUNTER_GREATER_ZERO(ItemProperties(R.string.subscriptions_counter_greater_zero,
            SubscriptionsFilter.COUNTER_GREATER_ZERO)),
    AUTO_DOWNLOAD(ItemProperties(R.string.auto_downloaded, SubscriptionsFilter.ENABLED_AUTO_DOWNLOAD),
            ItemProperties(R.string.not_auto_downloaded, SubscriptionsFilter.DISABLED_AUTO_DOWNLOAD)),
    UPDATED(ItemProperties(R.string.kept_updated, SubscriptionsFilter.ENABLED_UPDATES),
            ItemProperties(R.string.not_kept_updated, SubscriptionsFilter.DISABLED_UPDATES)),
    NEW_EPISODE_NOTIFICATION(ItemProperties(R.string.new_episode_notification_enabled,
                    SubscriptionsFilter.EPISODE_NOTIFICATION_ENABLED),
            ItemProperties(R.string.new_episode_notification_disabled,
                    SubscriptionsFilter.EPISODE_NOTIFICATION_DISABLED));

    class ItemProperties(val displayName: Int, val filterId: String)
}
