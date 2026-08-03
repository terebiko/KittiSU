package anhiutangerinee.kittisu.ui.viewmodel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleViewModelTest {
    @Test
    fun parseModuleInfo_duplicateMetadataIds_keepDistinctDirectoryIds() {
        val first = ModuleViewModel.Parser.parseModuleInfo(moduleJson("shared", "first"))
        val second = ModuleViewModel.Parser.parseModuleInfo(moduleJson("shared", "second"))

        assertEquals("shared", first.id)
        assertEquals("shared", second.id)
        assertEquals(setOf("first", "second"), setOf(first.dirId, second.dirId))
    }

    @Test
    fun parseModuleInfo_missingMetadataId_fallsBackToDirectoryId() {
        val module = ModuleViewModel.Parser.parseModuleInfo(
            moduleJson(id = null, dirId = "fallback")
        )

        assertEquals("fallback", module.id)
        assertEquals("fallback", module.name)
    }

    @Test
    fun parseModuleInfo_acceptsStringBooleanAndIntegerFields() {
        val module = ModuleViewModel.Parser.parseModuleInfo(
            moduleJson("module", "module-dir").apply {
                put("enabled", "1")
                put("remove", "false")
                put("versionCode", "42")
            }
        )

        assertTrue(module.enabled)
        assertFalse(module.remove)
        assertEquals(42, module.versionCode)
    }

    @Test
    fun parseModuleInfo_missingDirectoryId_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ModuleViewModel.Parser.parseModuleInfo(JSONObject().put("id", "metadata-only"))
        }
    }

    @Test
    fun parseModuleList_invalidEntry_doesNotHideValidModules() {
        val invalidIndexes = mutableListOf<Int>()
        val modules = ModuleViewModel.Parser.parseModuleList(
            JSONArray()
                .put(moduleJson("valid-one", "one"))
                .put("not-an-object")
                .put(JSONObject().put("id", "missing-dir"))
                .put(moduleJson("valid-two", "two"))
        ) { index, _ -> invalidIndexes += index }

        assertEquals(listOf("one", "two"), modules.map { it.dirId })
        assertEquals(listOf(1, 2), invalidIndexes)
    }

    private fun moduleJson(id: String?, dirId: String) = JSONObject().apply {
        if (id != null) put("id", id)
        put("dir_id", dirId)
    }
}
