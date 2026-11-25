package anhiutangerinee.kittisu.ui.viewmodel

import android.content.Context
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.activity.util.isNetworkAvailable
import anhiutangerinee.kittisu.ui.util.HanziToPinyin
import anhiutangerinee.kittisu.ui.util.module.ReleaseAssetInfo
import anhiutangerinee.kittisu.ui.util.module.ReleaseInfo
import anhiutangerinee.kittisu.ui.util.module.fetchModuleDetail
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * @author AlexLiuDev233
 * @date 2025/12/6.
 */
class ModuleRepoViewModel : ViewModel() {
    companion object {
        private const val TAG = "ModuleRepoViewModel"
        const val KERNELSU_REPO_URL = "https://modules.kernelsu.org/modules.json"
        private const val PREFS_NAME = "settings"
        private const val KEY_CUSTOM_REPOS = "module_repo_custom_sources"
        private const val KEY_OFFICIAL_ENABLED = "module_repo_official_enabled"
    }

    @Immutable
    data class RepoSource(
        val url: String,
        val name: String,
        val enabled: Boolean = true,
        val isBuiltIn: Boolean = false,
    )

    var sortStargazerCountFirst by mutableStateOf(false)
    var sources by mutableStateOf<List<RepoSource>>(loadSourcesFromPrefs())
        private set

    private var _modules = mutableStateOf<List<RepoModule>>(emptyList())
    val modules: List<RepoModule> by derivedStateOf {
        _modules.value
            .filter { module ->
                search.isBlank() ||
                    module.moduleId.contains(search, true) ||
                    module.moduleName.contains(search, true) ||
                    module.summary.contains(search, true) ||
                    module.sourceName.contains(search, true) ||
                    HanziToPinyin.getInstance().toPinyinString(module.moduleName)?.contains(search, true) == true
            }
            .sortedWith(
                compareByDescending<RepoModule> { it.installed }
                    .thenByDescending { if (sortStargazerCountFirst) it.stargazerCount else 0 }
            )
    }

    var isRefreshing by mutableStateOf(false)
    var search by mutableStateOf("")

    @Immutable
    @Parcelize
    data class Author(
        val name: String,
        val link: String,
    ) : Parcelable

    @Immutable
    @Parcelize
    data class RepoModule(
        val moduleId: String,
        val moduleName: String,
        val authors: String,
        val authorList: List<Author>,
        val summary: String,
        val metamodule: Boolean,
        val stargazerCount: Int,
        val updatedAt: String,
        val createdAt: String,
        val latestRelease: String,
        val latestReleaseTime: String,
        val latestVersionCode: Int,
        val latestAsset: ReleaseInfo?,
        val installed: Boolean,
        val readme: String,
        val sourceUrl: String,
        val releases: List<ReleaseInfo>,
        val sourceName: String = "KernelSU",
    ) : Parcelable

    private fun loadSourcesFromPrefs(): List<RepoSource> {
        val prefs = ksuApp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val officialEnabled = prefs.getBoolean(KEY_OFFICIAL_ENABLED, true)
        val builtIn = RepoSource(
            url = KERNELSU_REPO_URL,
            name = "KernelSU Official",
            enabled = officialEnabled,
            isBuiltIn = true,
        )
        val raw = prefs.getString(KEY_CUSTOM_REPOS, "[]") ?: "[]"
        val custom = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val url = obj.optString("url").trim()
                val name = obj.optString("name").trim().ifBlank { url }
                if (url.isBlank() || url == KERNELSU_REPO_URL) return@mapNotNull null
                RepoSource(
                    url = url,
                    name = name,
                    enabled = obj.optBoolean("enabled", true),
                    isBuiltIn = false,
                )
            }.distinctBy { it.url }
        }.getOrDefault(emptyList())
        return listOf(builtIn) + custom
    }

    private fun saveSourcesToPrefs(updated: List<RepoSource>) {
        val prefs = ksuApp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val official = updated.firstOrNull { it.isBuiltIn }
        val arr = JSONArray()
        updated.filterNot { it.isBuiltIn }.forEach { src ->
            arr.put(
                JSONObject().apply {
                    put("url", src.url)
                    put("name", src.name)
                    put("enabled", src.enabled)
                }
            )
        }
        prefs.edit {
            putBoolean(KEY_OFFICIAL_ENABLED, official?.enabled ?: true)
            putString(KEY_CUSTOM_REPOS, arr.toString())
        }
    }

    fun normalizeRepoUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return if (withScheme.endsWith(".json", ignoreCase = true)) {
            withScheme
        } else {
            withScheme.trimEnd('/') + "/json/modules.json"
        }
    }

    fun addCustomSource(rawUrl: String, customName: String = ""): Boolean {
        val normalized = normalizeRepoUrl(rawUrl)
        if (normalized.isBlank() || sources.any { it.url.equals(normalized, ignoreCase = true) }) {
            return false
        }
        val derivedName = customName.trim().ifBlank {
            runCatching { java.net.URI(normalized).host?.removePrefix("www.") }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: normalized
        }
        val updated = sources + RepoSource(
            url = normalized,
            name = derivedName,
            enabled = true,
            isBuiltIn = false,
        )
        sources = updated
        saveSourcesToPrefs(updated)
        refresh()
        return true
    }

    fun toggleSource(url: String, enabled: Boolean) {
        val updated = sources.map { if (it.url == url) it.copy(enabled = enabled) else it }
        sources = updated
        saveSourcesToPrefs(updated)
        refresh()
    }

    fun removeCustomSource(url: String) {
        val updated = sources.filterNot { !it.isBuiltIn && it.url == url }
        sources = updated
        saveSourcesToPrefs(updated)
        refresh()
    }

    fun refresh(
        onFailure: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val netAvailable = isNetworkAvailable(ksuApp)
            withContext(Dispatchers.Main) { isRefreshing = true }
            val parsed = withContext(Dispatchers.IO) {
                if (!netAvailable) null else fetchAllEnabledSources(onFailure)
            }
            withContext(Dispatchers.Main) {
                if (parsed != null) {
                    _modules.value = parsed
                } else {
                    Toast.makeText(
                        ksuApp,
                        ksuApp.getString(R.string.network_offline), Toast.LENGTH_SHORT
                    ).show()
                }
                isRefreshing = false
            }
        }
    }

    private suspend fun fetchAllEnabledSources(
        onFailure: (() -> Unit)? = null
    ): List<RepoModule> = coroutineScope {
        val enabledSources = sources.filter { it.enabled }
        if (enabledSources.isEmpty()) return@coroutineScope emptyList()

        val results = enabledSources.map { src ->
            async(Dispatchers.IO) {
                if (src.isBuiltIn) {
                    fetchOfficialModulesInternal(onFailure)
                } else {
                    fetchMmrlRepoModules(src)
                }
            }
        }.awaitAll().flatten()

        // Deduplicate by (sourceName + moduleId) while prioritizing official KernelSU entries first
        val seenIds = HashSet<String>()
        results.filter { seenIds.add(it.moduleId) }
    }

    private fun isModuleInstalledSafe(moduleId: String): Boolean {
        return runCatching {
            SuFile.open("/data/adb/modules/$moduleId/module.prop").exists()
        }.getOrDefault(false)
    }

    private suspend fun fetchOfficialModulesInternal(
        onFailure: (() -> Unit)? = null
    ): List<RepoModule> {
        return runCatching {
            val request = Request.Builder().url(KERNELSU_REPO_URL).build()
            ksuApp.okhttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val json = JSONArray(body)
                coroutineScope {
                    val jobs = (0 until json.length()).map { idx ->
                        async {
                            val item = json.optJSONObject(idx)
                            if (item != null) parseOfficialRepoModule(item) else null
                        }
                    }
                    jobs.awaitAll().filterNotNull()
                }
            }
        }.onFailure {
            onFailure?.invoke()
        }.getOrElse {
            Log.e(TAG, "fetch official modules failed", it)
            emptyList()
        }
    }

    private fun fetchMmrlRepoModules(source: RepoSource): List<RepoModule> {
        return runCatching {
            val request = Request.Builder()
                .url(source.url)
                .header("User-Agent", "SovaSU-Manager")
                .build()
            ksuApp.okhttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string()?.trim() ?: return emptyList()
                val repoName: String
                val modulesArray: JSONArray
                if (body.startsWith("{")) {
                    val root = JSONObject(body)
                    repoName = root.optString("name").trim().ifBlank { source.name }
                    modulesArray = root.optJSONArray("modules") ?: return emptyList()
                } else if (body.startsWith("[")) {
                    repoName = source.name
                    modulesArray = JSONArray(body)
                } else {
                    return emptyList()
                }

                (0 until modulesArray.length()).mapNotNull { idx ->
                    val obj = modulesArray.optJSONObject(idx) ?: return@mapNotNull null
                    parseMmrlModule(obj, repoName)
                }
            }
        }.getOrElse {
            Log.w(TAG, "fetch custom repo ${source.url} failed", it)
            emptyList()
        }
    }

    private fun parseMmrlModule(obj: JSONObject, repoName: String): RepoModule? {
        val id = obj.optString("id", obj.optString("moduleId", "")).trim()
        if (id.isEmpty()) return null
        val name = obj.optString("name", obj.optString("moduleName", id)).trim()
        val authorStr = obj.optString("author", "").trim()
        val authorList = if (authorStr.isNotBlank()) listOf(Author(authorStr, "")) else emptyList()
        val summary = obj.optString("description", obj.optString("summary", "")).trim()
        val version = obj.optString("version", "").trim()
        val versionCode = obj.optInt("versionCode", 0)
        val sourceUrl = obj.optString("source", obj.optString("homepage", "")).trim()

        val releases = mutableListOf<ReleaseInfo>()
        val versionsArr = obj.optJSONArray("versions")
        if (versionsArr != null) {
            for (vIdx in 0 until versionsArr.length()) {
                val vObj = versionsArr.optJSONObject(vIdx) ?: continue
                val zipUrl = vObj.optString("zipUrl", "").trim()
                if (zipUrl.isBlank()) continue
                val vTag = vObj.optString("version", version).trim().ifBlank { "v${vObj.optInt("versionCode", versionCode)}" }
                val fileName = zipUrl.substringAfterLast('/').ifBlank { "$id-$vTag.zip" }
                val asset = ReleaseAssetInfo(
                    name = fileName,
                    downloadUrl = zipUrl,
                    size = vObj.optLong("size", 0L),
                    downloadCount = 0
                )
                releases.add(
                    ReleaseInfo(
                        name = vTag,
                        tagName = vTag,
                        publishedAt = vObj.optString("timestamp", ""),
                        descriptionHTML = vObj.optString("changelog", ""),
                        assets = listOf(asset)
                    )
                )
            }
        }
        // Fallback if repo exposes top-level zipUrl without versions array
        if (releases.isEmpty()) {
            val topZip = obj.optString("zipUrl", obj.optString("downloadUrl", "")).trim()
            if (topZip.isNotBlank()) {
                val fileName = topZip.substringAfterLast('/').ifBlank { "$id.zip" }
                releases.add(
                    ReleaseInfo(
                        name = version.ifBlank { "latest" },
                        tagName = version.ifBlank { "latest" },
                        publishedAt = "",
                        descriptionHTML = "",
                        assets = listOf(ReleaseAssetInfo(fileName, topZip, 0L, 0))
                    )
                )
            }
        }

        val latestRel = releases.firstOrNull()
        return RepoModule(
            moduleId = id,
            moduleName = name,
            authors = authorStr,
            authorList = authorList,
            summary = summary,
            metamodule = obj.optBoolean("metamodule", false),
            stargazerCount = obj.optInt("stars", 0),
            updatedAt = latestRel?.publishedAt.orEmpty(),
            createdAt = "",
            latestRelease = latestRel?.name ?: version,
            latestReleaseTime = latestRel?.publishedAt.orEmpty(),
            latestVersionCode = versionCode,
            latestAsset = latestRel,
            installed = isModuleInstalledSafe(id),
            readme = obj.optString("readme", summary),
            sourceUrl = sourceUrl,
            releases = releases,
            sourceName = repoName,
        )
    }

    private suspend fun parseOfficialRepoModule(item: JSONObject): RepoModule? {
        val moduleId = item.optString("moduleId", "")
        if (moduleId.isEmpty()) return null
        val moduleName = item.optString("moduleName", "")
        val authorsArray = item.optJSONArray("authors")
        val authorList = if (authorsArray != null) {
            (0 until authorsArray.length())
                .mapNotNull { idx ->
                    val authorObj = authorsArray.optJSONObject(idx) ?: return@mapNotNull null
                    val name = authorObj.optString("name", "").trim()
                    var link = authorObj.optString("link", "").trim()
                    if (link.startsWith("`") && link.endsWith("`") && link.length >= 2) {
                        link = link.substring(1, link.length - 1)
                    }
                    if (name.isEmpty()) null else Author(name = name, link = link)
                }
        } else {
            emptyList()
        }
        val authors = if (authorList.isNotEmpty()) authorList.joinToString(", ") { it.name } else item.optString("authors", "")
        val summary = item.optString("summary", "")
        val metamodule = item.optBoolean("metamodule", false)
        val stargazerCount = item.optInt("stargazerCount", 0)
        val updatedAt = item.optString("updatedAt", "")
        val createdAt = item.optString("createdAt", "")

        var latestRelease = ""
        var latestReleaseTime = ""
        var latestVersionCode = 0
        var latestAsset: ReleaseInfo? = null
        val releases: MutableList<ReleaseInfo> = ArrayList()
        val lr = item.optJSONObject("latestRelease")
        if (lr != null) {
            val lrName = lr.optString("name", lr.optString("version", ""))
            val lrTime = lr.optString("time", "")
            val vcAny = lr.opt("versionCode")
            latestVersionCode = when (vcAny) {
                is Number -> vcAny.toInt()
                is String -> vcAny.toIntOrNull() ?: 0
                else -> 0
            }
            latestRelease = lrName
            latestReleaseTime = lrTime
        }
        val detail = withContext(Dispatchers.IO) {
            fetchModuleDetail(moduleId)
        }

        detail?.releases?.forEach { it ->
            releases.add(it)
            if (it.name != latestRelease) return@forEach
            latestAsset = it
        }

        return RepoModule(
            moduleId = moduleId,
            moduleName = moduleName,
            authors = authors,
            authorList = authorList,
            summary = summary,
            metamodule = metamodule,
            stargazerCount = stargazerCount,
            updatedAt = updatedAt,
            createdAt = createdAt,
            latestRelease = latestRelease,
            latestReleaseTime = latestReleaseTime,
            latestVersionCode = latestVersionCode,
            latestAsset = latestAsset,
            installed = isModuleInstalledSafe(moduleId),
            readme = detail?.readme.orEmpty(),
            sourceUrl = detail?.sourceUrl.orEmpty(),
            releases = releases,
            sourceName = "KernelSU",
        )
    }
}
