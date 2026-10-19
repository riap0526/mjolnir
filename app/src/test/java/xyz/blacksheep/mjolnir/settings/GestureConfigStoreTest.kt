package xyz.blacksheep.mjolnir.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.blacksheep.mjolnir.home.Action
import java.io.File

/** Regression: "New" preset must save under the typed name, without stray untitled-N.cfg files. */
@RunWith(RobolectricTestRunner::class)
class GestureConfigStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dir get() = File(context.getExternalFilesDir(null)!!.parentFile, "gestures")
    private fun files() = dir.list()!!.filter { it.endsWith(".cfg") }.sorted()

    @Before
    fun setUp() {
        dir.deleteRecursively()
        GestureConfigStore.clearDraft()
        GestureConfigStore.setActiveConfig(context, "type-c.cfg")
        GestureConfigStore.ensureDefaults(context)
    }

    @Test
    fun newPresetSavesUnderTypedNameOnly() {
        val draft = GestureConfigStore.createPresetFromActive(context)
        assertEquals("nothing written before Save", listOf("type-a.cfg", "type-b.cfg", "type-c.cfg"), files())

        val saved = GestureConfigStore.saveFromEditor(context, draft.copy(single = Action.BOTTOM_HOME_DEFAULT), "My Setup", isDraft = true)
        assertEquals("my-setup.cfg", saved.fileName)
        assertEquals("My Setup", GestureConfigStore.getActiveConfig(context, forceRefresh = true).name)

        // Edit + save again (same name, then new name): still exactly one custom file.
        var edited = GestureConfigStore.saveFromEditor(context, saved.copy(double = Action.BOTH_HOME), "My Setup", isDraft = false)
        assertEquals("my-setup.cfg", edited.fileName)
        edited = GestureConfigStore.saveFromEditor(context, edited, "Renamed", isDraft = false)
        assertEquals(listOf("renamed.cfg", "type-a.cfg", "type-b.cfg", "type-c.cfg"), files())
        assertEquals(Action.BOTH_HOME, GestureConfigStore.getActiveConfig(context, forceRefresh = true).double)
    }

    @Test
    fun cancelledNewPresetLeavesNoFile() {
        GestureConfigStore.createPresetFromActive(context)
        GestureConfigStore.clearDraft()
        assertEquals(listOf("type-a.cfg", "type-b.cfg", "type-c.cfg"), files())
        assertEquals("type-c.cfg", GestureConfigStore.getActiveConfig(context, forceRefresh = true).fileName)
    }

    @Test
    fun renamingBuiltInCreatesNamedCopy() {
        val typeC = GestureConfigStore.getActiveConfig(context)
        val saved = GestureConfigStore.saveFromEditor(context, typeC, "Thor Daily", isDraft = false)
        assertEquals("thor-daily.cfg", saved.fileName)
        assertEquals("Thor Daily", GestureConfigStore.getActiveConfig(context, forceRefresh = true).name)
        assertEquals(4, files().size)
    }

    @Test
    fun presetNamedCustomSurvivesReloads() {
        // Our earlier fix (one-time legacy custom.cfg migration) together with the draft save path.
        val draft = GestureConfigStore.createPresetFromActive(context)
        GestureConfigStore.saveFromEditor(context, draft.copy(single = Action.BOTH_HOME), "custom", isDraft = true)
        repeat(3) { GestureConfigStore.ensureDefaults(context) }
        assertEquals(listOf("custom.cfg", "type-a.cfg", "type-b.cfg", "type-c.cfg"), files())
        assertEquals(Action.BOTH_HOME, GestureConfigStore.getActiveConfig(context, forceRefresh = true).single)
    }
}
