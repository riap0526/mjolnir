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
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.utils.AppQueryHelper
import xyz.blacksheep.mjolnir.utils.DiagnosticsConfig
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
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
 * - Triggers the background "Prewarm" of app icons to ensure UI responsiveness.
 *
 * **Process Awareness:**
 * Mjolnir may run in multiple processes (e.g., `:keepalive` for the foreground service).
 * Heavy initialization (like icon caching) is strictly limited to the main UI process
 * to save memory in the background service.
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

        // Only prewarm icons in the main UI process.
        // The keepalive process should stay as lightweight as possible.
        if (!isKeepAliveProcess()) {
            registerDisplayListener()
            CoroutineScope(Dispatchers.IO).launch {
                DiagnosticsLogger.logEvent("App", "ICON_PREWARM_START", "totalApps=N/A", this@MjolnirApp)
                val startTime = System.currentTimeMillis()
                try {
                    AppQueryHelper.prewarmAllApps(this@MjolnirApp)
                    val duration = System.currentTimeMillis() - startTime
                    DiagnosticsLogger.logEvent("App", "ICON_PREWARM_END", "durationMs=$duration totalApps=N/A", this@MjolnirApp)
                } catch (e: Exception) {
                    DiagnosticsLogger.logException("App", e, this@MjolnirApp)
                }
            }
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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val activityManager = getSystemService(ActivityManager::class.java)
            val last = activityManager.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull() ?: return
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(last.timestamp))
            DiagnosticsLogger.logEvent(
                "App",
                "PREVIOUS_PROCESS_EXIT",
                "reason=${exitReasonName(last.reason)} at=$time status=${last.status} importance=${last.importance} " +
                    "pssKb=${last.pss} rssKb=${last.rss} description=${last.description}",
                this
            )
        } catch (e: Exception) {
            DiagnosticsLogger.logException("App", e, this)
        }
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
    }
}
