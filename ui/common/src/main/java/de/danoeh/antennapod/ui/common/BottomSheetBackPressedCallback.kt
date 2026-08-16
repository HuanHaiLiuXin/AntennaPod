package de.danoeh.antennapod.ui.common

import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior

class BottomSheetBackPressedCallback(enabled: Boolean, private val sheetBehavior: BottomSheetBehavior<*>,
                                     private val view: View) : OnBackPressedCallback(enabled) {

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        val height = view.getHeight().toFloat()
        if (height <= 0f) {
            return
        }
        view.setTranslationY(height * 0.2f * backEvent.progress)
    }

    override fun handleOnBackPressed() {
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)
        val from = view.getTranslationY()
        val animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                super.applyTransformation(interpolatedTime, t)
                view.setTranslationY((1.0f - interpolatedTime) * from)
            }
        }
        animation.setDuration(100)
        view.startAnimation(animation)
    }

    override fun handleOnBackCancelled() {
        view.setTranslationY(0f)
    }
}
