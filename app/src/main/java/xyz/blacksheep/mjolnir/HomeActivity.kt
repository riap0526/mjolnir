package xyz.blacksheep.mjolnir

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import xyz.blacksheep.mjolnir.onboarding.OnboardingActivity
import xyz.blacksheep.mjolnir.model.MainScreen
import xyz.blacksheep.mjolnir.launchers.resolveLaunchIntent
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.DualScreenLauncher
import xyz.blacksheep.mjolnir.settings.settingsPrefs

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = settingsPrefs()

        // --- NEW: Requirement 2 Safety Check ---
        val isInterceptionActive = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
        val topAppPkg = prefs.getString(KEY_TOP_APP, null)
        val bottomAppPkg = prefs.getString(KEY_BOTTOM_APP, null)
        DiagnosticsLogger.logEvent(
            "HomeActivity",
            "START",
            "displayId=${currentDisplayId()} categories=${intent?.categories} advanced=$isInterceptionActive top=$topAppPkg bottom=$bottomAppPkg",
            this
        )

        if (!isInterceptionActive) {
            // In Basic Mode, both screens must be set. 
            // If one is set (valid string) and the other is null (NOTHING), we have a "Bottomless Pit".
            // We use XOR: if (Top Set) != (Bottom Set), then we have a mismatch.
            val isTopSet = topAppPkg != null
            val isBottomSet = bottomAppPkg != null

            if (isTopSet xor isBottomSet) {
                DiagnosticsLogger.logEvent("HomeActivity", "CONFIG_WIPE", "reason=basic_mode_single_slot", this)
                wipeConfigAndLaunchOnboarding()
                return
            }
        }
        // ---------------------------------------

        if (!isConfigurationValid()) {
            DiagnosticsLogger.logEvent("HomeActivity", "CONFIG_WIPE", "reason=invalid_configuration", this)
            wipeConfigAndLaunchOnboarding()
            return
        }

        val failureCount = prefs.getInt(KEY_LAUNCH_FAILURE_COUNT, 0)

        if (failureCount >= 3) {
            DiagnosticsLogger.logEvent("HomeActivity", "CONFIG_WIPE", "reason=repeated_launch_failures count=$failureCount", this)
            prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, 0) }
            Toast.makeText(this, "Repeated launch failures. Resetting configuration.", Toast.LENGTH_LONG).show()
            wipeConfigAndLaunchOnboarding()
            return
        }

        SafetyNetManager.ensureSafetyNetActivities(this, allowStart = true)

        //val topAppPkg = prefs.getString(KEY_TOP_APP, null)
        //val bottomAppPkg = prefs.getString(KEY_BOTTOM_APP, null)
        val mainScreen = MainScreen.fromPref(prefs.getString(KEY_MAIN_SCREEN, null))

        if (topAppPkg != null || bottomAppPkg != null) {
            if (topAppPkg == null || bottomAppPkg == null) {
                 val targetPkg = topAppPkg ?: bottomAppPkg
                 val launchIntent = targetPkg?.let { resolveLaunchIntent(this, it) }
                 if (launchIntent != null) {
                     val launched = if (mainScreen == MainScreen.TOP) {
                         DualScreenLauncher.launchOnTop(this, launchIntent)
                     } else {
                         DualScreenLauncher.launchOnBottom(this, launchIntent)
                     }
                     DiagnosticsLogger.logEvent("HomeActivity", "LAUNCH_SINGLE", "package=$targetPkg mainScreen=$mainScreen launched=$launched", this)
                     prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, 0) }
                 } else {
                      DiagnosticsLogger.logEvent("HomeActivity", "LAUNCH_FAILED", "package=$targetPkg reason=not_launchable failureCount=${failureCount + 1}", this)
                      prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, failureCount + 1) }
                      launchSettings()
                 }
            } else {
                val topIntent = resolveLaunchIntent(this, topAppPkg)
                val bottomIntent = resolveLaunchIntent(this, bottomAppPkg)

                if (topIntent != null && bottomIntent != null) {
                    val success = DualScreenLauncher.launchOnDualScreens(this, topIntent, bottomIntent, mainScreen)
                    DiagnosticsLogger.logEvent("HomeActivity", "LAUNCH_BOTH", "mainScreen=$mainScreen success=$success", this)
                    if (success) {
                        prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, 0) }
                    } else {
                        prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, failureCount + 1) }
                    }
                } else {
                    DiagnosticsLogger.logEvent(
                        "HomeActivity",
                        "LAUNCH_FAILED",
                        "topLaunchable=${topIntent != null} bottomLaunchable=${bottomIntent != null} failureCount=${failureCount + 1}",
                        this
                    )
                    prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, failureCount + 1) }
                    launchSettings()
                }
            }
        } else {
            launchSettings()
        }
        finish()
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayId(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.displayId else windowManager.defaultDisplay?.displayId

    private fun isConfigurationValid(): Boolean {
        val prefs = settingsPrefs()
        val isInterceptionActive = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
        val topApp = prefs.getString(KEY_TOP_APP, null)
        val bottomApp = prefs.getString(KEY_BOTTOM_APP, null)
        val SPECIAL_HOME_APPS = setOf("com.android.launcher3", "com.odin.odinlauncher")

        if (topApp == null && bottomApp == null) return false

        if (isInterceptionActive) {
            if (!isAccessibilityServiceEnabled()) return false

            if (topApp in SPECIAL_HOME_APPS) {
                val defaultHome = getCurrentDefaultHomePackage(this)
                if (defaultHome != topApp) return false
            }
        }

        return true
    }

    private fun getCurrentDefaultHomePackage(context: Context): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo: ResolveInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, HomeKeyInterceptorService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    private fun wipeConfigAndLaunchOnboarding() {
        Toast.makeText(this, "Mjolnir config invalid, resetting.", Toast.LENGTH_LONG).show()
        settingsPrefs().edit {
            remove(KEY_TOP_APP)
            remove(KEY_BOTTOM_APP)
            remove(KEY_HOME_INTERCEPTION_ACTIVE)
        }

        // NEW: Launch a safe, dual-screen recovery state instead of a single activity.
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val onboardingIntent = Intent(this, OnboardingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        DualScreenLauncher.launchOnDualScreens(this, mainActivityIntent, onboardingIntent, MainScreen.TOP)

        finish()
    }

    private fun launchSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_settings", true)
        }
        startActivity(intent)
    }
}