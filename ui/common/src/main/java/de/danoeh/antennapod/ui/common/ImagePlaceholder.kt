package de.danoeh.antennapod.ui.common

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

import androidx.core.content.ContextCompat


class ImagePlaceholder {
    companion object {
        @JvmStatic
        fun getDrawable(context: Context, cornerRadius: Float): Drawable {
            val drawable = GradientDrawable()
            drawable.setShape(GradientDrawable.RECTANGLE)
            val color = ContextCompat.getColor(context, R.color.light_gray)
            drawable.setColor(color)
            drawable.setCornerRadius(cornerRadius)
            return drawable
        }
    }
}
