package com.speedmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Foreground service that polls TrafficStats every second and updates
 * the persistent notification with current download / upload speeds.
 */
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

        // Initialize baseline counts
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()

        startForeground(NOTIFICATION_ID, buildNotification("↓ 0 B/s", "↑ 0 B/s"))
        handler.post(speedRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(speedRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Private helpers ──────────────────────────────────────────────────────

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

        val notification = buildNotification(downText, upText)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(downText: String, upText: String): Notification {
        // Tap notification → open app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action
        val stopIntent = Intent(this, NetworkSpeedService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show live download speed as the status bar icon text
        val speedIcon = speedTextIcon(downText.removePrefix("↓ ").trim())

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(speedIcon)
            .setContentTitle("$downText   $upText")
            .setContentText("Tap to open · Internet Speed Monitor")
            .setContentIntent(openPi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .build()
    }

    /**
     * Renders speed text (e.g. "1.2 MB/s") as a white-on-transparent bitmap
     * so the live speed appears directly in the status bar icon slot.
     */
    private fun speedTextIcon(speed: String): IconCompat {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Split "1.2 MB/s" → value="1.2", unit="MB/s"
        val parts = speed.trim().split(" ")
        val value = parts.getOrElse(0) { speed }
        val unit  = parts.getOrElse(1) { "" }

        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.WHITE
            typeface  = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize  = when {
                value.length >= 5 -> 26f
                value.length >= 4 -> 30f
                else              -> 36f
            }
        }
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.WHITE
            typeface  = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
            textSize  = 20f
        }

        // Centre the two lines vertically
        val totalHeight = valuePaint.textSize + 4f + unitPaint.textSize
        val topY = (size - totalHeight) / 2f + valuePaint.textSize

        canvas.drawText(value, size / 2f, topY, valuePaint)
        if (unit.isNotEmpty()) {
            canvas.drawText(unit, size / 2f, topY + 4f + unitPaint.textSize, unitPaint)
        }

        return IconCompat.createWithBitmap(bitmap)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Internet Speed Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows real-time internet download and upload speeds"
            setShowBadge(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
