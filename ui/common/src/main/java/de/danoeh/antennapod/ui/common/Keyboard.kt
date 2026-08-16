package de.danoeh.antennapod.ui.common

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

class Keyboard private constructor() {
    companion object {
        @JvmStatic
        fun show(context: Context, view: View) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            if (imm != null) {
                imm.showSoftInput(view, 0)
            }
        }

        @JvmStatic
        fun hide(activity: Activity) {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            var view = activity.getCurrentFocus()
            //If no view currently has focus, create a new one, just so we can grab a window token from it
            if (view == null) {
                view = View(activity)
            }
            view.clearFocus()
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0)
        }
    }
}
