package de.danoeh.antennapod.ui.preferences.preference

import android.content.Context
import android.util.AttributeSet
import androidx.cardview.widget.CardView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.elevation.SurfaceColors
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.R
import de.danoeh.antennapod.ui.preferences.databinding.ThemePreferenceBinding

class ThemePreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
    private var viewBinding: ThemePreferenceBinding? = null

    init {
        setLayoutResource(R.layout.theme_preference)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        viewBinding = ThemePreferenceBinding.bind(holder.itemView)
        updateUi()
    }

    internal fun updateThemeCard(card: CardView, theme: UserPreferences.ThemePreference) {
        val density = getContext().getResources().getDisplayMetrics().density
        val surfaceColor = SurfaceColors.getColorForElevation(getContext(), 1 * density)
        val surfaceColorActive = SurfaceColors.getColorForElevation(getContext(), 32 * density)
        val activeTheme = UserPreferences.getTheme()
        card.setCardBackgroundColor(if (theme == activeTheme) surfaceColorActive else surfaceColor)
        card.setOnClickListener {
            UserPreferences.setTheme(theme)
            if (getOnPreferenceChangeListener() != null) {
                getOnPreferenceChangeListener()!!.onPreferenceChange(this, UserPreferences.getTheme())
            }
            updateUi()
        }
    }

    internal fun updateUi() {
        updateThemeCard(viewBinding!!.themeSystemCard, UserPreferences.ThemePreference.SYSTEM)
        updateThemeCard(viewBinding!!.themeLightCard, UserPreferences.ThemePreference.LIGHT)
        updateThemeCard(viewBinding!!.themeDarkCard, UserPreferences.ThemePreference.DARK)
    }
}
