package de.danoeh.antennapod.ui.screen.playback.audio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.common.ThemeUtils

class ChapterSeekBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
                                               defStyle: Int = 0) :
        AppCompatSeekBar(context, attrs, defStyle) {

    private var top = 0f
    private var width = 0f
    private var center = 0f
    private var bottom = 0f
    private var density = 0f
    private var progressPrimary = 0f
    private var progressSecondary = 0f
    private var dividerPos: FloatArray? = null
    private var isHighlighted = false
    private val paintBackground = Paint()
    private val paintProgressPrimary = Paint()

    init {
        init(context)
    }

    private fun init(context: Context) {
        setBackground(null) // Removes the thumb shadow
        dividerPos = null
        density = context.getResources().getDisplayMetrics().density

        paintBackground.setColor(ThemeUtils.getColorFromAttr(getContext(), R.attr.colorSurfaceVariant))
        paintBackground.setAlpha(128)
        paintProgressPrimary.setColor(ThemeUtils.getColorFromAttr(getContext(), R.attr.colorPrimary))
    }

    /**
     * Sets the relative positions of the chapter dividers.
     * @param dividerPos of the chapter dividers relative to the duration of the media.
     */
    fun setDividerPos(dividerPos: FloatArray?) {
        if (dividerPos != null) {
            this.dividerPos = FloatArray(dividerPos.size + 2)
            this.dividerPos!![0] = 0f
            System.arraycopy(dividerPos, 0, this.dividerPos, 1, dividerPos.size)
            this.dividerPos!![this.dividerPos!!.size - 1] = 1f
        } else {
            this.dividerPos = null
        }
        invalidate()
    }

    fun highlightCurrentChapter() {
        isHighlighted = true
        Handler(Looper.getMainLooper()).postDelayed({
            isHighlighted = false
            invalidate()
        }, 1000)
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        center = (getBottom() - getPaddingBottom() - getTop() - getPaddingTop()) / 2.0f
        top = center - density * 1.5f
        bottom = center + density * 1.5f
        width = (getRight() - getPaddingRight() - getLeft() - getPaddingLeft()).toFloat()
        progressSecondary = getSecondaryProgress().toFloat() / getMax().toFloat() * width
        progressPrimary = getProgress().toFloat() / getMax().toFloat() * width

        if (dividerPos == null) {
            drawProgress(canvas)
        } else {
            drawProgressChapters(canvas)
        }
        drawThumb(canvas)
    }

    private fun drawProgress(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.translate(getPaddingLeft().toFloat(), getPaddingTop().toFloat())
        canvas.drawRect(0f, top, width, bottom, paintBackground)
        canvas.drawRect(0f, top, progressSecondary, bottom, paintBackground)
        canvas.drawRect(0f, top, progressPrimary, bottom, paintProgressPrimary)
        canvas.restoreToCount(saveCount)
    }

    private fun drawProgressChapters(canvas: Canvas) {
        val saveCount = canvas.save()
        var currChapter = 1
        val chapterMargin = density * 1.2f
        val topExpanded = center - density * 2.0f
        val bottomExpanded = center + density * 2.0f

        canvas.translate(getPaddingLeft().toFloat(), getPaddingTop().toFloat())

        for (i in 1 until dividerPos!!.size) {
            val right = dividerPos!![i] * width - chapterMargin
            val left = dividerPos!![i - 1] * width
            val rightCurr = dividerPos!![currChapter] * width - chapterMargin
            val leftCurr = dividerPos!![currChapter - 1] * width

            canvas.drawRect(left, top, right, bottom, paintBackground)

            if (progressSecondary > 0 && progressSecondary < width) {
                if (right < progressSecondary) {
                    canvas.drawRect(left, top, right, bottom, paintBackground)
                } else if (progressSecondary > left) {
                    canvas.drawRect(left, top, progressSecondary, bottom, paintBackground)
                }
            }

            if (right < progressPrimary) {
                currChapter = i + 1
                canvas.drawRect(left, top, right, bottom, paintProgressPrimary)
            } else if (isHighlighted || isPressed()) {
                canvas.drawRect(leftCurr, topExpanded, rightCurr, bottomExpanded, paintBackground)
                canvas.drawRect(leftCurr, topExpanded, progressPrimary, bottomExpanded, paintProgressPrimary)
            } else {
                canvas.drawRect(leftCurr, top, progressPrimary, bottom, paintProgressPrimary)
            }
        }
        canvas.restoreToCount(saveCount)
    }

    private fun drawThumb(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.translate((getPaddingLeft() - getThumbOffset()).toFloat(), getPaddingTop().toFloat())
        getThumb().draw(canvas)
        canvas.restoreToCount(saveCount)
    }
}
