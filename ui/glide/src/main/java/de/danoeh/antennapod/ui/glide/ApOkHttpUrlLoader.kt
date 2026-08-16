package de.danoeh.antennapod.ui.glide

import android.content.ContentResolver
import android.text.TextUtils
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.net.common.UserAgentInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

import java.io.IOException
import java.io.InputStream

/**
 * {@see com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader}.
 */
class ApOkHttpUrlLoader private constructor(private val client: OkHttpClient) : ModelLoader<String, InputStream> {

    /**
     * The default factory for [ApOkHttpUrlLoader]s.
     */
    class Factory : ModelLoaderFactory<String, InputStream> {

        private val client: OkHttpClient

        /**
         * Constructor for a new Factory that runs requests using a static singleton client.
         */
        constructor() {
            this.client = getInternalClient()
        }

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, InputStream> {
            return ApOkHttpUrlLoader(client)
        }

        override fun teardown() {
            // Do nothing, this instance doesn't own the client.
        }

        companion object {
            @Volatile
            private var internalClient: OkHttpClient? = null

            private fun getInternalClient(): OkHttpClient {
                var client = internalClient
                if (client == null) {
                    synchronized(Factory::class.java) {
                        client = internalClient
                        if (client == null) {
                            val builder = AntennapodHttpClient.newBuilder()
                            builder.interceptors().add(NetworkAllowanceInterceptor())
                            builder.interceptors().add(UserAgentInterceptor())
                            builder.cache(null) // Handled by Glide
                            client = builder.build()
                            internalClient = client
                        }
                    }
                }
                return client!!
            }
        }
    }

    override fun buildLoadData(model: String, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData(ObjectKey(model), ResizingOkHttpStreamFetcher(client, GlideUrl(model)))
    }

    override fun handles(model: String): Boolean {
        return !TextUtils.isEmpty(model)
                // If the other loaders fail, do not attempt to load as web resource
                && !model.startsWith(Feed.PREFIX_GENERATIVE_COVER)
                && !model.startsWith(FeedMedia.FILENAME_PREFIX_EMBEDDED_COVER)
                // Leave content URIs to Glide's default loaders
                && !model.startsWith(ContentResolver.SCHEME_CONTENT)
                && !model.startsWith(ContentResolver.SCHEME_ANDROID_RESOURCE)
    }

    private class NetworkAllowanceInterceptor : Interceptor {

        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            if (NetworkUtils.isImageAllowed()) {
                return chain.proceed(chain.request())
            } else {
                return Response.Builder()
                        .protocol(Protocol.HTTP_2)
                        .code(420)
                        .message("Policy Not Fulfilled")
                        .body(byteArrayOf().toResponseBody(null))
                        .request(chain.request())
                        .build()
            }
        }
    }
}
