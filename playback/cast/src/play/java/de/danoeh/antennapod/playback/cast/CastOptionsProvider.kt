package de.danoeh.antennapod.playback.cast

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

@Suppress("unused")
@SuppressLint("VisibleForTests")
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        return CastOptions.Builder()
                .setReceiverApplicationId("BEBC1DB1")
                .setCastMediaOptions(
                        CastMediaOptions.Builder()
                                .setMediaSessionEnabled(false)
                                .setNotificationOptions(null)
                                .build())
                .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}
