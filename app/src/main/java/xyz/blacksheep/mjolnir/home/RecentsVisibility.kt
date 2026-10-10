package xyz.blacksheep.mjolnir.home

import android.content.SharedPreferences
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_HIDE_BOTTOM_APP_FROM_RECENTS
import xyz.blacksheep.mjolnir.KEY_HIDE_TOP_APP_FROM_RECENTS
import xyz.blacksheep.mjolnir.KEY_TOP_APP

/**
 * Per-slot opt-in "Hide from Recents" (idea and original implementation by alexboydray,
 * upstream PR #40).
 *
 * Decided by the package being launched rather than the target display, so the setting follows
 * the app even when a single configured app is routed to the other screen. Only takes effect when
 * the launch creates a new task; an app that is already running keeps its current Recents entry
 * until its task is recreated.
 */
object RecentsVisibility {
    fun shouldHide(prefs: SharedPreferences, pkg: String?): Boolean {
        if (pkg == null) return false
        if (pkg == prefs.getString(KEY_TOP_APP, null) && prefs.getBoolean(KEY_HIDE_TOP_APP_FROM_RECENTS, false)) return true
        if (pkg == prefs.getString(KEY_BOTTOM_APP, null) && prefs.getBoolean(KEY_HIDE_BOTTOM_APP_FROM_RECENTS, false)) return true
        return false
    }
}
