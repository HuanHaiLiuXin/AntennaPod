package de.danoeh.antennapod.ui.screen.feed.preferences

import android.content.Context
import android.util.AttributeSet

import java.util.Arrays

import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import de.danoeh.antennapod.ui.preferences.preference.MaterialListPreference

class VolumeAdaptationPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        MaterialListPreference(context, attrs) {

    override fun getEntries(): Array<CharSequence>? {
        if (VolumeAdaptionSetting.isBoostSupported()) {
            return super.getEntries()
        } else {
            return Arrays.copyOfRange(super.getEntries(), 0, 3)
        }
    }
}
