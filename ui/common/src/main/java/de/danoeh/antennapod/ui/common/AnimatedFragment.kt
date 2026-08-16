package de.danoeh.antennapod.ui.common

import android.os.Bundle

import androidx.fragment.app.Fragment

import com.google.android.material.transition.MaterialSharedAxis

abstract class AnimatedFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setEnterTransition(MaterialSharedAxis(MaterialSharedAxis.X, true))
        setReturnTransition(MaterialSharedAxis(MaterialSharedAxis.X, false))
        setExitTransition(MaterialSharedAxis(MaterialSharedAxis.X, true))
        setReenterTransition(MaterialSharedAxis(MaterialSharedAxis.X, false))
    }
}
