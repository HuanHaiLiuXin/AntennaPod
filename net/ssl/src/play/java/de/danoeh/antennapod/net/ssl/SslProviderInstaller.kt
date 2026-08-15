package de.danoeh.antennapod.net.ssl

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller

class SslProviderInstaller {
    companion object {
        @JvmStatic
        fun install(context: Context) {
            try {
                ProviderInstaller.installIfNeeded(context)
            } catch (e: GooglePlayServicesRepairableException) {
                e.printStackTrace()
                GoogleApiAvailability.getInstance().showErrorNotification(context, e.connectionStatusCode)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
