package xyz.blacksheep.mjolnir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGestureStateTest {
    @Test
    fun repeatDownDoesNotCreateAnotherTap() {
        val state = HomeGestureState()

        assertEquals(HomeGestureState.DownResult.ACCEPTED, state.onDown(repeatCount = 0))
        assertEquals(HomeGestureState.DownResult.REPEAT_IGNORED, state.onDown(repeatCount = 1))
        assertEquals(1, state.pressCount)
    }

    @Test
    fun unmatchedUpDoesNotStartGestureResolution() {
        val state = HomeGestureState()

        assertEquals(HomeGestureState.UpResult.UNMATCHED_IGNORED, state.onUp())
        assertEquals(0, state.pressCount)
    }

    @Test
    fun releaseAfterLongPressIsIgnoredWithoutPoisoningNextTap() {
        val state = HomeGestureState()

        assertEquals(HomeGestureState.DownResult.ACCEPTED, state.onDown(repeatCount = 0))
        assertTrue(state.markLongPressTriggered())
        state.reset(preserveLongPressRelease = true)

        assertEquals(HomeGestureState.UpResult.LONG_PRESS_RELEASE_IGNORED, state.onUp())
        assertEquals(HomeGestureState.DownResult.ACCEPTED, state.onDown(repeatCount = 0))
        assertEquals(1, state.pressCount)
    }

    @Test
    fun freshTapSupersedesMissingReleaseFromLongPress() {
        val state = HomeGestureState()

        state.onDown(repeatCount = 0)
        assertTrue(state.markLongPressTriggered())
        state.reset(preserveLongPressRelease = true)

        assertEquals(HomeGestureState.DownResult.ACCEPTED, state.onDown(repeatCount = 0))
        assertEquals(HomeGestureState.UpResult.ACCEPTED, state.onUp())
    }

    @Test
    fun resetClearsPendingTapState() {
        val state = HomeGestureState()

        state.onDown(repeatCount = 0)
        assertEquals(HomeGestureState.UpResult.ACCEPTED, state.onUp())
        state.reset()

        assertEquals(0, state.pressCount)
        assertEquals(HomeGestureState.UpResult.UNMATCHED_IGNORED, state.onUp())
    }
}
