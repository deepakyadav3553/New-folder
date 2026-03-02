package com.speedmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.TrafficStats
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class NetworkSpeedService : Service() {

    companion object {
        const val CHANNEL_ID = "speed_monitor_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.speedmonitor.ACTION_STOP"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTime = 0L

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    private val speedRunnable = object : Runnable {
        override fun run() {
            updateSpeed()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()

        // Foreground notification – IMPORTANCE_MIN so it stays invisible in the shade
        startForeground(NOTIFICATION_ID, buildSilentNotification())

        // Show floating overlay if permission is granted
        if (Settings.canDrawOverlays(this)) {
            showOverlay()
        }

        handler.post(speedRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(speedRunnable)
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Overlay ───────────────────────────────────────────────────────────────

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_speed, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 8
            y = 60   // sits just below the status bar
        }

        windowManager?.addView(overlayView, params)
    }

    private fun removeOverlay() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        overlayView = null
        windowManager = null
    }

    // ── Speed polling ─────────────────────────────────────────────────────────

    private fun updateSpeed() {
        val currentRxBytes = TrafficStats.getTotalRxBytes()
        val currentTxBytes = TrafficStats.getTotalTxBytes()
        val currentTime = System.currentTimeMillis()

        val elapsedSec = (currentTime - lastTime) / 1000.0
        if (elapsedSec <= 0) return

        val rxSpeed = ((currentRxBytes - lastRxBytes) / elapsedSec).toLong()
        val txSpeed = ((currentTxBytes - lastTxBytes) / elapsedSec).toLong()

        lastRxBytes = currentRxBytes
        lastTxBytes = currentTxBytes
        lastTime = currentTime

        val downText = "↓ ${SpeedFormatter.format(rxSpeed)}"
        val upText   = "↑ ${SpeedFormatter.format(txSpeed)}"

        // Update floating overlay
        handler.post {
            overlayView?.findViewById<TextView>(R.id.tvDown)?.text = downText
            overlayView?.findViewById<TextView>(R.id.tvUp)?.text   = upText
        }
    }

    // ── Notification (silent / invisible) ────────────────────────────────────

    private fun buildSilentNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, NetworkSpeedService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_speed_notification)
            .setContentTitle("Speed Monitor")
            .setContentText("Running – tap to open")
            .setContentIntent(openPi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)   // hides from status bar
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Internet Speed Monitor",
            NotificationManager.IMPORTANCE_MIN          // no sound, no status-bar icon
        ).apply {
            description = "Required to keep the speed service running"
            setShowBadge(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
