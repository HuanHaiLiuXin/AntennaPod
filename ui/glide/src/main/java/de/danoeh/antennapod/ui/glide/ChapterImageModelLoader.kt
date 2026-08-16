package de.danoeh.antennapod.ui.glide

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import de.danoeh.antennapod.model.feed.EmbeddedChapterImage
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer

import de.danoeh.antennapod.net.common.AntennapodHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.io.IOUtils

class ChapterImageModelLoader(private val context: Context) : ModelLoader<EmbeddedChapterImage, ByteBuffer> {
    class Factory(private val context: Context) : ModelLoaderFactory<EmbeddedChapterImage, ByteBuffer> {

        override fun build(unused: MultiModelLoaderFactory): ModelLoader<EmbeddedChapterImage, ByteBuffer> {
            return ChapterImageModelLoader(context)
        }

        override fun teardown() {
            // Do nothing.
        }
    }

    override fun buildLoadData(model: EmbeddedChapterImage, width: Int,
                               height: Int, options: Options): ModelLoader.LoadData<ByteBuffer> {
        return ModelLoader.LoadData(ObjectKey(model), EmbeddedImageFetcher(model, context))
    }

    override fun handles(model: EmbeddedChapterImage): Boolean {
        return true
    }

    internal class EmbeddedImageFetcher(private val image: EmbeddedChapterImage,
                                        private val context: Context) : DataFetcher<ByteBuffer> {

        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ByteBuffer>) {

            var stream: BufferedInputStream? = null
            try {
                val isLocalFeed = image.getMedia().getStreamUrl()!!.startsWith(ContentResolver.SCHEME_CONTENT)
                if (isLocalFeed || image.getMedia().localFileAvailable()) {
                    if (isLocalFeed) {
                        val uri = Uri.parse(image.getMedia().getStreamUrl())
                        stream = BufferedInputStream(context.getContentResolver().openInputStream(uri))
                    } else {
                        val localFile = File(image.getMedia().getLocalFileUrl())
                        stream = BufferedInputStream(FileInputStream(localFile))
                    }
                    IOUtils.skip(stream, image.getPosition().toLong())
                    val imageContent = ByteArray(image.getLength())
                    IOUtils.read(stream, imageContent, 0, image.getLength())
                    callback.onDataReady(ByteBuffer.wrap(imageContent))
                } else {
                    val httpReq = Request.Builder()
                    // Skipping would download the whole file
                    httpReq.header("Range", "bytes=" + image.getPosition()
                            + "-" + (image.getPosition() + image.getLength()))
                    httpReq.url(image.getMedia().getStreamUrl()!!)
                    val response = AntennapodHttpClient.getHttpClient().newCall(httpReq.build()).execute()
                    if (!response.isSuccessful || response.body == null) {
                        throw IOException("Invalid response: " + response.code + " " + response.message)
                    }
                    callback.onDataReady(ByteBuffer.wrap(response.body!!.bytes()))
                }
            } catch (e: IOException) {
                callback.onLoadFailed(e)
            } finally {
                IOUtils.closeQuietly(stream)
            }
        }

        override fun cleanup() {
            // nothing to clean up
        }

        override fun cancel() {
            // cannot cancel
        }

        override fun getDataClass(): Class<ByteBuffer> {
            return ByteBuffer::class.java
        }

        override fun getDataSource(): DataSource {
            return DataSource.LOCAL
        }
    }
}
