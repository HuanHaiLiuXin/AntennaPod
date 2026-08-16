package de.danoeh.antennapod.ui.screen.playback.audio

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.TextView.BufferType
import androidx.appcompat.widget.AppCompatTextView

class NoRelayoutTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
                                                   defStyleAttr: Int = 0) :
        AppCompatTextView(context, attrs, defStyleAttr) {
    private var requestLayoutEnabled = true
    private var maxTextLength = 0f

    override fun requestLayout() {
        if (requestLayoutEnabled) {
            super.requestLayout()
        }
        requestLayoutEnabled = false
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        requestLayoutEnabled = true
        super.onRestoreInstanceState(state)
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        val textLength = getPaint().measureText(text!!.toString())
        if (textLength > maxTextLength) {
            maxTextLength = textLength
            requestLayoutEnabled = true
        }
        super.setText(text, type)
    }
}
