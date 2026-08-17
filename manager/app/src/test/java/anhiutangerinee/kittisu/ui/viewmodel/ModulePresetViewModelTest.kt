package anhiutangerinee.kittisu.ui.viewmodel

import anhiutangerinee.kittisu.ui.util.module.LoadedPreset
import anhiutangerinee.kittisu.ui.util.module.PresetEntry
import anhiutangerinee.kittisu.ui.util.module.PresetModule
import anhiutangerinee.kittisu.ui.util.module.PresetRequirement
import anhiutangerinee.kittisu.ui.util.module.parsePresetFile
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ponytail: tests cover pure logic only. Android-bound paths (file IO,
// okhttp, SharedPreferences) are validated manually / in instrumented tests.
// Requires testImplementation(junit:junit) in app/build.gradle.kts to run.
class ModulePresetViewModelTest {

    private fun module(
        id: String,
        directUrl: String = "https://example.com/$id.zip",
        rebootAfter: Boolean = false,
        stopIfFail: Boolean = true,
        dependsOn: List<String> = emptyList(),
        requirement: PresetRequirement? = null
    ) = PresetModule(
        moduleName = id,
        moduleId = id,
        moduleVersion = "1.0",
        directUrl = directUrl,
        stopIfFail = stopIfFail,
        rebootAfter = rebootAfter,
        dependsOn = dependsOn,
        requirement = requirement
    )

    private fun entry(
        id: String,
        modules: List<PresetModule>,
        requiresRebootAtEnd: Boolean = false
    ) = PresetEntry(
        id = id,
        destination = id,
        author = "tester",
        committer = "@tester",
        team = "Test Team",
        requiresRebootAtEnd = requiresRebootAtEnd,
        modules = modules
    )

    private fun loaded(entry: PresetEntry) = LoadedPreset(
        sourceId = "test",
        fileName = "${entry.id}.json",
        presetEntry = entry,
        isLocal = true
    )

    @Test
    fun buildInstallPlan_skipsInstalledModules_whenSkipInstalledTrue() = runBlocking {
        val vm = ModulePresetViewModel()
        val installed = setOf("installed-mod")
        val loaded = loaded(
            entry(
                id = "p1",
                modules = listOf(
                    module(id = "installed-mod"),
                    module(id = "fresh-mod")
                )
            )
        )

        val plan = vm.buildInstallPlan(loaded, skipInstalled = true) { installed.contains(it) }.getOrThrow()

        assertEquals(2, plan.modules.size)
        val byId = plan.modules.associateBy { it.presetModule.moduleId }
        assertTrue("installed module should be skipped", byId.getValue("installed-mod").skip)
        assertTrue("installed flag should be true", byId.getValue("installed-mod").isInstalled)
        assertFalse("fresh module should not be skipped", byId.getValue("fresh-mod").skip)
        assertFalse("fresh module should not be installed", byId.getValue("fresh-mod").isInstalled)
    }

    @Test
    fun buildInstallPlan_includesAllModules_whenSkipInstalledFalse() = runBlocking {
        val vm = ModulePresetViewModel()
        val installed = setOf("installed-mod")
        val loaded = loaded(
            entry(
                id = "p1",
                modules = listOf(
                    module(id = "installed-mod"),
                    module(id = "fresh-mod")
                )
            )
        )

        val plan = vm.buildInstallPlan(loaded, skipInstalled = false) { installed.contains(it) }.getOrThrow()

        assertEquals(2, plan.modules.size)
        assertTrue("none should be skipped when skipInstalled=false", plan.modules.none { it.skip })
        // isInstalled flag is still reported even when skip is false.
        assertTrue(plan.modules.first { it.presetModule.moduleId == "installed-mod" }.isInstalled)
        assertFalse(plan.modules.first { it.presetModule.moduleId == "fresh-mod" }.isInstalled)
    }

    @Test
    fun buildInstallPlan_requiresReboot_whenLastModuleRebootAfterIsTrue() = runBlocking {
        val vm = ModulePresetViewModel()
        val loaded = loaded(
            entry(
                id = "p-reboot",
                modules = listOf(
                    module(id = "a", rebootAfter = false),
                    module(id = "b", rebootAfter = true)
                )
            )
        )
        val plan = vm.buildInstallPlan(loaded).getOrThrow()
        assertTrue(plan.requiresReboot)
    }

    @Test
    fun buildInstallPlan_requiresReboot_whenEntrySetsRequiresRebootAtEnd() = runBlocking {
        val vm = ModulePresetViewModel()
        val loaded = loaded(
            entry(
                id = "p-entry-reboot",
                requiresRebootAtEnd = true,
                modules = listOf(module(id = "a"))
            )
        )
        val plan = vm.buildInstallPlan(loaded).getOrThrow()
        assertTrue(plan.requiresReboot)
    }

    @Test
    fun buildInstallPlan_noRebootByDefault() = runBlocking {
        val vm = ModulePresetViewModel()
        val loaded = loaded(
            entry(
                id = "p-noop",
                modules = listOf(module(id = "a"), module(id = "b"))
            )
        )
        val plan = vm.buildInstallPlan(loaded).getOrThrow()
        assertFalse(plan.requiresReboot)
    }

    @Test
    fun localPreset_roundtripsThroughParsePresetFile() {
        val original = entry(
            id = "roundtrip",
            modules = listOf(
                module(
                    id = "m1",
                    directUrl = "https://example.com/m1.zip",
                    dependsOn = listOf("dep1"),
                    requirement = PresetRequirement(susfs = "2.0.0")
                )
            )
        )

        // Mirror the ViewModel's local write format: PresetFile wrapper around
        // a single PresetEntry.
        val wrapped = JSONObject().apply {
            put("name", original.id)
            put("preset", JSONArray().put(presetEntryToJsonForTest(original)))
        }

        val parsed = parsePresetFile(wrapped.toString(), "${original.id}.json")
        assertNotNull("parsePresetFile should accept the wrapped entry", parsed)
        assertEquals(1, parsed!!.preset.size)
        val round = parsed.preset.first()
        assertEquals(original.id, round.id)
        assertEquals(original.destination, round.destination)
        assertEquals(original.committer, round.committer)
        assertEquals(original.team, round.team)
        assertEquals(original.modules.size, round.modules.size)
        val m = round.modules.first()
        assertEquals("m1", m.moduleId)
        assertEquals("https://example.com/m1.zip", m.directUrl)
        assertEquals(listOf("dep1"), m.dependsOn)
        assertNotNull(m.requirement)
        assertEquals("2.0.0", m.requirement!!.susfs)
    }

    // ponytail: mirror of the private serializer in ModulePresetViewModel,
    // kept here so the test exercises the exact JSON shape we persist.
    private fun presetEntryToJsonForTest(entry: PresetEntry): JSONObject {
        val modules = JSONArray()
        for (m in entry.modules) {
            modules.put(
                JSONObject().apply {
                    put("moduleName", m.moduleName)
                    put("moduleId", m.moduleId)
                    put("moduleVersion", m.moduleVersion ?: false)
                    put("directUrl", m.directUrl)
                    put("stopIfFail", m.stopIfFail)
                    put("rebootAfter", m.rebootAfter)
                    put("dependsOn", JSONArray(m.dependsOn))
                    m.requirement?.let { r ->
                        put(
                            "requirement",
                            JSONObject().apply {
                                r.susfs?.let { put("susfs", it) }
                                r.kernelsu?.let { put("kernelsu", it) }
                                r.android?.let { put("android", it) }
                                r.metadata?.let { put("metadata", it) }
                            }
                        )
                    }
                }
            )
        }
        return JSONObject().apply {
            put("id", entry.id)
            put("destination", entry.destination)
            entry.author?.let { put("author", it) }
            entry.committer?.let { put("committer", it) }
            entry.team?.let { put("team", it) }
            put("requiresRebootAtEnd", entry.requiresRebootAtEnd)
            put("modules", modules)
        }
    }
}
