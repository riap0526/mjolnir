package xyz.blacksheep.mjolnir.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Logs main-thread stalls for a limited time, e.g. the first minutes after the accessibility
 * service connects at boot, when a stall delays the boot action and every Home press.
 *
 * A tick is posted every [INTERVAL_MS]; when it runs at least [STALL_MS] late, the main thread was
 * blocked for about that long. Uses uptime, so deep sleep does not count as a stall.
 */
class MainThreadWatchdog(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var expectedAt = 0L
    private var stopAt = 0L
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val lateMs = now - expectedAt
            if (lateMs >= STALL_MS) {
                DiagnosticsLogger.logEvent("Watchdog", "MAIN_THREAD_STALL", "lateMs=$lateMs", context)
            }
            if (now >= stopAt) {
                running = false
                return
            }
            expectedAt = now + INTERVAL_MS
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start(durationMs: Long) {
        if (!DiagnosticsConfig.isEnabled(context)) return
        val now = SystemClock.uptimeMillis()
        stopAt = maxOf(stopAt, now + durationMs)
        if (running) return
        running = true
        expectedAt = now + INTERVAL_MS
        handler.postDelayed(tick, INTERVAL_MS)
    }

    fun stop() {
        handler.removeCallbacks(tick)
        running = false
    }

    companion object {
        private const val INTERVAL_MS = 500L
        private const val STALL_MS = 1_000L

        /** Logs [op] when it held the main thread for [SLOW_OP_MS] or more since [startUptimeMs]. */
        fun logIfSlow(context: Context, op: String, startUptimeMs: Long) {
            val ms = SystemClock.uptimeMillis() - startUptimeMs
            if (ms >= SLOW_OP_MS) {
                DiagnosticsLogger.logEvent("Watchdog", "MAIN_THREAD_SLOW", "op=$op durationMs=$ms", context)
            }
        }

        private const val SLOW_OP_MS = 200L
    }
}
