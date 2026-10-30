package xyz.blacksheep.mjolnir.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowPackagesTest {
    @Test
    fun keepsDisplayWhenLaterEventHasNone() {
        WindowPackages.record(9001, "org.es_de.frontend", 0)
        WindowPackages.record(9001, "org.es_de.frontend", -1)
        assertEquals(WindowPackages.Entry("org.es_de.frontend", 0), WindowPackages.remove(9001))
        assertNull(WindowPackages.packageOf(9001))
    }

    @Test
    fun ignoresEventsWithoutWindowOrPackage() {
        WindowPackages.record(-1, "a.b", 0)
        WindowPackages.record(9002, null, 0)
        assertNull(WindowPackages.packageOf(9002))
    }

    @Test
    fun evictsOldestBeyondLimitAndCountsPerPackage() {
        for (id in 10_000 until 10_100) WindowPackages.record(id, "pkg.$id", 0)
        assertNull("oldest evicted", WindowPackages.packageOf(10_000))
        assertEquals("pkg.10099", WindowPackages.packageOf(10_099))
        WindowPackages.record(10_098, "pkg.10099", 0)
        assertEquals(2, WindowPackages.countFor("pkg.10099"))
    }
}
