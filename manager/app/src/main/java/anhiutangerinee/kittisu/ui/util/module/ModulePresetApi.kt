package anhiutangerinee.kittisu.ui.util.module

import android.os.Build
import android.util.Log
import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.activity.util.isNetworkAvailable
import anhiutangerinee.kittisu.ui.util.getSuSFSVersion
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

const val OFFICIAL_PRESETS_BASE_URL =
    "https://raw.githubusercontent.com/terebiko/KittiSU/main/presets/"

private const val PRESET_API_TAG = "ModulePresetApi"

sealed class RequirementCheckResult {
    object Passed : RequirementCheckResult()
    data class Failed(val reason: String, val type: RequirementType) : RequirementCheckResult()
}

enum class RequirementType { SUSFS, KERNELSU, ANDROID, METADATA }

// ponytail: duplicated from SuSFSManager.compareVersions; kept local so this file
// has no dependency on SuSFSManager (it's UI-only and lives outside this package).
private fun compareVersions(v1: String, v2: String): Int {
    val v1Parts = v1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val v2Parts = v2.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val maxLength = maxOf(v1Parts.size, v2Parts.size)
    for (i in 0 until maxLength) {
        val a = v1Parts.getOrNull(i) ?: 0
        val b = v2Parts.getOrNull(i) ?: 0
        if (a != b) return a.compareTo(b)
    }
    return 0
}

private fun stripTicks(s: String): String {
    val t = s.trim()
    return if (t.length >= 2 && t.startsWith("`") && t.endsWith("`")) t.substring(1, t.length - 1) else t
}

private fun joinUrl(base: String, file: String): String {
    val b = if (base.endsWith("/")) base else "$base/"
    return b + file.trimStart('/')
}

private const val GITHUB_LATEST_SCHEME = "github-latest://"
private val ZIP_CONTENT_TYPES = setOf("application/zip", "application/x-zip-compressed")

internal data class GitHubOwnerRepo(val owner: String, val repo: String) {
    companion object {
        fun parse(raw: String): GitHubOwnerRepo? {
            val path = raw.removePrefix(GITHUB_LATEST_SCHEME).trim('/')
            val parts = path.split('/').filter { it.isNotBlank() }
            if (parts.size < 2) return null
            return GitHubOwnerRepo(parts[0], parts[1])
        }
    }
}

suspend fun fetchGitHubLatestReleaseAsset(owner: String, repo: String): String? = withContext(Dispatchers.IO) {
    if (!isNetworkAvailable(ksuApp)) return@withContext null
    val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
    runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.e(PRESET_API_TAG, "GitHub latest release HTTP ${resp.code}: $url")
                return@use null
            }
            val body = resp.body?.string() ?: return@use null
            val obj = JSONObject(body)
            val assets = obj.optJSONArray("assets") ?: return@use null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val contentType = asset.optString("content_type", "")
                val name = asset.optString("name", "")
                val isZip = contentType in ZIP_CONTENT_TYPES || name.endsWith(".zip", ignoreCase = true)
                if (!isZip) continue
                asset.optString("browser_download_url", "").ifBlank { null }?.let { return@use it }
            }
            null
        }
    }.getOrElse {
        Log.e(PRESET_API_TAG, "fetchGitHubLatestReleaseAsset failed: $url", it)
        null
    }
}

suspend fun fetchPresetIndex(baseUrl: String): PresetIndex? = withContext(Dispatchers.IO) {
    if (!isNetworkAvailable(ksuApp)) return@withContext null
    val url = joinUrl(baseUrl, "index.json")
    runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            val obj = JSONObject(body)
            val version = obj.optInt("version", 1)
            val updatedAt = obj.optString("updatedAt", "")
            val filesArr = obj.optJSONArray("files") ?: JSONArray()
            val files = (0 until filesArr.length()).mapNotNull { filesArr.optString(it, "").ifBlank { null } }
            PresetIndex(version = version, updatedAt = updatedAt, files = files)
        }
    }.getOrElse {
        Log.e(PRESET_API_TAG, "fetchPresetIndex failed: $url", it)
        null
    }
}

suspend fun fetchPresetFileWithSignature(
    baseUrl: String,
    fileName: String
): Pair<PresetFile?, PresetVerificationStatus> = withContext(Dispatchers.IO) {
    if (!isNetworkAvailable(ksuApp)) return@withContext null to PresetVerificationStatus.UNVERIFIED
    val jsonUrl = joinUrl(baseUrl, fileName)
    val signUrl = joinUrl(baseUrl, "$fileName.sign")
    val jsonContent = runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(jsonUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.body?.string()
        }
    }.getOrElse {
        Log.e(PRESET_API_TAG, "fetchPresetFile failed: $jsonUrl", it)
        null
    } ?: return@withContext null to PresetVerificationStatus.UNVERIFIED

    val signatureContent = runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(signUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.body?.string()?.trim()
        }
    }.getOrElse {
        Log.e(PRESET_API_TAG, "fetch signature failed: $signUrl", it)
        null
    }

    val status = verifyPresetSignature(jsonContent, signatureContent)
    val parsed = runCatching { parsePresetFile(jsonContent, fileName) }.getOrElse {
        Log.e(PRESET_API_TAG, "parse preset file failed: $fileName", it)
        null
    }
    parsed to status
}

internal fun parsePresetFile(json: String, fileName: String): PresetFile? {
    val obj = JSONObject(json)
    val name = obj.optString("name", fileName.removeSuffix(".json")).ifBlank { fileName.removeSuffix(".json") }
    val arr = obj.optJSONArray("preset") ?: return PresetFile(name = name, preset = emptyList())
    val entries = (0 until arr.length()).mapNotNull { parsePresetEntry(arr.optJSONObject(it)) }
    return PresetFile(name = name, preset = entries)
}

private fun parsePresetEntry(obj: JSONObject?): PresetEntry? {
    if (obj == null) return null
    val id = obj.optString("id", "").ifBlank { return null }
    val destination = obj.optString("destination", id)
    val author = obj.optString("author", "").ifBlank { null }
    val requiresReboot = obj.optBoolean("requiresRebootAtEnd", false)
    val modulesArr = obj.optJSONArray("modules") ?: JSONArray()
    val modules = (0 until modulesArr.length()).mapNotNull { parsePresetModule(modulesArr.optJSONObject(it)) }
    return PresetEntry(
        id = id,
        destination = destination,
        author = author,
        requiresRebootAtEnd = requiresReboot,
        modules = modules
    )
}

private fun parsePresetModule(obj: JSONObject?): PresetModule? {
    if (obj == null) return null
    val moduleName = obj.optString("moduleName", "").ifBlank { return null }
    val moduleId = obj.optString("moduleId", "").ifBlank { return null }
    val directUrl = obj.optString("directUrl", "").ifBlank { return null }
    val moduleVersion: String? = when (val v = obj.opt("moduleVersion")) {
        is Boolean -> if (v) null else null
        is String -> v.ifBlank { null }
        else -> null
    }
    val stopIfFail = obj.optBoolean("stopIfFail", true)
    val rebootAfter = obj.optBoolean("rebootAfter", false)
    val depsArr = obj.optJSONArray("dependsOn") ?: JSONArray()
    val deps = (0 until depsArr.length()).mapNotNull { depsArr.optString(it, "").ifBlank { null } }
    val requirement = parseRequirement(obj.optJSONObject("requirement"))
    return PresetModule(
        moduleName = moduleName,
        moduleId = moduleId,
        moduleVersion = moduleVersion,
        directUrl = directUrl,
        stopIfFail = stopIfFail,
        rebootAfter = rebootAfter,
        dependsOn = deps,
        requirement = requirement
    )
}

private fun parseRequirement(obj: JSONObject?): PresetRequirement? {
    if (obj == null) return null
    val susfs = obj.optString("susfs", "").ifBlank { null }
    val kernelsu = obj.optString("kernelsu", "").ifBlank { null }
    val android = obj.optString("android", "").ifBlank { null }
    val metadata = obj.optString("metadata", "").ifBlank { null }
    if (susfs == null && kernelsu == null && android == null && metadata == null) return null
    return PresetRequirement(susfs = susfs, kernelsu = kernelsu, android = android, metadata = metadata)
}

fun verifyPresetSignature(jsonContent: String, signatureContent: String?): PresetVerificationStatus {
    if (signatureContent.isNullOrBlank()) return PresetVerificationStatus.MISSING_SIGNATURE
    val expected = computePresetSha256(jsonContent)
    val provided = signatureContent.trim().lowercase()
    return if (provided.equals(expected, ignoreCase = true)) {
        PresetVerificationStatus.VERIFIED
    } else {
        PresetVerificationStatus.INVALID_SIGNATURE
    }
}

fun computePresetSha256(jsonContent: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(jsonContent.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

suspend fun resolveModuleDownloadUrl(module: PresetModule): String? = withContext(Dispatchers.IO) {
    val raw = module.directUrl.trim()
    when {
        raw.equals("repo", ignoreCase = true) -> {
            val detail = fetchModuleDetail(module.moduleId)
            detail?.latestAssetUrl?.let { stripTicks(it).ifBlank { null } }
        }
        raw.startsWith(GITHUB_LATEST_SCHEME, ignoreCase = true) -> {
            val parsed = GitHubOwnerRepo.parse(raw)
                ?: run {
                    Log.e(PRESET_API_TAG, "Invalid $GITHUB_LATEST_SCHEME URL: $raw")
                    return@withContext null
                }
            fetchGitHubLatestReleaseAsset(parsed.owner, parsed.repo)
        }
        raw.isNotBlank() -> stripTicks(raw).ifBlank { null }
        else -> null
    }
}

fun checkPresetRequirements(requirement: PresetRequirement?): RequirementCheckResult {
    if (requirement == null) return RequirementCheckResult.Passed

    requirement.susfs?.let { min ->
        val current = runCatching { getSuSFSVersion().trim() }.getOrDefault("")
        if (current.isNotEmpty() && compareVersions(current, min) < 0) {
            return RequirementCheckResult.Failed(
                reason = "SuSFS $current < required $min",
                type = RequirementType.SUSFS
            )
        }
    }

    requirement.kernelsu?.let { min ->
        val current = runCatching { Natives.version.toString() }.getOrDefault("0")
        if (compareVersions(current, min) < 0) {
            return RequirementCheckResult.Failed(
                reason = "KernelSU $current < required $min",
                type = RequirementType.KERNELSU
            )
        }
    }

    requirement.android?.let { min ->
        val minSdk = min.toIntOrNull() ?: 0
        if (Build.VERSION.SDK_INT < minSdk) {
            return RequirementCheckResult.Failed(
                reason = "Android ${Build.VERSION.SDK_INT} < required $minSdk",
                type = RequirementType.ANDROID
            )
        }
    }

    // metadata is informational only — no automatic check.
    return RequirementCheckResult.Passed
}

fun isModuleInstalled(moduleId: String): Boolean = try {
    SuFile.open("/data/adb/modules/$moduleId/module.prop").exists()
} catch (_: Throwable) {
    false
}

fun areDependenciesSatisfied(dependsOn: List<String>): Boolean =
    dependsOn.all { isModuleInstalled(it) }
