package de.danoeh.antennapod.ui.preferences.screen

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.transition.MaterialSharedAxis
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.ui.preferences.R

abstract class AnimatedPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEnterTransition(MaterialSharedAxis(MaterialSharedAxis.X, true))
        setReturnTransition(MaterialSharedAxis(MaterialSharedAxis.X, false))
        setExitTransition(MaterialSharedAxis(MaterialSharedAxis.X, true))
        setReenterTransition(MaterialSharedAxis(MaterialSharedAxis.X, false))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ThemeUtils.getColorFromAttr(getContext()!!, R.attr.colorSurface))
    }
}
