package xyz.blacksheep.mjolnir.utils

/**
 * Remembers which package owns each accessibility window, learned from the events the service
 * already receives (they carry `windowId` and `packageName`).
 *
 * **Why:** `AccessibilityWindowInfo.getRoot()` asks the window's own process over IPC and blocks
 * until it answers or a 5 s timeout. Called on the main thread only to put a package name in the
 * log, it froze Home handling for 5 s while an app was starting at boot (field log 2026-10-31).
 * A lookup here never leaves the process. A window that has sent no event yet reads as null.
 */
object WindowPackages {
    private const val MAX_WINDOWS = 64

    data class Entry(val packageName: String, val displayId: Int)

    private val entries = object : LinkedHashMap<Int, Entry>(MAX_WINDOWS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Entry>?) = size > MAX_WINDOWS
    }

    @Synchronized
    fun record(windowId: Int, packageName: CharSequence?, displayId: Int) {
        if (windowId < 0 || packageName.isNullOrEmpty()) return
        val known = entries[windowId]
        // An event without a display (-1) keeps the display learned earlier.
        val display = if (displayId >= 0) displayId else known?.displayId ?: -1
        entries[windowId] = Entry(packageName.toString(), display)
    }

    @Synchronized
    fun packageOf(windowId: Int): String? = entries[windowId]?.packageName

    @Synchronized
    fun remove(windowId: Int): Entry? = entries.remove(windowId)

    /** Windows still tracked for [packageName], e.g. to tell "one window closed" from "app gone". */
    @Synchronized
    fun countFor(packageName: String): Int = entries.values.count { it.packageName == packageName }
}
