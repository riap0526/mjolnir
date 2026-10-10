package xyz.blacksheep.mjolnir.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusedDisplayResolverTest {
    @Test
    fun resolvesNonContiguousDisplayIdsToTheirPhysicalSlots() {
        assertEquals(FocusTarget.TOP, resolveFocusTarget(0, topDisplayId = 0, bottomDisplayId = 2))
        assertEquals(FocusTarget.BOTTOM, resolveFocusTarget(2, topDisplayId = 0, bottomDisplayId = 2))
    }

    @Test
    fun returnsNullWhenFocusedDisplayIsMissingOrUnknown() {
        assertNull(resolveFocusTarget(null, topDisplayId = 0, bottomDisplayId = 2))
        assertNull(resolveFocusTarget(7, topDisplayId = 0, bottomDisplayId = 2))
    }

    @Test
    fun acceptsVendorFocusOnlyWhenItMatchesARegisteredDisplay() {
        val availableDisplayIds = intArrayOf(0, 2)

        assertEquals(2, validateDisplayId(2, availableDisplayIds))
        assertNull(validateDisplayId(1, availableDisplayIds))
        assertNull(validateDisplayId(-1, availableDisplayIds))
    }
}
