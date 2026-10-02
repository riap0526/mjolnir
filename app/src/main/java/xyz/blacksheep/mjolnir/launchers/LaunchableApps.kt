package xyz.blacksheep.mjolnir.launchers

import android.content.Context
import android.content.Intent
import xyz.blacksheep.mjolnir.utils.AppQueryHelper

/**
 * Retrieves a list of installed applications suitable for the launcher picker.
 *
 * **Logic:**
 * - Uses [AppQueryHelper] to fetch apps.
 * - If `showAll` is true: Returns all launchable apps (canonical list).
 * - If `showAll` is false: Returns only apps with `CATEGORY_HOME` (launchers).
 * - **Injects:** A special `<Nothing>` option at the top of the list to allow clearing a slot.
 *
 * @param context Context for PackageManager access.
 * @param showAll Filter toggle state.
 * @return A sorted list of [LauncherApp] objects.
 */
fun getLaunchableApps(context: Context, showAll: Boolean): List<LauncherApp> {
    val queryHelper = AppQueryHelper(context)
    val appInfoList = if (showAll) {
        queryHelper.queryAllApps()
    } else {
        queryHelper.queryLauncherApps()
    }

    val pm = context.packageManager

    val apps = appInfoList.map { appInfo ->
        val launchIntent =
            pm.getLaunchIntentForPackage(appInfo.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(appInfo.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

        LauncherApp(
            label = appInfo.label,
            packageName = appInfo.packageName,
            launchIntent = launchIntent
        )
    }.sortedBy { it.label.lowercase() }

    // --- MANUAL CHANGE START ---
    // Create the <Nothing> option
    val nothingOption = LauncherApp(
        label = "<Nothing>",
        packageName = "NOTHING", // This matches the backend check
        launchIntent = Intent()  // Empty intent
    )

    // Return the list with <Nothing> at the top
    return listOf(nothingOption) + apps
    // --- MANUAL CHANGE END ---
}

/**
 * Builds the launch intent for one configured package without querying every installed app.
 *
 * Unlike [getLaunchableApps], this ignores the blacklist and the "Show all apps" filter: those only
 * decide what the picker offers, and must not make an already-configured slot fail to launch.
 *
 * @return An intent shaped like the ones [getLaunchableApps] builds, or `null` if the package is
 * empty, is Mjolnir itself, is not installed, or has no launchable activity.
 */
fun resolveLaunchIntent(context: Context, packageName: String): Intent? {
    if (packageName.isBlank() || packageName == "NOTHING" || packageName == context.packageName) return null
    val pm = context.packageManager

    pm.getLaunchIntentForPackage(packageName)?.let {
        return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val fallback = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setPackage(packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return if (pm.resolveActivity(fallback, 0) != null) fallback else null
}
