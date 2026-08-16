package de.danoeh.antennapod.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.ui.common.ToolbarActivity
import de.danoeh.antennapod.ui.glide.FastBlurTransformation

import java.util.Locale

class WidgetConfigActivity : ToolbarActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var opacitySeekBar: SeekBar
    private lateinit var opacityTextView: TextView
    private lateinit var widgetPreview: View
    private lateinit var ckPlaybackSpeed: CheckBox
    private lateinit var ckRewind: CheckBox
    private lateinit var ckFastForward: CheckBox
    private lateinit var ckSkip: CheckBox
    private lateinit var ckCoverAsBcg: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        val configIntent = getIntent()
        val extras = configIntent.getExtras()
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID)
        }

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, resultValue)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
        }

        opacityTextView = findViewById(R.id.widget_opacity_textView)
        opacityTextView.setText(String.format(Locale.getDefault(), "%d%%", 100))
        opacitySeekBar = findViewById(R.id.widget_opacity_seekBar)
        widgetPreview = findViewById(R.id.widgetLayout)
        findViewById<View>(R.id.butConfirm).setOnClickListener { confirmCreateWidget() }
        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                opacityTextView.setText(String.format(Locale.getDefault(), "%d%%", seekBar.getProgress()))
                val color = getColorWithAlpha(PlayerWidget.DEFAULT_COLOR, opacitySeekBar.getProgress())
                widgetPreview.setBackgroundColor(color)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })

        widgetPreview.findViewById<View>(R.id.txtNoPlaying).setVisibility(View.GONE)
        val title = widgetPreview.findViewById<TextView>(R.id.txtvTitle)
        title.setVisibility(View.VISIBLE)
        title.setText(R.string.app_name)
        val progress = widgetPreview.findViewById<TextView>(R.id.txtvProgress)
        progress.setVisibility(View.VISIBLE)
        progress.setText(R.string.position_default_label)

        ckPlaybackSpeed = findViewById(R.id.ckPlaybackSpeed)
        ckPlaybackSpeed.setOnClickListener { displayPreviewPanel() }
        ckRewind = findViewById(R.id.ckRewind)
        ckRewind.setOnClickListener { displayPreviewPanel() }
        ckFastForward = findViewById(R.id.ckFastForward)
        ckFastForward.setOnClickListener { displayPreviewPanel() }
        ckSkip = findViewById(R.id.ckSkip)
        ckSkip.setOnClickListener { displayPreviewPanel() }
        ckCoverAsBcg = findViewById(R.id.ckCoverAsBcg)
        ckCoverAsBcg.setOnClickListener { displayPreviewPanel() }

        setInitialState()
    }

    private fun setInitialState() {
        val prefs = getSharedPreferences(PlayerWidget.PREFS_NAME, Context.MODE_PRIVATE)
        ckPlaybackSpeed.setChecked(prefs.getBoolean(PlayerWidget.KEY_WIDGET_PLAYBACK_SPEED + appWidgetId, false))
        ckRewind.setChecked(prefs.getBoolean(PlayerWidget.KEY_WIDGET_REWIND + appWidgetId, false))
        ckFastForward.setChecked(prefs.getBoolean(PlayerWidget.KEY_WIDGET_FAST_FORWARD + appWidgetId, false))
        ckSkip.setChecked(prefs.getBoolean(PlayerWidget.KEY_WIDGET_SKIP + appWidgetId, false))
        ckCoverAsBcg.setChecked(prefs.getBoolean(PlayerWidget.KEY_WIDGET_COVER_BACKGROUND + appWidgetId, false))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val color = prefs.getInt(PlayerWidget.KEY_WIDGET_COLOR + appWidgetId, PlayerWidget.DEFAULT_COLOR)
            val opacity = Color.alpha(color) * 100 / 0xFF

            opacitySeekBar.setProgress(opacity, false)
        }
        displayPreviewPanel()
    }

    private fun displayPreviewPanel() {
        val showExtendedPreview =
                ckPlaybackSpeed.isChecked() || ckRewind.isChecked() || ckFastForward.isChecked() || ckSkip.isChecked()
        widgetPreview.findViewById<View>(R.id.extendedButtonsContainer)
                .setVisibility(if (showExtendedPreview) View.VISIBLE else View.GONE)
        widgetPreview.findViewById<View>(R.id.butPlay).setVisibility(if (showExtendedPreview) View.GONE else View.VISIBLE)
        widgetPreview.findViewById<View>(R.id.butPlaybackSpeed)
                .setVisibility(if (ckPlaybackSpeed.isChecked()) View.VISIBLE else View.GONE)
        widgetPreview.findViewById<View>(R.id.butFastForward)
                .setVisibility(if (ckFastForward.isChecked()) View.VISIBLE else View.GONE)
        widgetPreview.findViewById<View>(R.id.butSkip).setVisibility(if (ckSkip.isChecked()) View.VISIBLE else View.GONE)
        widgetPreview.findViewById<View>(R.id.butRew).setVisibility(if (ckRewind.isChecked()) View.VISIBLE else View.GONE)

        if (ckCoverAsBcg.isChecked()) {
            widgetPreview.findViewById<View>(R.id.imgvCover).setVisibility(View.GONE)
            widgetPreview.findViewById<View>(R.id.imgvBackground).setVisibility(View.VISIBLE)
            loadCover(R.id.imgvBackground, FastBlurTransformation())
            opacitySeekBar.setEnabled(false)
            opacitySeekBar.setProgress(100)
        } else {
            widgetPreview.findViewById<View>(R.id.imgvCover).setVisibility(View.VISIBLE)
            widgetPreview.findViewById<View>(R.id.imgvBackground).setVisibility(View.GONE)
            widgetPreview.findViewById<View>(R.id.widgetLayout).setBackgroundColor(PlayerWidget.DEFAULT_COLOR)
            opacitySeekBar.setEnabled(true)
            val radius = getResources().getDimensionPixelSize(R.dimen.widget_inner_radius)
            loadCover(R.id.imgvCover, RoundedCorners(radius))
        }
    }

    private fun loadCover(viewId: Int, transform: Transformation<Bitmap>) {
        val target = findViewById<ImageView>(viewId)
        Glide.with(this)
                .asBitmap()
                .load(Feed.PREFIX_GENERATIVE_COVER)
                .dontAnimate()
                .transform(FitCenter(), transform)
                .into(target)
    }

    private fun confirmCreateWidget() {
        val backgroundColor = getColorWithAlpha(PlayerWidget.DEFAULT_COLOR, opacitySeekBar.getProgress())

        val prefs = getSharedPreferences(PlayerWidget.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt(PlayerWidget.KEY_WIDGET_COLOR + appWidgetId, backgroundColor)
        editor.putBoolean(PlayerWidget.KEY_WIDGET_PLAYBACK_SPEED + appWidgetId, ckPlaybackSpeed.isChecked())
        editor.putBoolean(PlayerWidget.KEY_WIDGET_SKIP + appWidgetId, ckSkip.isChecked())
        editor.putBoolean(PlayerWidget.KEY_WIDGET_REWIND + appWidgetId, ckRewind.isChecked())
        editor.putBoolean(PlayerWidget.KEY_WIDGET_FAST_FORWARD + appWidgetId, ckFastForward.isChecked())
        editor.putBoolean(PlayerWidget.KEY_WIDGET_COVER_BACKGROUND + appWidgetId, ckCoverAsBcg.isChecked())
        editor.apply()

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
        WidgetUpdaterWorker.enqueueWork(this)
    }

    private fun getColorWithAlpha(color: Int, opacity: Int): Int {
        return (Math.round(0xFF * (0.01 * opacity)).toInt() * 0x1000000 + (color and 0xffffff))
    }
}
