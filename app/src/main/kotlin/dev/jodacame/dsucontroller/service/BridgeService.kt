// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import dev.jodacame.dsucontroller.R
import dev.jodacame.dsucontroller.settings.BridgeSettings
import dev.jodacame.dsucontroller.ui.MainActivity

/**
 * Keeps the bridge alive. Screens bind to it to read its state; while the bridge runs it is a
 * foreground service with a notification, so Android does not stop it when the app is not on
 * screen.
 */
class BridgeService : Service() {
    /** In-process handle given to the app's own screens; the service is not exported. */
    inner class LocalBinder : Binder() {
        val bridge: Bridge get() = this@BridgeService.bridge

        fun stopBridge() = this@BridgeService.stopBridge()
    }

    private lateinit var bridge: Bridge
    private lateinit var wakeLock: PowerManager.WakeLock
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        bridge = Bridge(this, BridgeSettings(this))
        bridge.onStoppedByFailure = ::leaveForeground
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent): IBinder = binder

    // The lock is held for as long as the user keeps the bridge running, which has no known end.
    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires the notification promptly after startForegroundService, even when the
        // server then fails to start.
        enterForeground()
        if (intent?.action == ACTION_START && bridge.start()) {
            wakeLock.acquire()
        } else {
            leaveForeground()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        bridge.close()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private fun stopBridge() {
        bridge.stop()
        leaveForeground()
    }

    private fun enterForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun leaveForeground() {
        if (wakeLock.isHeld) wakeLock.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setStyle(Notification.BigTextStyle().bigText(getString(R.string.notification_text)))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val ACTION_START = "dev.jodacame.dsucontroller.action.START"
        private const val CHANNEL_ID = "bridge"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "DsuController:bridge"

        /** Starts the bridge. Call from a screen that is in the foreground. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, BridgeService::class.java).setAction(ACTION_START))
        }
    }
}
