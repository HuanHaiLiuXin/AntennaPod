package de.danoeh.antennapod.net.common

import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build())
    }

    companion object {
        @JvmField
        var USER_AGENT: String = "AntennaPod/0.0.0"
    }
}
