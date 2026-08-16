package de.danoeh.antennapod.ui.screen.playback.video

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

class PictureInPictureUtil private constructor() {
    companion object {
        @JvmStatic
        fun supportsPictureInPicture(activity: Activity): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val packageManager = activity.getPackageManager()
                return packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            }
            return false
        }

        @JvmStatic
        fun isInPictureInPictureMode(activity: Activity): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && supportsPictureInPicture(activity)) {
                return activity.isInPictureInPictureMode()
            }
            return false
        }
    }
}
