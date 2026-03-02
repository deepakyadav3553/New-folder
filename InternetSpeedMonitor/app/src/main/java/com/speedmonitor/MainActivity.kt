package com.speedmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.speedmonitor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRunning = false

    // Step 1 – overlay ("display over other apps") permission
    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                requestNotificationPermissionThenStart()
            } else {
                Toast.makeText(
                    this,
                    "\"Display over other apps\" permission is required to show the speed widget.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    // Step 2 – notification permission (Android 13+)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startMonitorService()   // start regardless; overlay is the main display
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnToggle.setOnClickListener {
            if (isRunning) stopMonitorService() else checkOverlayPermission()
        }
    }

    // ── Permission chain ──────────────────────────────────────────────────────

    private fun checkOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            requestNotificationPermissionThenStart()
        } else {
            Toast.makeText(
                this,
                "Please allow \"Display over other apps\" so the speed widget can appear on screen.",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMonitorService()
        }
    }

    // ── Service control ───────────────────────────────────────────────────────

    private fun startMonitorService() {
        startForegroundService(Intent(this, NetworkSpeedService::class.java))
        isRunning = true
        updateUI()
    }

    private fun stopMonitorService() {
        startService(Intent(this, NetworkSpeedService::class.java).apply {
            action = NetworkSpeedService.ACTION_STOP
        })
        isRunning = false
        updateUI()
    }

    private fun updateUI() {
        if (isRunning) {
            binding.btnToggle.text = "Stop Monitoring"
            binding.tvStatus.text  = "● Speed widget active on screen"
        } else {
            binding.btnToggle.text = "Start Monitoring"
            binding.tvStatus.text  = "● Monitoring stopped"
        }
    }
}
