package de.danoeh.antennapod.ui.common

import android.content.Context
import android.content.res.TypedArray
import androidx.appcompat.widget.AppCompatImageView
import android.util.AttributeSet

/**
 * From http://stackoverflow.com/a/19449488/6839
 */
class SquareImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
        AppCompatImageView(context, attrs, defStyle) {
    companion object {
        const val DIRECTION_WIDTH = 0
        const val DIRECTION_HEIGHT = 1
        const val DIRECTION_MINIMUM = 2
    }

    private var direction = DIRECTION_WIDTH

    init {
        if (attrs != null) {
            loadAttrs(context, attrs)
        }
    }

    private fun loadAttrs(context: Context, attrs: AttributeSet) {
        val a: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.SquareImageView)
        direction = a.getInt(R.styleable.SquareImageView_direction, DIRECTION_WIDTH)
        a.recycle()
    }

    fun setDirection(direction: Int) {
        this.direction = direction
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        when (direction) {
            DIRECTION_MINIMUM -> {
                val size = Math.min(getMeasuredWidth(), getMeasuredHeight())
                setMeasuredDimension(size, size)
            }
            DIRECTION_HEIGHT ->
                setMeasuredDimension(getMeasuredHeight(), getMeasuredHeight())
            else ->
                setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth())
        }
    }

}
