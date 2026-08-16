package de.danoeh.antennapod.ui.common

import com.google.android.material.appbar.MaterialToolbar

interface NavigationToolbarActivity {
    fun setupToolbarToggle(toolbar: MaterialToolbar, displayUpArrow: Boolean)
}
