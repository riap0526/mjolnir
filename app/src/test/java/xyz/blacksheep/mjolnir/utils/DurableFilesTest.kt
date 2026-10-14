package xyz.blacksheep.mjolnir.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DurableFilesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val alwaysValid: (String) -> Boolean = { true }
    private val jsonLike: (String) -> Boolean = { it.trim().startsWith("{") }

    private fun file(name: String = "settings.json") = File(tmp.root, name)

    @Test
    fun write_createsPrimaryAndBackup_andLeavesNoTempFile() {
        val f = file()
        DurableFiles.write(f, "{\"a\":1}")

        assertEquals("{\"a\":1}", f.readText())
        assertEquals("{\"a\":1}", DurableFiles.backupOf(f).readText())
        assertFalse(File(tmp.root, "settings.json.tmp").exists())
    }

    @Test
    fun write_createsMissingParentDirectory() {
        val f = File(tmp.root, "nested/dir/config.ini")
        DurableFiles.write(f, "KEY=1")
        assertEquals("KEY=1", f.readText())
    }

    @Test
    fun read_validPrimary_returnsPrimary() {
        val f = file()
        DurableFiles.write(f, "{\"a\":1}")

        val result = DurableFiles.read(f, jsonLike)
        assertEquals("{\"a\":1}", result.text)
        assertEquals(DurableFiles.Source.PRIMARY, result.source)
    }

    @Test
    fun read_missingPrimary_isNotRestoredFromBackup() {
        // Users delete files to reset them; a stale backup must not bring the settings back.
        val f = file()
        DurableFiles.write(f, "{\"a\":1}")
        assertTrue(f.delete())

        val result = DurableFiles.read(f, jsonLike)
        assertNull(result.text)
        assertEquals(DurableFiles.Source.NONE, result.source)
    }

    @Test
    fun read_primaryWithNulBytes_fallsBackToBackup_andKeepsCorruptCopy() {
        val f = file()
        DurableFiles.write(f, "{\"a\":1}")
        f.writeText("\u0000\u0000\u0000")

        val result = DurableFiles.read(f, alwaysValid)
        assertEquals("{\"a\":1}", result.text)
        assertEquals(DurableFiles.Source.BACKUP, result.source)
        assertEquals("\u0000\u0000\u0000", File(tmp.root, "settings.json.corrupt").readText())
    }

    @Test
    fun read_primaryFailingValidation_fallsBackToBackup() {
        val f = file()
        DurableFiles.write(f, "{\"a\":1}")
        f.writeText("")

        val result = DurableFiles.read(f, jsonLike)
        assertEquals("{\"a\":1}", result.text)
        assertEquals(DurableFiles.Source.BACKUP, result.source)
    }

    @Test
    fun read_primaryAndBackupBothCorrupt_returnsNone() {
        val f = file()
        f.writeText("\u0000")
        DurableFiles.backupOf(f).writeText("not json")

        val result = DurableFiles.read(f, jsonLike)
        assertNull(result.text)
        assertEquals(DurableFiles.Source.NONE, result.source)
    }

    @Test
    fun read_corruptPrimaryWithoutBackup_returnsNone() {
        val f = file()
        f.writeText("\u0000")

        val result = DurableFiles.read(f, alwaysValid)
        assertNull(result.text)
        assertEquals(DurableFiles.Source.NONE, result.source)
    }

    @Test
    fun write_overwritesPreviousContentInBothCopies() {
        val f = file()
        DurableFiles.write(f, "{\"a\":1}")
        DurableFiles.write(f, "{\"a\":2}")

        assertEquals("{\"a\":2}", f.readText())
        assertEquals("{\"a\":2}", DurableFiles.backupOf(f).readText())
    }

    @Test
    fun delete_removesPrimaryAndBackup() {
        val f = file()
        DurableFiles.write(f, "{\"a\":1}")

        assertTrue(DurableFiles.delete(f))
        assertFalse(f.exists())
        assertFalse(DurableFiles.backupOf(f).exists())
    }
}
