package com.speedmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.speedmonitor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRunning = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startMonitorService()
            } else {
                Toast.makeText(
                    this,
                    "Notification permission denied. Speed won't appear in notification bar.",
                    Toast.LENGTH_LONG
                ).show()
                // Still start the service; it will run silently
                startMonitorService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnToggle.setOnClickListener {
            if (isRunning) stopMonitorService() else requestPermissionAndStart()
        }
    }

    private fun requestPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED -> startMonitorService()
                else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startMonitorService()
        }
    }

    private fun startMonitorService() {
        val intent = Intent(this, NetworkSpeedService::class.java)
        startForegroundService(intent)
        isRunning = true
        updateUI()
    }

    private fun stopMonitorService() {
        val intent = Intent(this, NetworkSpeedService::class.java).apply {
            action = NetworkSpeedService.ACTION_STOP
        }
        startService(intent)
        isRunning = false
        updateUI()
    }

    private fun updateUI() {
        if (isRunning) {
            binding.btnToggle.text = "Stop Monitoring"
            binding.tvStatus.text = "● Monitoring active – check your notification bar"
        } else {
            binding.btnToggle.text = "Start Monitoring"
            binding.tvStatus.text = "● Monitoring stopped"
        }
    }
}
