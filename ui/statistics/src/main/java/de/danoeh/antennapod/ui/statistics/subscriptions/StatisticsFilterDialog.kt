package de.danoeh.antennapod.ui.statistics.subscriptions

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.util.Pair
import de.danoeh.antennapod.event.StatisticsEvent
import de.danoeh.antennapod.ui.statistics.R
import de.danoeh.antennapod.ui.statistics.StatisticsFragment
import de.danoeh.antennapod.ui.statistics.databinding.StatisticsFilterDialogBinding
import org.greenrobot.eventbus.EventBus

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatisticsFilterDialog(private val context: Context, oldestDate: Long) {
    private val prefs: SharedPreferences
    private var includeMarkedAsPlayed: Boolean
    private var timeFilterFrom: Long
    private var timeFilterTo: Long
    private val filterDatesFrom: Pair<Array<String>, Array<Long>>
    private val filterDatesTo: Pair<Array<String>, Array<Long>>

    init {
        prefs = context.getSharedPreferences(StatisticsFragment.PREF_NAME, Context.MODE_PRIVATE)
        includeMarkedAsPlayed = prefs.getBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, false)
        timeFilterFrom = prefs.getLong(StatisticsFragment.PREF_FILTER_FROM, 0L)
        timeFilterTo = prefs.getLong(StatisticsFragment.PREF_FILTER_TO, Long.MAX_VALUE)
        filterDatesFrom = makeMonthlyList(oldestDate, false)
        filterDatesTo = makeMonthlyList(oldestDate, true)
    }

    fun show() {
        val dialogBinding = StatisticsFilterDialogBinding.inflate(
                LayoutInflater.from(context))
        val builder = MaterialAlertDialogBuilder(context)
        builder.setView(dialogBinding.getRoot())
        builder.setTitle(R.string.filter)
        dialogBinding.includeMarkedCheckbox.setOnCheckedChangeListener { compoundButton, checked ->
            dialogBinding.timeToSpinner.setEnabled(!checked)
            dialogBinding.timeFromSpinner.setEnabled(!checked)
            dialogBinding.pastYearButton.setEnabled(!checked)
            dialogBinding.allTimeButton.setEnabled(!checked)
            dialogBinding.dateSelectionContainer.setAlpha(if (checked) 0.5f else 1f)
        }
        dialogBinding.includeMarkedCheckbox.setChecked(includeMarkedAsPlayed)


        val adapterFrom = ArrayAdapter(context,
                android.R.layout.simple_spinner_item, filterDatesFrom.first)
        adapterFrom.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.timeFromSpinner.setAdapter(adapterFrom)
        for (i in filterDatesFrom.second.indices) {
            if (filterDatesFrom.second[i] >= timeFilterFrom) {
                dialogBinding.timeFromSpinner.setSelection(i)
                break
            }
        }

        val adapterTo = ArrayAdapter(context,
                android.R.layout.simple_spinner_item, filterDatesTo.first)
        adapterTo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.timeToSpinner.setAdapter(adapterTo)
        for (i in filterDatesTo.second.indices) {
            if (filterDatesTo.second[i] >= timeFilterTo) {
                dialogBinding.timeToSpinner.setSelection(i)
                break
            }
        }

        dialogBinding.allTimeButton.setOnClickListener {
            dialogBinding.timeFromSpinner.setSelection(0)
            dialogBinding.timeToSpinner.setSelection(filterDatesTo.first.size - 1)
        }
        dialogBinding.pastYearButton.setOnClickListener {
            dialogBinding.timeFromSpinner.setSelection(Math.max(0, filterDatesFrom.first.size - 12))
            dialogBinding.timeToSpinner.setSelection(filterDatesTo.first.size - 2)
        }

        builder.setPositiveButton(android.R.string.ok) { dialog, which ->
            includeMarkedAsPlayed = dialogBinding.includeMarkedCheckbox.isChecked()
            if (includeMarkedAsPlayed) {
                // We do not know the date at which something was marked as played, so filtering does not make sense
                timeFilterFrom = 0
                timeFilterTo = Long.MAX_VALUE
            } else {
                timeFilterFrom = filterDatesFrom.second[dialogBinding.timeFromSpinner.getSelectedItemPosition()]
                timeFilterTo = filterDatesTo.second[dialogBinding.timeToSpinner.getSelectedItemPosition()]
            }
            prefs.edit()
                    .putBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, includeMarkedAsPlayed)
                    .putLong(StatisticsFragment.PREF_FILTER_FROM, timeFilterFrom)
                    .putLong(StatisticsFragment.PREF_FILTER_TO, timeFilterTo)
                    .apply()
            EventBus.getDefault().post(StatisticsEvent())
        }
        builder.show()
    }

    private fun makeMonthlyList(oldestDate: Long, inclusive: Boolean): Pair<Array<String>, Array<Long>> {
        val date = Calendar.getInstance()
        date.setTimeInMillis(oldestDate)
        date.set(Calendar.HOUR_OF_DAY, 0)
        date.set(Calendar.MINUTE, 0)
        date.set(Calendar.SECOND, 0)
        date.set(Calendar.MILLISECOND, 0)
        date.set(Calendar.DAY_OF_MONTH, 1)
        val names = ArrayList<String>()
        val timestamps = ArrayList<Long>()
        val skeleton = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMM yyyy")
        val dateFormat = SimpleDateFormat(skeleton, Locale.getDefault())
        while (date.getTimeInMillis() < System.currentTimeMillis()) {
            names.add(dateFormat.format(Date(date.getTimeInMillis())))
            if (!inclusive) {
                timestamps.add(date.getTimeInMillis())
            }
            if (date.get(Calendar.MONTH) == Calendar.DECEMBER) {
                date.set(Calendar.MONTH, Calendar.JANUARY)
                date.set(Calendar.YEAR, date.get(Calendar.YEAR) + 1)
            } else {
                date.set(Calendar.MONTH, date.get(Calendar.MONTH) + 1)
            }
            if (inclusive) {
                timestamps.add(date.getTimeInMillis())
            }
        }
        if (inclusive) {
            names.add(context.getString(R.string.statistics_today))
            timestamps.add(Long.MAX_VALUE)
        }
        return Pair(names.toTypedArray(), timestamps.toTypedArray())
    }
}
