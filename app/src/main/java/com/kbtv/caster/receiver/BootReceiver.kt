package com.kbtv.caster.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.kbtv.caster.service.CastCoordinatorService
import timber.log.Timber

/**
 * BroadcastReceiver that starts the casting service when the device boots
 * Automatically starts DLNA/UPnP service for immediate device discovery
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Timber.d("BootReceiver received action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.REBOOT" -> {
                Timber.i("Device booted, starting casting service...")
                startCastingService(context)
            }
        }
    }

    /**
     * Start the casting service in the background after a short delay for WiFi readiness
     */
    private fun startCastingService(context: Context) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val serviceIntent = Intent(context, CastCoordinatorService::class.java).apply {
                    action = CastCoordinatorService.ACTION_START
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                Timber.i("Casting service started automatically")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start casting service automatically")
            }
        }, 5000)
    }
}
