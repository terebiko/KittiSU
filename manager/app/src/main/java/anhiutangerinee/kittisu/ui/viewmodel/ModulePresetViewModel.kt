package anhiutangerinee.kittisu.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.activity.util.isNetworkAvailable
import anhiutangerinee.kittisu.ui.util.module.LoadedPreset
import anhiutangerinee.kittisu.ui.util.module.ModuleInstallPlan
import anhiutangerinee.kittisu.ui.util.module.OFFICIAL_PRESETS_BASE_URL
import anhiutangerinee.kittisu.ui.util.module.PlanModule
import anhiutangerinee.kittisu.ui.util.module.PresetEntry
import anhiutangerinee.kittisu.ui.util.module.PresetModule
import anhiutangerinee.kittisu.ui.util.module.PresetRequirement
import anhiutangerinee.kittisu.ui.util.module.PresetSource
import anhiutangerinee.kittisu.ui.util.module.PresetVerificationStatus
import anhiutangerinee.kittisu.ui.util.module.fetchPresetFileWithSignature
import anhiutangerinee.kittisu.ui.util.module.fetchPresetIndex
import anhiutangerinee.kittisu.ui.util.module.isModuleInstalled
import anhiutangerinee.kittisu.ui.util.module.parsePresetFile
import anhiutangerinee.kittisu.ui.util.module.resolveModuleDownloadUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ModulePresetViewModel : ViewModel() {
    companion object {
        private const val TAG = "ModulePresetViewModel"
        private const val PREFS_NAME = "prefs_preset_sources"
        private const val KEY_CUSTOM_SOURCES = "custom_sources_json"
        private const val LOCAL_PRESETS_DIR = "presets"
        const val OFFICIAL_SOURCE_ID = "official"
        const val OFFICIAL_SOURCE_NAME = "KittiSU Official"
    }

    var presets by mutableStateOf<List<LoadedPreset>>(emptyList())
    var isRefreshing by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var sources by mutableStateOf<List<PresetSource>>(emptyList())

    fun loadSources(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val custom = loadCustomSources(prefs)
        sources = listOf(officialSource()) + custom
    }

    fun refreshPresets(context: Context) {
        viewModelScope.launch {
            isRefreshing = true
            errorMessage = null
            try {
                if (sources.isEmpty()) loadSources(context)
                val currentSources = sources
                val netAvailable = withContext(Dispatchers.IO) { isNetworkAvailable(ksuApp) }
                val cloudPresets = if (netAvailable) {
                    fetchCloudPresets(currentSources)
                } else {
                    errorMessage = context.getString(R.string.preset_fetch_failed)
                    emptyList()
                }
                val localPresets = withContext(Dispatchers.IO) { loadLocalPresets(context) }
                presets = cloudPresets + localPresets
            } catch (t: Throwable) {
                Log.e(TAG, "refreshPresets failed", t)
                errorMessage = t.message ?: context.getString(R.string.preset_fetch_failed)
            } finally {
                isRefreshing = false
            }
        }
    }

    fun addCustomSource(name: String, baseUrl: String): Boolean {
        if (!isValidHttpUrl(baseUrl)) return false
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val source = PresetSource(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { normalized },
            baseUrl = normalized,
            isOfficial = false,
            isEnabled = true
        )
        sources = sources + source
        persistCustomSources()
        return true
    }

    fun removeCustomSource(sourceId: String) {
        val target = sources.firstOrNull { it.id == sourceId } ?: return
        if (target.isOfficial) return
        sources = sources.filterNot { it.id == sourceId }
        persistCustomSources()
    }

    suspend fun createLocalPreset(context: Context, preset: PresetEntry) {
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, LOCAL_PRESETS_DIR).apply { mkdirs() }
                val file = File(dir, "${preset.id}.json")
                val wrapped = JSONObject().apply {
                    put("name", preset.id)
                    put("preset", JSONArray().put(presetEntryToJson(preset)))
                }
                file.writeText(wrapped.toString())
            }.onFailure { Log.e(TAG, "createLocalPreset failed: ${preset.id}", it) }
        }
    }

    suspend fun deleteLocalPreset(context: Context, presetId: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(File(context.filesDir, LOCAL_PRESETS_DIR), "$presetId.json")
                if (file.exists()) file.delete()
            }.onFailure { Log.e(TAG, "deleteLocalPreset failed: $presetId", it) }
        }
    }

    suspend fun buildInstallPlan(
        preset: LoadedPreset,
        skipInstalled: Boolean = true,
        isInstalledCheck: (String) -> Boolean = ::isModuleInstalled
    ): ModuleInstallPlan {
        val modules = mutableListOf<PlanModule>()
        for (pm in preset.presetEntry.modules) {
            val installed = isInstalledCheck(pm.moduleId)
            // ponytail: dependency + requirement are surfaced via state flags but
            // don't gate the plan; UI re-reads checkPresetRequirements() / areDependenciesSatisfied()
            // before downloading so the install button can show the failure reason.
            // For "repo" directUrl, resolution can fail (offline, missing module);
            // we surface that as a blank downloadUrl and let the UI decide.
            val resolved = resolveModuleDownloadUrl(pm).orEmpty()
            modules.add(
                PlanModule(
                    presetModule = pm,
                    downloadUrl = resolved,
                    isInstalled = installed,
                    skip = skipInstalled && installed
                )
            )
        }
        val lastReboot = modules.lastOrNull()?.presetModule?.rebootAfter == true
        return ModuleInstallPlan(modules = modules, requiresReboot = lastReboot || preset.presetEntry.requiresRebootAtEnd)
    }

    suspend fun downloadAllModules(
        plan: ModuleInstallPlan,
        onProgress: (current: Int, total: Int) -> Unit
    ): Result<ModuleInstallPlan> {
        val updated = plan.modules.toMutableList()
        val total = updated.count { !it.skip && it.cacheUri == null }
        if (total == 0) {
            onProgress(0, 0)
            return Result.success(plan.copy(modules = updated))
        }
        var current = 0
        for (i in updated.indices) {
            val m = updated[i]
            if (m.skip || m.cacheUri != null) continue
            val url = m.downloadUrl
            if (url.isBlank()) {
                val reason = "URL not resolved for ${m.presetModule.moduleId}"
                Log.e(TAG, reason)
                if (m.presetModule.stopIfFail) {
                    return Result.failure(IllegalStateException(reason))
                }
                continue
            }
            val uri = runCatching {
                withContext(Dispatchers.IO) {
                    downloadToCache(ksuApp, url, m.presetModule.moduleId)
                }
            }.getOrElse {
                Log.e(TAG, "download failed: ${m.presetModule.moduleId} ($url)", it)
                if (m.presetModule.stopIfFail) {
                    return Result.failure(
                        IllegalStateException(
                            "Failed to download ${m.presetModule.moduleId}: ${it.message ?: it::class.java.simpleName}",
                            it
                        )
                    )
                }
                null
            }
            if (uri == null) continue
            updated[i] = m.copy(cacheUri = uri)
            current++
            onProgress(current, total)
        }
        return Result.success(plan.copy(modules = updated))
    }

    // --- internals ---

    private fun isValidHttpUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
        return runCatching {
            val uri = android.net.Uri.parse(trimmed)
            !uri.host.isNullOrBlank() && uri.scheme?.lowercase() in setOf("http", "https")
        }.getOrDefault(false)
    }

    private fun officialSource() = PresetSource(
        id = OFFICIAL_SOURCE_ID,
        name = OFFICIAL_SOURCE_NAME,
        baseUrl = OFFICIAL_PRESETS_BASE_URL,
        isOfficial = true,
        isEnabled = true
    )

    private fun persistCustomSources() {
        val ctx = ksuApp
        val custom = sources.filter { !it.isOfficial }
        val arr = JSONArray()
        for (s in custom) {
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("baseUrl", s.baseUrl)
                    put("isEnabled", s.isEnabled)
                }
            )
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_CUSTOM_SOURCES, arr.toString())
        }
    }

    private fun loadCustomSources(prefs: android.content.SharedPreferences): List<PresetSource> {
        val raw = prefs.getString(KEY_CUSTOM_SOURCES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id", "").ifBlank { return@mapNotNull null }
                val base = o.optString("baseUrl", "").ifBlank { return@mapNotNull null }
                PresetSource(
                    id = id,
                    name = o.optString("name", base),
                    baseUrl = if (base.endsWith("/")) base else "$base/",
                    isOfficial = false,
                    isEnabled = o.optBoolean("isEnabled", true)
                )
            }
        }.getOrElse {
            Log.e(TAG, "loadCustomSources failed", it)
            emptyList()
        }
    }

    private suspend fun fetchCloudPresets(currentSources: List<PresetSource>): List<LoadedPreset> = coroutineScope {
        val enabled = currentSources.filter { it.isEnabled }
        val jobs = enabled.map { src ->
            async(Dispatchers.IO) { fetchFromSource(src) }
        }
        jobs.awaitAll().flatten()
    }

    private suspend fun fetchFromSource(source: PresetSource): List<LoadedPreset> {
        val index = fetchPresetIndex(source.baseUrl) ?: return emptyList()
        val out = mutableListOf<LoadedPreset>()
        for (file in index.files) {
            val (parsed, status) = fetchPresetFileWithSignature(source.baseUrl, file)
            if (parsed == null) continue
            for (entry in parsed.preset) {
                out.add(
                    LoadedPreset(
                        sourceId = source.id,
                        fileName = file,
                        presetEntry = entry,
                        verificationStatus = status,
                        isLocal = false
                    )
                )
            }
        }
        return out
    }

    private fun loadLocalPresets(context: Context): List<LoadedPreset> {
        val dir = File(context.filesDir, LOCAL_PRESETS_DIR)
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        val out = mutableListOf<LoadedPreset>()
        for (f in files) {
            runCatching {
                val json = f.readText()
                val parsed = parsePresetFile(json, f.name) ?: return@runCatching
                for (entry in parsed.preset) {
                    out.add(
                        LoadedPreset(
                            sourceId = "local",
                            fileName = f.name,
                            presetEntry = entry,
                            verificationStatus = PresetVerificationStatus.VERIFIED,
                            isLocal = true
                        )
                    )
                }
            }.onFailure { Log.e(TAG, "loadLocalPresets: bad file ${f.name}", it) }
        }
        return out
    }

    private fun downloadToCache(context: Context, url: String, moduleId: String): Uri? {
        val request = Request.Builder().url(url).build()
        ksuApp.okhttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful || resp.body == null) return null
            val outFile = File(
                context.cacheDir,
                "preset_${moduleId}_${System.currentTimeMillis()}.zip"
            )
            resp.body!!.byteStream().use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            return Uri.fromFile(outFile)
        }
    }

    // ponytail: hand-rolled JSON to avoid pulling in a serialization dep just for
    // local presets. Mirrors parsePresetEntry in ModulePresetApi.kt.
    private fun presetEntryToJson(entry: PresetEntry): JSONObject {
        val modules = JSONArray()
        for (m in entry.modules) modules.put(presetModuleToJson(m))
        return JSONObject().apply {
            put("id", entry.id)
            put("destination", entry.destination)
            entry.author?.let { put("author", it) }
            put("requiresRebootAtEnd", entry.requiresRebootAtEnd)
            put("modules", modules)
        }
    }

    private fun presetModuleToJson(m: PresetModule): JSONObject = JSONObject().apply {
        put("moduleName", m.moduleName)
        put("moduleId", m.moduleId)
        m.moduleVersion?.let { put("moduleVersion", it) } ?: put("moduleVersion", false)
        put("directUrl", m.directUrl)
        put("stopIfFail", m.stopIfFail)
        put("rebootAfter", m.rebootAfter)
        put("dependsOn", JSONArray(m.dependsOn))
        m.requirement?.let { put("requirement", presetRequirementToJson(it)) }
    }

    private fun presetRequirementToJson(r: PresetRequirement): JSONObject = JSONObject().apply {
        r.susfs?.let { put("susfs", it) }
        r.kernelsu?.let { put("kernelsu", it) }
        r.android?.let { put("android", it) }
        r.metadata?.let { put("metadata", it) }
    }
}
