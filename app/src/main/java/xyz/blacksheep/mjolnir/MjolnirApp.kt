package xyz.blacksheep.mjolnir

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import xyz.blacksheep.mjolnir.utils.AppQueryHelper
import xyz.blacksheep.mjolnir.utils.DiagnosticsConfig
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.MemoryState
import xyz.blacksheep.mjolnir.settings.SettingsStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Application class for Mjolnir.
 *
 * **Role:**
 * - Initializes app-wide singletons (Diagnostics, Notification Channels).
 * - Manages process-level startup logic.
 *
 * **Process Awareness:**
 * Startup stays light: most process starts are the system rebinding the accessibility
 * service, so UI-only work such as icon caching happens when a Mjolnir screen opens.
 */
class MjolnirApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createPersistentNotificationChannel()

        // Initialize Diagnostics
        DiagnosticsLogger.init(this)
        if (DiagnosticsConfig.isEnabled(this)) {
            DiagnosticsLogger.logHeader(this)
            logStartupContext()
        }

        // Icons are only needed by the settings UI, which warms the cache itself
        // (AppQueryHelper.prewarmInBackground). Most process starts are the system rebinding the
        // accessibility service, so loading every app icon here only cost memory.
        if (!isKeepAliveProcess()) {
            registerDisplayListener()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (DiagnosticsConfig.isEnabled(this)) {
            DiagnosticsLogger.logEvent("App", "TRIM_MEMORY", "level=${MemoryState.trimLevelName(level)} ${MemoryState.describe(this)}", this)
        }
        // UI_HIDDEN: no Mjolnir screen is visible any more, so the icon cache has no user.
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            AppQueryHelper.clearIconCache()
        }
    }

    /**
     * Records why the previous process ended and any settings recovery done during load, so a
     * restart in the log can be told apart: killed for memory, killed by a task cleaner, crash...
     */
    private fun logStartupContext() {
        SettingsStore.consumeLoadIssues().forEach { issue ->
            DiagnosticsLogger.logEvent("Settings", "SETTINGS_LOAD_ISSUE", issue, this)
        }

        DiagnosticsLogger.logEvent("App", "MEMORY_STATE", "at=process_start ${MemoryState.describe(this)}", this)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            logNewExitRecords()
        } catch (e: Exception) {
            DiagnosticsLogger.logException("App", e, this)
        }
    }

    /**
     * Logs every exit record not logged before, oldest first. Only the latest record used to be
     * logged, so repeated kills in a row were invisible and a shutdown (which leaves no record)
     * made the next boots repeat an old one.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun logNewExitRecords() {
        val state = getSharedPreferences(DIAGNOSTICS_STATE_PREFS, MODE_PRIVATE)
        val lastLoggedAt = state.getLong(KEY_LAST_LOGGED_EXIT_AT, 0L)
        val records = getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(packageName, 0, MAX_EXIT_RECORDS)
            .filter { it.timestamp > lastLoggedAt }
            .sortedBy { it.timestamp }
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        if (records.isEmpty()) {
            val last = if (lastLoggedAt > 0) format.format(Date(lastLoggedAt)) else "none"
            DiagnosticsLogger.logEvent("App", "PREVIOUS_PROCESS_EXIT_NONE_NEW", "lastLoggedAt=$last", this)
            return
        }
        // A shutdown leaves no exit record, so the first start after boot can see older ones.
        val bootTimeMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        records.forEachIndexed { index, record ->
            DiagnosticsLogger.logEvent(
                "App",
                "PREVIOUS_PROCESS_EXIT",
                "reason=${exitReasonName(record.reason)} at=${format.format(Date(record.timestamp))} " +
                    "beforeBoot=${record.timestamp < bootTimeMs} status=${record.status} importance=${record.importance} " +
                    "pssKb=${record.pss} rssKb=${record.rss} description=${record.description} n=${index + 1}/${records.size}",
                this
            )
        }
        state.edit().putLong(KEY_LAST_LOGGED_EXIT_AT, records.last().timestamp).apply()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun exitReasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "UNKNOWN($reason)"
    }

    private fun registerDisplayListener() {
        val displayManager = getSystemService(DisplayManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        fun markPendingIfAllowed() {
            if (!SafetyNetManager.isDefaultHome(this@MjolnirApp)) return
            val keyguard = getSystemService(android.app.KeyguardManager::class.java)
            if (keyguard.isKeyguardLocked) return
            SafetyNetManager.markPending(this@MjolnirApp)
        }
        displayManager.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                handler.postDelayed({ markPendingIfAllowed() }, 300L)
            }

            override fun onDisplayRemoved(displayId: Int) {
                // No-op for now.
            }

            override fun onDisplayChanged(displayId: Int) {
                handler.postDelayed({ markPendingIfAllowed() }, 300L)
            }
        }, handler)
    }

    /**
     * Checks if the current process is the dedicated `:keepalive` process.
     *
     * @return `true` if this is the keepalive process, `false` if it is the main UI process.
     */
    private fun isKeepAliveProcess(): Boolean {
        return try {
            val cmdline = File("/proc/self/cmdline").readText().trim { it <= ' ' }
            cmdline.endsWith(":keepalive")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Creates the NotificationChannel required for the [xyz.blacksheep.mjolnir.services.KeepAliveService].
     * This must be done before the service attempts to post its foreground notification.
     */
    private fun createPersistentNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PERSISTENT_CHANNEL_ID,
                "Mjolnir Persistent Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Mjolnir active so home key interception remains available."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val PERSISTENT_CHANNEL_ID = "mjolnir_persistent_channel"

        /** Internal diagnostics bookkeeping, kept out of the user's settings files. */
        const val DIAGNOSTICS_STATE_PREFS = "diagnostics_state"
        private const val KEY_LAST_LOGGED_EXIT_AT = "last_logged_exit_at"
        private const val MAX_EXIT_RECORDS = 16
    }
}
