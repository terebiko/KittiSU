package anhiutangerinee.kittisu.ui.viewmodel

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.util.HanziToPinyin
import anhiutangerinee.kittisu.ui.util.getRootShell
import anhiutangerinee.kittisu.ui.util.listModules
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */
class ModuleViewModel : ViewModel() {

    companion object {
        private const val TAG = "ModuleViewModel"
        private var modules by mutableStateOf<List<ModuleInfo>>(emptyList())

        internal fun shouldCheckUpdate(
            module: ModuleInfo,
            knownVersions: Set<Pair<String, Int>>,
        ) = module.updateJson.isNotBlank() && module.enabled && !module.remove && !module.update &&
            (module.dirId to module.versionCode) !in knownVersions
    }

    val moduleSize = MutableStateFlow<Map<String, String>>(emptyMap())
    private var updateCheckJob: Job? = null

    fun loadSize(dirId: String) = viewModelScope.launch(Dispatchers.IO) {
        moduleSize.update {
            it + (dirId to
                    formatFileSize(
                        try {
                            val shell = getRootShell()
                            val command =
                                "/data/adb/ksu/bin/busybox du -sb /data/adb/modules/$dirId"
                            val result = shell.newJob().add(command).to(ArrayList(), null).exec()

                            if (result.isSuccess && result.out.isNotEmpty()) {
                                val sizeStr = result.out.firstOrNull()?.split("\t")?.firstOrNull()
                                sizeStr?.toLongOrNull() ?: 0L
                            } else {
                                0L
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "计算模块大小失败 $dirId: ${e.message}")
                            0L
                        }
                    ))
        }
    }

    data class ModuleUpdateInfo(
        val zipUrl: String,
        val version: String,
        val changelog: String
    )

    @Stable
    data class ModuleInfo(
        val id: String,
        val name: String,
        val author: String,
        val version: String,
        val versionCode: Int,
        val description: String,
        val enabled: Boolean,
        val update: Boolean,
        val remove: Boolean,
        val updateJson: String,
        val hasWebUi: Boolean,
        val hasActionScript: Boolean,
        val metamodule: Boolean,
        val actionIconPath: String?,
        val webUiIconPath: String?,
        val banner: String?,
        val dirId: String, // real module id (dir name)
        val moduleUpdate: ModuleUpdateInfo?
    )

    internal object Parser {
        fun parseModuleInfo(obj: JSONObject): ModuleInfo {
            val dirId = obj.optString("dir_id").trim()
            require(dirId.isNotEmpty()) { "module dir_id is missing" }
            val moduleId = obj.optString("id").trim().ifEmpty { dirId }
            return ModuleInfo(
                id = moduleId,
                name = obj.optString("name").ifBlank { moduleId },
                author = obj.optString("author", "Unknown"),
                version = obj.optString("version", "Unknown"),
                versionCode = obj.getIntCompat("versionCode", 0),
                description = obj.optString("description"),
                enabled = obj.getBooleanCompat("enabled"),
                update = obj.getBooleanCompat("update"),
                remove = obj.getBooleanCompat("remove"),
                updateJson = obj.optString("updateJson"),
                hasWebUi = obj.getBooleanCompat("web"),
                hasActionScript = obj.getBooleanCompat("action"),
                metamodule = obj.getBooleanCompat("metamodule"),
                actionIconPath = obj.optString("actionIcon").takeIf { it.isNotBlank() },
                webUiIconPath = obj.optString("webuiIcon").takeIf { it.isNotBlank() },
                banner = obj.optString("banner").takeIf { it.isNotBlank() },
                dirId = dirId,
                moduleUpdate = null
            )
        }

        fun parseModuleList(
            array: JSONArray,
            onInvalid: (index: Int, error: Throwable) -> Unit = { _, _ -> }
        ): List<ModuleInfo> = (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index)
            if (obj == null) {
                onInvalid(index, IllegalArgumentException("module entry is not an object"))
                return@mapNotNull null
            }
            runCatching { parseModuleInfo(obj) }
                .onFailure { onInvalid(index, it) }
                .getOrNull()
        }
    }

    var isRefreshing by mutableStateOf(false)
        private set
    var search by mutableStateOf("")

    var sortEnabledFirst by mutableStateOf(false)
    var sortActionFirst by mutableStateOf(false)
    val moduleList by derivedStateOf {
        val comparator =
            compareBy<ModuleInfo>(
                {
                    val executable = it.hasWebUi || it.hasActionScript
                    when {
                        it.metamodule && it.enabled -> 0
                        sortEnabledFirst && sortActionFirst -> when {
                            it.enabled && executable -> 1
                            it.enabled -> 2
                            executable -> 3
                            else -> 4
                        }
                        sortEnabledFirst && !sortActionFirst -> if (it.enabled) 1 else 2
                        !sortEnabledFirst && sortActionFirst -> if (executable) 1 else 2
                        else -> 1
                    }
                },
                { if (sortEnabledFirst) !it.enabled else 0 },
                { if (sortActionFirst) !(it.hasWebUi || it.hasActionScript) else 0 },
            ).thenBy(Collator.getInstance(Locale.getDefault()), ModuleInfo::id)
                .thenBy(ModuleInfo::dirId)
        modules.filter {
            it.id.contains(search, true) || it.name.contains(search, true) || HanziToPinyin.getInstance()
                .toPinyinString(it.name)?.contains(search, true) == true
        }.sortedWith(comparator).also {
            isRefreshing = false
        }
    }

    var hasModuleRequireMount by mutableStateOf(false)
        private set

    var isNeedRefresh by mutableStateOf(false)
        private set

    fun markNeedRefresh() {
        isNeedRefresh = true
    }

    fun fetchModuleList(
        manualRefresh: Boolean = false,
        callBack: () -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing = true

            val oldModuleList = modules

            val start = SystemClock.elapsedRealtime()

            kotlin.runCatching {
                val result = listModules()

                Log.i(TAG, "result: $result")

                val knownModuleVersions = if (manualRefresh) {
                    emptySet()
                } else {
                    oldModuleList.mapTo(mutableSetOf()) { it.dirId to it.versionCode }
                }

                val array = JSONArray(result)
                val parsedModules = Parser.parseModuleList(array) { index, error ->
                    Log.w(TAG, "Ignoring invalid module entry at index $index", error)
                }
                modules = parsedModules.map { module ->
                    module.copy(
                        moduleUpdate = oldModuleList.firstOrNull {
                            it.dirId == module.dirId && it.versionCode == module.versionCode
                        }?.moduleUpdate
                    )
                }

                hasModuleRequireMount = modules.map { module ->
                    async(Dispatchers.IO) {
                        SuFile.open("/data/adb/modules/${module.dirId}/system").exists()
                                && !SuFile.open("/data/adb/modules/${module.dirId}/skip_mount")
                            .exists() // skip_mount
                                && !SuFile.open("/data/adb/modules/${module.dirId}/disable")
                            .exists() // disable
                                && !SuFile.open("/data/adb/modules/${module.dirId}/remove")
                            .exists() // remove
                    }
                }.awaitAll().any { it }

                isNeedRefresh = false
                isRefreshing = false
                callBack()
                checkModuleUpdatesInBackground(modules, knownModuleVersions)
            }.onFailure { e ->
                Log.e(TAG, "fetchModuleList: ", e)
                isRefreshing = false
                callBack()
            }

            // when both old and new is kotlin.collections.EmptyList
            // moduleList update will don't trigger
            if (oldModuleList === modules) {
                isRefreshing = false
            }

            Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}, modules: $modules")
        }
    }

    private fun checkModuleUpdatesInBackground(
        snapshot: List<ModuleInfo>,
        knownVersions: Set<Pair<String, Int>>,
    ) {
        updateCheckJob?.cancel()
        updateCheckJob = viewModelScope.launch(Dispatchers.IO) {
            val updated = snapshot.map { module ->
                async {
                    if (shouldCheckUpdate(module, knownVersions)) {
                        module.copy(moduleUpdate = checkUpdate(module.updateJson, module.versionCode))
                    } else {
                        module
                    }
                }
            }.awaitAll()

            if (modules === snapshot) modules = updated
        }
    }

    private fun sanitizeVersionString(version: String): String {
        return version.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
    }

    fun checkUpdate(updateUrl: String, versionCode: Int): ModuleUpdateInfo? {
        val isCheckUpdateEnabled = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("check_update", true)
        if (!isCheckUpdateEnabled) {
            return null
        }
        // download updateJson
        val result = kotlin.runCatching {
            Log.i(TAG, "checkUpdate url: $updateUrl")

            val request = okhttp3.Request.Builder()
                .url(updateUrl)
                .build()

            val response = ksuApp.okhttpClient.newCall(request).execute()

            Log.d(TAG, "checkUpdate code: ${response.code}")
            if (response.isSuccessful) {
                response.body?.string() ?: ""
            } else {
                Log.d(TAG, "checkUpdate failed: ${response.message}")
                ""
            }
        }.getOrElse { e ->
            Log.e(TAG, "checkUpdate exception", e)
            ""
        }

        Log.i(TAG, "checkUpdate result: $result")

        if (result.isEmpty()) {
            return null
        }

        val updateJson = kotlin.runCatching {
            JSONObject(result)
        }.getOrNull() ?: return null

        var version = updateJson.optString("version", "")
        version = sanitizeVersionString(version)
        val onlineVersionCode = updateJson.optInt("versionCode", 0)
        val zipUrl = updateJson.optString("zipUrl", "")
        val changelog = updateJson.optString("changelog", "")
        if (onlineVersionCode <= versionCode || zipUrl.isEmpty()) {
            return null
        }

        return ModuleUpdateInfo(zipUrl, version, changelog)
    }
}

private fun JSONObject.getBooleanCompat(key: String, default: Boolean = false): Boolean {
    if (!has(key)) return default
    return when (val value = opt(key)) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true) || value == "1"
        is Number -> value.toInt() != 0
        else -> default
    }
}

private fun JSONObject.getIntCompat(key: String, default: Int = 0): Int {
    if (!has(key)) return default
    return when (val value = opt(key)) {
        is Int -> value
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }
}

/**
 * 格式化文件大小
 */
fun formatFileSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val tb = gb * 1024

    return when {
        bytes >= tb -> "%.2f TB".format(bytes / tb)
        bytes >= gb -> "%.2f GB".format(bytes / gb)
        bytes >= mb -> "%.2f MB".format(bytes / mb)
        bytes >= kb -> "%.2f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}
