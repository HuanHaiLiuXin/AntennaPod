package de.danoeh.antennapod.ui.screen.home.settingsdialog

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.text.TextUtils
import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.screen.home.HomeFragment
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap

object HomePreferences {
    private const val PREF_HIDDEN_SECTIONS = "PrefHomeSectionsString"
    private const val PREF_SECTION_ORDER = "PrefHomeSectionOrder"
    private var sectionTagToName: HashMap<String, String>? = null

    fun getNameFromTag(context: Context, sectionTag: String): String {
        if (sectionTagToName == null) {
            initializeMap(context)
        }
        return sectionTagToName!![sectionTag]!!
    }

    private fun initializeMap(context: Context) {
        val resources: Resources = context.resources
        val sectionLabels = resources.getStringArray(R.array.home_section_titles)
        val sectionTags = resources.getStringArray(R.array.home_section_tags)
        sectionTagToName = HashMap(sectionTags.size)
        for (i in sectionLabels.indices) {
            sectionTagToName!![sectionTags[i]] = sectionLabels[i]
        }
    }

    fun getHiddenSectionTags(context: Context): List<String> {
        return getListPreference(context, PREF_HIDDEN_SECTIONS)
    }

    fun getSortedSectionTags(context: Context): List<String> {
        val sectionTagOrder = getListPreference(context, PREF_SECTION_ORDER)
        val hiddenSectionTags = getHiddenSectionTags(context)
        val sectionTags = context.resources.getStringArray(R.array.home_section_tags)
        Arrays.sort(sectionTags) { a: String, b: String -> Integer.signum(
                indexOfOrMaxValue(sectionTagOrder, a) - indexOfOrMaxValue(sectionTagOrder, b)) }

        val finalSectionTags: MutableList<String> = ArrayList()
        for (sectionTag in sectionTags) {
            if (hiddenSectionTags.contains(sectionTag)) {
                continue
            }
            finalSectionTags.add(sectionTag)
        }
        return finalSectionTags
    }

    private fun getListPreference(context: Context, preferenceKey: String): List<String> {
        val prefs: SharedPreferences = context.getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE)
        val hiddenSectionsString = prefs.getString(preferenceKey, "")
        return ArrayList(Arrays.asList(*TextUtils.split(hiddenSectionsString, ",")))
    }

    private fun indexOfOrMaxValue(haystack: List<String>, needle: String): Int {
        val index = haystack.indexOf(needle)
        return if (index == -1) Int.MAX_VALUE else index
    }

    fun saveChanges(context: Context, hiddenSections: List<String>, sectionOrder: List<String>) {
        val prefs: SharedPreferences = context.getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        edit.putString(PREF_HIDDEN_SECTIONS, TextUtils.join(",", hiddenSections))
        edit.putString(PREF_SECTION_ORDER, TextUtils.join(",", sectionOrder))
        edit.apply()
    }
}
