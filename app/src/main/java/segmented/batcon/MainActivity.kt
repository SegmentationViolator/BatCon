package segmented.batcon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private lateinit var openDocument: ActivityResultLauncher<Array<String>>
    private lateinit var onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val preferences = getSharedPreferences("config", Context.MODE_PRIVATE)

        val informationButton = findViewById<ImageButton>(R.id.information_button)
        val soundSelector = findViewById<TextView>(R.id.sound_selector)
        val startButton = findViewById<FloatingActionButton>(R.id.start_button)
        val stopButton = findViewById<FloatingActionButton>(R.id.stop_button)
        val thresholdBar = findViewById<SeekBar>(R.id.threshold_bar)
        val thresholdDescription = findViewById<TextView>(R.id.threshold_description)
        val thresholdLabel = findViewById<TextView>(R.id.threshold_label)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                resources.getString(R.string.notification_channel),
                "Default",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult

            val soundUri = preferences.getString("sound-uri", "null")
            if (soundUri != null) contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            contentResolver.query(uri, null, null, null)?.use { cursor ->
                cursor.moveToFirst()

                soundSelector.text =
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                        .also { name ->
                            preferences.edit()
                                .putString(
                                    "sound-name",
                                    name
                                )
                                .putString("sound-uri", uri.toString())
                                .apply()
                        }
            }
        }

        onSharedPreferenceChangeListener =
            OnSharedPreferenceChangeListener { sharedPreferences, key ->
                if (key == "service-enabled") {
                    startButton.isVisible =
                        !sharedPreferences.getBoolean("service-enabled", false)
                            .also { isServiceEnabled ->
                                stopButton.isVisible = isServiceEnabled
                                thresholdBar.isEnabled = !isServiceEnabled
                                soundSelector.isClickable = !isServiceEnabled
                            }
                }
            }

        informationButton.setOnClickListener { view ->
            val popupView = layoutInflater.inflate(R.layout.popup, null)
            val popupLinks = popupView.findViewById<TextView>(R.id.popup_links)

            popupLinks.movementMethod = LinkMovementMethod.getInstance()

            val popupWindow = PopupWindow(
                popupView,
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                true
            )

            popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);

            popupView.findViewById<ImageButton>(R.id.dismiss_button).setOnClickListener {
                popupWindow.dismiss()
            }
        }

        soundSelector.setOnClickListener {
            openDocument.launch(arrayOf("audio/*"))
        }

        startButton.setOnClickListener {
            preferences.edit()
                .putBoolean("service-enabled", true)
                .apply()

            startForegroundService(
                Intent(applicationContext, BatteryMonitor::class.java)
            )
        }

        stopButton.setOnClickListener {
            preferences.edit()
                .putBoolean("service-enabled", false)
                .apply()

            stopService(Intent(applicationContext, BatteryMonitor::class.java))
        }

        thresholdBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                thresholdDescription.text =
                    resources.getString(R.string.threshold_description, progress)
                thresholdLabel.text = resources.getString(R.string.threshold_label, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    preferences.edit()
                        .putInt("threshold", it.progress)
                        .commit()
                }
            }
        })

        soundSelector.text =
            preferences.getString("sound-name", resources.getString(R.string.sound))
        startButton.isVisible =
            !preferences.getBoolean("service-enabled", false).also { isServiceEnabled ->
                stopButton.isVisible = isServiceEnabled
                thresholdBar.isEnabled = !isServiceEnabled
                soundSelector.isEnabled = !isServiceEnabled
            }
        thresholdBar.progress =
            preferences.getInt("threshold", resources.getInteger(R.integer.threshold))
                .also { progress ->
                    thresholdDescription.text =
                        resources.getString(R.string.threshold_description, progress)
                    thresholdLabel.text = resources.getString(R.string.threshold_label, progress)
                }
    }

    override fun onPause() {
        super.onPause()

        val preferences = getSharedPreferences("config", Context.MODE_PRIVATE)
        preferences.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    override fun onResume() {
        super.onResume()

        val preferences = getSharedPreferences("config", Context.MODE_PRIVATE)
        preferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }
}