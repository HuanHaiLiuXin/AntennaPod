package de.danoeh.antennapod.ui.echo.background

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import de.danoeh.antennapod.ui.echo.EchoConfig
import de.danoeh.antennapod.ui.echo.R
import java.util.ArrayList

class FinalShareBackground(context: Context,
                           private val favoritePodNames: ArrayList<String>,
                           private val favoritePodImages: ArrayList<Drawable>) : BubbleBackground(context) {
    companion object {
        private val COVER_POSITIONS = arrayOf(floatArrayOf(0.0f, 0.0f),
                floatArrayOf(0.4f, 0.0f), floatArrayOf(0.4f, 0.2f), floatArrayOf(0.6f, 0.2f), floatArrayOf(0.8f, 0.2f))
    }

    private val paintTextMain: Paint
    private val paintCoverBorder: Paint
    private val heading: String
    private val year: String
    private val logo: Drawable?
    private val typefaceNormal: Typeface?
    private val typefaceBold: Typeface?

    init {
        this.heading = context.getString(R.string.echo_share_heading)
        this.logo = AppCompatResources.getDrawable(context, R.drawable.echo)
        this.year = EchoConfig.RELEASE_YEAR.toString()
        typefaceNormal = ResourcesCompat.getFont(context, R.font.sarabun_regular)
        typefaceBold = ResourcesCompat.getFont(context, R.font.sarabun_semi_bold)
        paintTextMain = Paint()
        paintTextMain.setColor(0xffffffff.toInt())
        paintTextMain.setFlags(Paint.ANTI_ALIAS_FLAG)
        paintTextMain.setStyle(Paint.Style.FILL)
        paintCoverBorder = Paint()
        paintCoverBorder.setColor(0xffffffff.toInt())
        paintCoverBorder.setFlags(Paint.ANTI_ALIAS_FLAG)
        paintCoverBorder.setStyle(Paint.Style.FILL)
        paintCoverBorder.setAlpha(70)
    }

    override fun drawInner(canvas: Canvas, innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float) {
        paintTextMain.setTextAlign(Paint.Align.CENTER)
        paintTextMain.setTypeface(typefaceBold)
        val headingSize = innerBoxSize / 14
        paintTextMain.setTextSize(headingSize)
        canvas.drawText(heading, innerBoxX + 0.5f * innerBoxSize, innerBoxY + headingSize, paintTextMain)
        paintTextMain.setTextSize(0.12f * innerBoxSize)
        canvas.drawText(year, innerBoxX + 0.8f * innerBoxSize, innerBoxY + 0.25f * innerBoxSize, paintTextMain)

        var fontSizePods = innerBoxSize / 18 // First one only
        var textY = innerBoxY + 0.62f * innerBoxSize
        for (i in favoritePodNames.indices) {
            val coverSize = if (i == 0) 0.4f * innerBoxSize else 0.2f * innerBoxSize
            val coverX = COVER_POSITIONS[i][0]
            val coverY = COVER_POSITIONS[i][1]
            val logo1Pos = RectF(innerBoxX + coverX * innerBoxSize,
                    innerBoxY + (coverY + 0.12f) * innerBoxSize,
                    innerBoxX + coverX * innerBoxSize + coverSize,
                    innerBoxY + (coverY + 0.12f) * innerBoxSize + coverSize)
            logo1Pos.inset(0.01f * innerBoxSize, 0.01f * innerBoxSize)
            val radius = if (i == 0) coverSize / 16 else coverSize / 8
            canvas.drawRoundRect(logo1Pos, radius, radius, paintCoverBorder)
            logo1Pos.inset(0.003f * innerBoxSize, 0.003f * innerBoxSize)
            val pos = Rect()
            logo1Pos.round(pos)
            if (favoritePodImages.size > i) {
                favoritePodImages.get(i).setBounds(pos)
                favoritePodImages.get(i).draw(canvas)
            } else {
                canvas.drawText(" ...", pos.left.toFloat(), pos.centerY().toFloat(), paintTextMain)
            }

            paintTextMain.setTextAlign(Paint.Align.CENTER)
            paintTextMain.setTextSize(fontSizePods)
            val numberWidth = 0.06f * innerBoxSize
            canvas.drawText((i + 1).toString() + ".", innerBoxX + numberWidth / 2, textY, paintTextMain)
            paintTextMain.setTextAlign(Paint.Align.LEFT)
            val ellipsizedTitle = ellipsize(favoritePodNames.get(i), paintTextMain, innerBoxSize - numberWidth)
            canvas.drawText(ellipsizedTitle, innerBoxX + numberWidth, textY, paintTextMain)
            fontSizePods = innerBoxSize / 24 // Starting with second text is smaller
            textY += 1.3f * fontSizePods
            paintTextMain.setTypeface(typefaceNormal)
        }

        val ratio = 1.0 * logo!!.getIntrinsicHeight() / logo.getIntrinsicWidth()
        logo.setBounds((innerBoxX + 0.1 * innerBoxSize).toInt(),
                (innerBoxY + innerBoxSize - 0.8 * innerBoxSize * ratio).toInt(),
                (innerBoxX + 0.9 * innerBoxSize).toInt(),
                (innerBoxY + innerBoxSize).toInt())
        logo.draw(canvas)
    }

    internal fun ellipsize(string: String, paint: Paint, maxWidth: Float): String {
        var ellipsized = string
        if (paint.measureText(ellipsized) <= maxWidth) {
            return ellipsized
        }
        while (paint.measureText(ellipsized + "…") > maxWidth || ellipsized.endsWith(" ")) {
            ellipsized = ellipsized.substring(0, ellipsized.length - 1)
        }
        return ellipsized + "…"
    }
}
