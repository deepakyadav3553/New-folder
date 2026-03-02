package com.speedmonitor

/**
 * Utility to format raw bytes-per-second into a human-readable speed string.
 * Automatically picks the best unit: B/s, KB/s, MB/s, or GB/s.
 */
object SpeedFormatter {

    private const val KB = 1024.0
    private const val MB = 1024.0 * 1024
    private const val GB = 1024.0 * 1024 * 1024

    fun format(bytesPerSec: Long): String {
        return when {
            bytesPerSec < 0 -> "0 B/s"
            bytesPerSec < KB -> "${bytesPerSec} B/s"
            bytesPerSec < MB -> String.format("%.1f KB/s", bytesPerSec / KB)
            bytesPerSec < GB -> String.format("%.2f MB/s", bytesPerSec / MB)
            else -> String.format("%.2f GB/s", bytesPerSec / GB)
        }
    }
}
