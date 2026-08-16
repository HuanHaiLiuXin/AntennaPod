package de.danoeh.antennapod.ui.glide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log

import com.bumptech.glide.Priority
import com.bumptech.glide.integration.okhttp3.OkHttpStreamFetcher
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.GlideUrl
import okhttp3.Call
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class ResizingOkHttpStreamFetcher(client: Call.Factory, url: GlideUrl) : OkHttpStreamFetcher(client, url) {
    companion object {
        private const val TAG = "ResizingOkHttpStreamFet"
        private const val MAX_DIMENSIONS = 1500
        private const val MAX_FILE_SIZE = 1024 * 1024 // 1 MB
    }

    private var stream: FileInputStream? = null
    private var tempIn: File? = null
    private var tempOut: File? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        super.loadData(priority, object : DataFetcher.DataCallback<InputStream> {
            override fun onDataReady(data: InputStream?) {
                if (data == null) {
                    callback.onDataReady(null)
                    return
                }
                try {
                    tempIn = File.createTempFile("resize_", null)
                    tempOut = File.createTempFile("resize_", null)
                    val outputStream: OutputStream = FileOutputStream(tempIn)
                    IOUtils.copy(data, outputStream)
                    outputStream.close()
                    IOUtils.closeQuietly(data)

                    if (tempIn!!.length() <= MAX_FILE_SIZE) {                        try {
                            stream = FileInputStream(tempIn)
                            callback.onDataReady(stream) // Just deliver the original, non-scaled image
                        } catch (fileNotFoundException: FileNotFoundException) {
                            callback.onLoadFailed(fileNotFoundException)
                        }
                        return
                    }

                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    var inputStream = FileInputStream(tempIn)
                    BitmapFactory.decodeStream(inputStream, null, options)
                    IOUtils.closeQuietly(inputStream)

                    if (options.outWidth == -1 || options.outHeight == -1) {
                        throw IOException("Not a valid image")
                    } else if (Math.max(options.outHeight, options.outWidth) >= MAX_DIMENSIONS) {
                        val sampleSize = Math.max(options.outHeight, options.outWidth).toDouble() / MAX_DIMENSIONS
                        options.inSampleSize = Math.pow(2.0, Math.floor(Math.log(sampleSize) / Math.log(2.0))).toInt()
                    }

                    options.inJustDecodeBounds = false
                    inputStream = FileInputStream(tempIn)
                    val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                    IOUtils.closeQuietly(inputStream)

                    val format = if (Build.VERSION.SDK_INT < 30)
                        Bitmap.CompressFormat.WEBP else Bitmap.CompressFormat.WEBP_LOSSY

                    var quality = 100
                    while (true) {
                        val out = FileOutputStream(tempOut)
                        bitmap!!.compress(format, quality, out)
                        IOUtils.closeQuietly(out)

                        if (tempOut!!.length() > 3L * MAX_FILE_SIZE && quality >= 45) {
                            quality -= 40
                        } else if (tempOut!!.length() > 2L * MAX_FILE_SIZE && quality >= 25) {
                            quality -= 20
                        } else if (tempOut!!.length() > MAX_FILE_SIZE && quality >= 15) {
                            quality -= 10
                        } else if (tempOut!!.length() > MAX_FILE_SIZE && quality >= 10) {
                            quality -= 5
                        } else {
                            break
                        }
                    }
                    bitmap!!.recycle()

                    stream = FileInputStream(tempOut)
                    callback.onDataReady(stream)
                    Log.d(TAG, "Compressed image from " + tempIn!!.length() / 1024
                            + " to " + tempOut!!.length() / 1024 + " kB (quality: " + quality + "%)")
                } catch (e: Throwable) {
                    e.printStackTrace()

                    try {
                        stream = FileInputStream(tempIn)
                        callback.onDataReady(stream) // Just deliver the original, non-scaled image
                    } catch (fileNotFoundException: FileNotFoundException) {
                        e.printStackTrace()
                        callback.onLoadFailed(fileNotFoundException)
                    }
                }
            }

            override fun onLoadFailed(e: Exception) {
                callback.onLoadFailed(e)
            }
        })
    }

    override fun cleanup() {
        IOUtils.closeQuietly(stream)
        FileUtils.deleteQuietly(tempIn)
        FileUtils.deleteQuietly(tempOut)
        super.cleanup()
    }
}
