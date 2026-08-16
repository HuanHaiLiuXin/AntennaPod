package de.danoeh.antennapod.event.settings

import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting

class VolumeAdaptionChangedEvent {
    private val volumeAdaptionSetting: VolumeAdaptionSetting
    private val feedId: Long

    constructor(volumeAdaptionSetting: VolumeAdaptionSetting, feedId: Long) {
        this.volumeAdaptionSetting = volumeAdaptionSetting
        this.feedId = feedId
    }

    fun getVolumeAdaptionSetting(): VolumeAdaptionSetting {
        return volumeAdaptionSetting
    }

    fun getFeedId(): Long {
        return feedId
    }
}
