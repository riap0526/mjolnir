package xyz.blacksheep.mjolnir.utils

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context

/**
 * One-line memory snapshot for the diagnostics log.
 *
 * Boot-time kills (`LOW_MEMORY`, Mjolnir and probably the slot apps) could so far only be inferred
 * from restarts; these numbers show how tight memory actually was at each decision point.
 */
object MemoryState {
    fun describe(context: Context): String = try {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        "availMb=${info.availMem / MB} totalMb=${info.totalMem / MB} thresholdMb=${info.threshold / MB} lowMemory=${info.lowMemory}"
    } catch (e: Exception) {
        "mem=unavailable"
    }

    @Suppress("DEPRECATION")
    fun trimLevelName(level: Int): String = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
        else -> "LEVEL_$level"
    }

    private const val MB = 1024L * 1024L
}
