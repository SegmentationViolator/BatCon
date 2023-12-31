package segmented.batcon

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder

class BatteryMonitor : Service() {
    private lateinit var powerReceiver: PowerReceiver

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val preferences = getSharedPreferences("config", Context.MODE_PRIVATE)
        val threshold =
            preferences.getInt("threshold", resources.getInteger(R.integer.threshold))

        if (!preferences.getBoolean(
                "service-enabled",
                false
            ) || this::powerReceiver.isInitialized
        ) {
            return START_NOT_STICKY
        }

        val pendingIntent = Intent(applicationContext, MainActivity::class.java).let {
            PendingIntent.getActivity(
                applicationContext,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification: Notification = Notification.Builder(
            applicationContext,
            resources.getString(R.string.notification_channel)
        )
            .setContentIntent(pendingIntent)
            .setContentTitle(resources.getString(R.string.service_title))
            .setContentText(resources.getString(R.string.threshold_description, threshold))
            .setSmallIcon(R.drawable.icon)
            .build()

        startForeground(R.id.notification, notification)

        powerReceiver = PowerReceiver.create(applicationContext)

        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_POWER_CONNECTED)
        intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        registerReceiver(powerReceiver, intentFilter)

        return START_STICKY
    }

    override fun onDestroy() {
        if (!this::powerReceiver.isInitialized) return

        powerReceiver.release()
        unregisterReceiver(powerReceiver)

        val preferences = getSharedPreferences("config", Context.MODE_PRIVATE)

        preferences.edit()
            .putBoolean("service-enabled", false)
            .apply()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}