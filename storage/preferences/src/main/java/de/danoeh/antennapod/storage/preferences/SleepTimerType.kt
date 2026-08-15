package de.danoeh.antennapod.storage.preferences

enum class SleepTimerType(@JvmField val index: Int) {
    CLOCK(0),
    EPISODES(1);

    companion object {
        @JvmStatic
        fun fromIndex(index: Int): SleepTimerType {
            for (stt in values()) {
                if (stt.index == index) {
                    return stt
                }
            }
            return SleepTimerType.EPISODES
        }
    }
}
