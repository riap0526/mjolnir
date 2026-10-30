package xyz.blacksheep.mjolnir

import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import xyz.blacksheep.mjolnir.settings.settingsPrefs
import xyz.blacksheep.mjolnir.utils.DiagnosticsConfig
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.MemoryState
import android.app.ActivityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class SafetyNetActivity : ComponentActivity() {
    companion object {
        private const val TAG = "SafetyNetActivity"
        /** Visible this long means the slot app is not coming back by itself. */
        private const val STUCK_AFTER_MS = 5_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var visibleSince = 0L
    private val stuckCheck = Runnable {
        DiagnosticsLogger.logEvent(
            "SafetyNet",
            "STUCK",
            "displayId=${currentDisplayId()} visibleMs=${SystemClock.elapsedRealtime() - visibleSince} ${slotSummary()} ${MemoryState.describe(this)}",
            this
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        excludeTaskFromRecents()
        Log.d(TAG, "onCreate displayId=${display?.displayId} taskId=$taskId data=${intent?.data}")
        DiagnosticsLogger.logEvent("SafetyNet", "ACTIVITY_CREATED", "displayId=${currentDisplayId()} taskId=$taskId data=${intent?.data}", this)
        setContent { SafetyNetScreen() }
    }

    // Resumed means the SafetyNet screen is what the user sees on that display: the app that
    // should be there is gone or was never launched. Logged so those moments can be traced.
    override fun onResume() {
        super.onResume()
        visibleSince = SystemClock.elapsedRealtime()
        if (!DiagnosticsConfig.isEnabled(this)) return
        DiagnosticsLogger.logEvent("SafetyNet", "VISIBLE", "displayId=${currentDisplayId()} taskId=$taskId ${MemoryState.describe(this)}", this)
        handler.postDelayed(stuckCheck, STUCK_AFTER_MS)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(stuckCheck)
        val visibleMs = SystemClock.elapsedRealtime() - visibleSince
        DiagnosticsLogger.logEvent("SafetyNet", "HIDDEN", "displayId=${currentDisplayId()} taskId=$taskId visibleMs=$visibleMs", this)
    }

    private fun slotSummary(): String {
        val prefs = settingsPrefs()
        return "top=${prefs.getString(KEY_TOP_APP, null)} bottom=${prefs.getString(KEY_BOTTOM_APP, null)}"
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayId(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.displayId else windowManager.defaultDisplay?.displayId

    private fun excludeTaskFromRecents() {
        val activityManager = getSystemService(ActivityManager::class.java)
        val taskId = this.taskId
        activityManager.appTasks.firstOrNull { it.taskInfo.taskId == taskId }
            ?.setExcludeFromRecents(true)
    }
}

@Composable
private fun SafetyNetScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text("You should not be here.", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
