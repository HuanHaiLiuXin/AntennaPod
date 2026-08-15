package de.danoeh.antennapod.net.sync.wearinterface

class WearDataPaths private constructor() {
    companion object {
        const val QUEUE = "/queue"
        const val DOWNLOADS = "/downloads"
        const val EPISODES = "/episodes"
        const val SUBSCRIPTIONS = "/subscriptions"
        const val FEED_EPISODES_PREFIX = "/feed_episodes/"
        const val PLAY_PREFIX = "/play/"
        const val NOW_PLAYING = "/now_playing"
        const val PAUSE = "/pause"
        const val SKIP_FORWARD = "/skip_forward"
        const val SKIP_BACKWARD = "/skip_backward"
        const val OPEN_ON_PHONE_PREFIX = "/open_on_phone/"

        @JvmStatic
        fun playPath(itemId: Long): String {
            return PLAY_PREFIX + itemId
        }

        @JvmStatic
        fun openOnPhonePath(itemId: Long): String {
            return OPEN_ON_PHONE_PREFIX + itemId
        }

        @JvmStatic
        fun feedEpisodesPath(feedId: Long): String {
            return FEED_EPISODES_PREFIX + feedId
        }
    }
}
