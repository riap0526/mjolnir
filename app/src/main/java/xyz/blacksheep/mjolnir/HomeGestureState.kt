package xyz.blacksheep.mjolnir

internal class HomeGestureState {
    enum class DownResult {
        ACCEPTED,
        REPEAT_IGNORED,
        DUPLICATE_IGNORED
    }

    enum class UpResult {
        ACCEPTED,
        LONG_PRESS_RELEASE_IGNORED,
        UNMATCHED_IGNORED
    }

    var pressCount: Int = 0
        private set

    private var isKeyDown = false
    private var ignoreNextUp = false

    fun onDown(repeatCount: Int): DownResult {
        if (repeatCount > 0) return DownResult.REPEAT_IGNORED
        if (isKeyDown) return DownResult.DUPLICATE_IGNORED
        ignoreNextUp = false

        isKeyDown = true
        pressCount++
        return DownResult.ACCEPTED
    }

    fun onUp(): UpResult {
        if (ignoreNextUp) {
            ignoreNextUp = false
            return UpResult.LONG_PRESS_RELEASE_IGNORED
        }
        if (!isKeyDown) return UpResult.UNMATCHED_IGNORED

        isKeyDown = false
        return UpResult.ACCEPTED
    }

    fun markLongPressTriggered(): Boolean {
        if (!isKeyDown || pressCount == 0) return false

        isKeyDown = false
        ignoreNextUp = true
        return true
    }

    fun reset(preserveLongPressRelease: Boolean = false) {
        pressCount = 0
        isKeyDown = false
        if (!preserveLongPressRelease) ignoreNextUp = false
    }
}
