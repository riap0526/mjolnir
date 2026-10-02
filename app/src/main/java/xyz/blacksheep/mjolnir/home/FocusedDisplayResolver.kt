package xyz.blacksheep.mjolnir.home

internal enum class FocusTarget {
    TOP,
    BOTTOM
}

internal fun resolveFocusTarget(
    focusedDisplayId: Int?,
    topDisplayId: Int,
    bottomDisplayId: Int
): FocusTarget? = when (focusedDisplayId) {
    topDisplayId -> FocusTarget.TOP
    bottomDisplayId -> FocusTarget.BOTTOM
    else -> null
}

internal fun validateDisplayId(
    reportedDisplayId: Int,
    availableDisplayIds: IntArray
): Int? = reportedDisplayId.takeIf { candidate ->
    availableDisplayIds.any { it == candidate }
}
