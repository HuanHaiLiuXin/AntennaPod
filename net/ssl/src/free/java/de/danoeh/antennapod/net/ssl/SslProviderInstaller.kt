package de.danoeh.antennapod.net.ssl

import android.content.Context
import org.conscrypt.Conscrypt

import java.security.Security

class SslProviderInstaller {
    companion object {
        @JvmStatic
        fun install(context: Context) {
            // Insert bundled conscrypt as highest security provider (overrides OS version).
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }
    }
}
