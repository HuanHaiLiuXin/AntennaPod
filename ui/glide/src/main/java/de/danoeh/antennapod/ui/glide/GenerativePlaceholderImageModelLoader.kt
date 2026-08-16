package de.danoeh.antennapod.ui.glide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import de.danoeh.antennapod.model.feed.Feed

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Random

class GenerativePlaceholderImageModelLoader : ModelLoader<String, InputStream> {

    class Factory : ModelLoaderFactory<String, InputStream> {
        override fun build(unused: MultiModelLoaderFactory): ModelLoader<String, InputStream> {
            return GenerativePlaceholderImageModelLoader()
        }

        override fun teardown() {
            // Do nothing.
        }
    }

    override fun buildLoadData(model: String, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream> {
        return ModelLoader.LoadData(ObjectKey(model), EmbeddedImageFetcher(model, width, height))
    }

    override fun handles(model: String): Boolean {
        return model.startsWith(Feed.PREFIX_GENERATIVE_COVER)
    }

    internal class EmbeddedImageFetcher(private val model: String,
                                        private val width: Int,
                                        private val height: Int) : DataFetcher<InputStream> {
        companion object {
            private val PALETTES = intArrayOf(0xff78909c.toInt(), 0xffff6f00.toInt(), 0xff388e3c.toInt(),
                    0xff00838f.toInt(), 0xff7b1fa2.toInt(), 0xffb71c1c.toInt(), 0xff2196f3.toInt())
        }

        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val generator = Random(model.hashCode().toLong())
            val lineGridSteps = 4 + generator.nextInt(4)
            val slope = width / 4
            val shadowWidth = width * 0.01f
            val lineDistance = width.toFloat() / (lineGridSteps - 2)
            val baseColor = PALETTES[generator.nextInt(PALETTES.size)]

            val paint = Paint()
            var color = randomShadeOfGrey(generator)
            paint.setColor(color)
            paint.setStrokeWidth(lineDistance)
            paint.setColorFilter(PorterDuffColorFilter(baseColor, PorterDuff.Mode.MULTIPLY))
            val paintShadow = Paint()
            paintShadow.setColor(0xff000000.toInt())
            paintShadow.setStrokeWidth(lineDistance)

            val forcedColorChange = 1 + generator.nextInt(lineGridSteps - 2)
            for (i in lineGridSteps - 1 downTo 0) {
                val linePos = (i - 0.5f) * lineDistance
                val switchColor = generator.nextFloat() < 0.3f || i == forcedColorChange
                if (switchColor) {
                    var newColor = color
                    while (newColor == color) {
                        newColor = randomShadeOfGrey(generator)
                    }
                    color = newColor
                    paint.setColor(newColor)
                    canvas.drawLine(linePos + slope + shadowWidth, -slope.toFloat(),
                            linePos - slope + shadowWidth, (height + slope).toFloat(), paintShadow)
                }
                canvas.drawLine(linePos + slope, -slope.toFloat(),
                        linePos - slope, (height + slope).toFloat(), paint)
            }

            val gradientPaint = Paint()
            paint.setDither(true)
            gradientPaint.setShader(LinearGradient(0f, 0f, 0f, height.toFloat(), 0x00000000, 0x55000000, Shader.TileMode.CLAMP))
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val inputStream: InputStream = ByteArrayInputStream(baos.toByteArray())
            callback.onDataReady(inputStream)
        }

        private fun randomShadeOfGrey(generator: Random): Int {
            return (0xff777777 + 0x222222 * generator.nextInt(5)).toInt()
        }

        override fun cleanup() {
            // nothing to clean up
        }

        override fun cancel() {
            // cannot cancel
        }

        override fun getDataClass(): Class<InputStream> {
            return InputStream::class.java
        }

        override fun getDataSource(): DataSource {
            return DataSource.LOCAL
        }
    }
}
