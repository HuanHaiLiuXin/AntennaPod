package de.danoeh.antennapod.model.feed

import android.media.audiofx.AudioEffect

import androidx.annotation.VisibleForTesting

enum class VolumeAdaptionSetting(private val value: Int, private var adaptionFactor: Float) {
    OFF(0, 1.0f),
    LIGHT_REDUCTION(1, 0.5f),
    HEAVY_REDUCTION(2, 0.2f),
    LIGHT_BOOST(3, 1.5f),
    MEDIUM_BOOST(4, 2f),
    HEAVY_BOOST(5, 2.5f);

    fun toInteger(): Int {
        return value
    }

    fun getAdaptionFactor(): Float {
        return adaptionFactor
    }

    companion object {
        private var boostSupported: Boolean? = null

        @JvmStatic
        fun fromInteger(value: Int): VolumeAdaptionSetting {
            for (setting in values()) {
                if (setting.value == value) {
                    return setting
                }
            }
            throw IllegalArgumentException("Cannot map value to VolumeAdaptionSetting: " + value)
        }

        @JvmStatic
        fun isBoostSupported(): Boolean {
            if (boostSupported != null) {
                return boostSupported!!
            }
            val audioEffects = AudioEffect.queryEffects()
            if (audioEffects != null) {
                for (effect in audioEffects) {
                    if (effect.type == AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER) {
                        boostSupported = true
                        return boostSupported!!
                    }
                }
            }
            boostSupported = false
            return boostSupported!!
        }

        @VisibleForTesting
        @JvmStatic
        fun setBoostSupported(boostSupported: Boolean?) {
            VolumeAdaptionSetting.boostSupported = boostSupported
        }
    }
}
