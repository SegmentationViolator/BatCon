package segmented.batcon

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings

class BatteryReceiver : BroadcastReceiver() {
    private lateinit var mediaPlayer: MediaPlayer
    private var isJobDone = false

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_CHANGED || isJobDone) return

        val preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE)
        val serviceEnabled = preferences.getBoolean("service-enabled", false)
        val threshold =
            preferences.getInt(
                "threshold",
                context.resources.getInteger(R.integer.threshold)
            )

        val status = intent.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            -1
        )

        if (!serviceEnabled || !(status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)) {
            isJobDone = true
            return
        }

        val percentage = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            .toFloat() / intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            .toFloat() * 100.0

        if (percentage >= threshold) {
            isJobDone = true

            mediaPlayer = MediaPlayer.create(
                context,
                Uri.parse(
                    preferences.getString(
                        "sound-uri",
                        Settings.System.DEFAULT_RINGTONE_URI.toString()
                    )
                )
            )

            val pendingIntent = Intent(context, MainActivity::class.java).let {
                PendingIntent.getActivity(
                    context,
                    0,
                    it,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

            val notification: Notification = Notification.Builder(
                context,
                context.resources.getString(R.string.notification_channel)
            )
                .setContentIntent(pendingIntent)
                .setContentTitle(context.resources.getString(R.string.notification_title))
                .setContentText(
                    context.resources.getString(
                        R.string.notification_description,
                        threshold
                    )
                )
                .setSmallIcon(R.drawable.icon)
                .build()

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(R.id.notification, notification)

            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener { player ->
                val serviceNotification: Notification = Notification.Builder(
                    context,
                    context.resources.getString(R.string.notification_channel)
                )
                    .setContentIntent(pendingIntent)
                    .setContentTitle(context.resources.getString(R.string.service_title))
                    .setContentText(
                        context.resources.getString(
                            R.string.threshold_description,
                            threshold
                        )
                    )
                    .setSmallIcon(R.drawable.icon)
                    .build()

                notificationManager.notify(R.id.notification, serviceNotification)
                player.release()
            }
        }
    }

    fun release() {
        if (!this::mediaPlayer.isInitialized || !isJobDone) return

        isJobDone = false
        mediaPlayer.release()
    }

    fun reset(context: Context) {
        release()

        val preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE)
        val threshold =
            preferences.getInt(
                "threshold",
                context.resources.getInteger(R.integer.threshold)
            )

        val pendingIntent = Intent(context, MainActivity::class.java).let { intent ->
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        val serviceNotification: Notification = Notification.Builder(
            context,
            context.resources.getString(R.string.notification_channel)
        )
            .setContentIntent(pendingIntent)
            .setContentTitle(context.resources.getString(R.string.service_title))
            .setContentText(
                context.resources.getString(
                    R.string.threshold_description,
                    threshold
                )
            )
            .setSmallIcon(R.drawable.icon)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(R.id.notification, serviceNotification)
    }
}