package de.danoeh.antennapod.ui.common

import android.content.Context
import androidx.annotation.AttrRes
import android.util.TypedValue
import androidx.core.content.ContextCompat

class ThemeUtils private constructor() {
    companion object {
        @JvmStatic
        fun getColorFromAttr(context: Context, @AttrRes attr: Int): Int {
            val typedValue = TypedValue()
            context.getTheme().resolveAttribute(attr, typedValue, true)
            if (typedValue.resourceId != 0) {
                return ContextCompat.getColor(context, typedValue.resourceId)
            }
            return typedValue.data
        }

        @JvmStatic
        fun getDrawableFromAttr(context: Context, @AttrRes attr: Int): Int {
            val typedValue = TypedValue()
            context.getTheme().resolveAttribute(attr, typedValue, true)
            return typedValue.resourceId
        }
    }
}
