package xyz.blacksheep.mjolnir.model

/**
 * Indicates which physical display (Top or Bottom screen on the AYN Thor)
 * should be considered the "primary" display during dual-screen launching.
 *
 * This preference affects:
 * - Focus order when launching two apps simultaneously.
 * - Which app is launched first during dual-launch sequences.
 * - UI labels inside HomeLauncherSettingsMenu.
 *
 * NOTE:
 * The actual display IDs are handled by `DualScreenLauncher` and are not
 * defined here; this enum simply represents user preference.
 */
enum class MainScreen {
    TOP,
    BOTTOM;

    companion object {
        /**
         * Parses a stored preference value, falling back to [TOP] for missing or invalid values
         * (e.g. a hand-edited settings.json). [valueOf] would throw and crash the Home activity.
         */
        fun fromPref(value: String?): MainScreen =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: TOP
    }
}
