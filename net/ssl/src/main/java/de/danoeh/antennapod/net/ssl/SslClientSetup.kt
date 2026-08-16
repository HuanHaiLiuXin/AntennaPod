package de.danoeh.antennapod.net.ssl

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient

import javax.net.ssl.X509TrustManager
import java.util.Arrays

class SslClientSetup {
    companion object {
        @JvmStatic
        fun installCertificates(builder: OkHttpClient.Builder) {
            val trustManager = BackportTrustManager.create()
            builder.sslSocketFactory(AntennaPodSslSocketFactory(trustManager!!), trustManager)
            builder.connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
        }
    }
}
