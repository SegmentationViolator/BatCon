package segmented.batcon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import java.util.concurrent.TimeUnit

class PowerReceiver : BroadcastReceiver() {
    private val batteryReceiver = BatteryReceiver()
    private lateinit var registeringContext: Context
    private lateinit var wakeLock: WakeLock

    companion object {
        fun create(context: Context): PowerReceiver {
            return PowerReceiver().let { receiver ->
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

                receiver.wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "${context.packageName}:wakelock"
                )

                receiver
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                val preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE)
                val serviceEnabled = preferences.getBoolean("service-enabled", false)

                if (!serviceEnabled || wakeLock.isHeld) return

                wakeLock.acquire(
                    TimeUnit.MILLISECONDS.convert(
                        context.resources.getInteger(R.integer.wakelock_timeout).toLong(),
                        TimeUnit.HOURS
                    )
                )

                context.registerReceiver(
                    batteryReceiver,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                registeringContext = context
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                reset()
            }
        }
    }

    fun release() {
        if (!this::registeringContext.isInitialized || !wakeLock.isHeld) return

        registeringContext.unregisterReceiver(batteryReceiver)
        wakeLock.release()
        batteryReceiver.release()
    }

    private fun reset() {
        if (!this::registeringContext.isInitialized || !wakeLock.isHeld) return

        registeringContext.unregisterReceiver(batteryReceiver)
        wakeLock.release()
        batteryReceiver.reset(registeringContext)
    }
}