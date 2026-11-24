package anhiutangerinee.kittisu.data.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import anhiutangerinee.kittisu.BuildConfig
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.activity.util.isNetworkAvailable
import anhiutangerinee.kittisu.ui.util.module.LatestVersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

private const val TAG = "ManagerUpdate"
private const val REPO_SLUG = "terebiko/KittiSU"
private const val API_ROOT = "https://api.github.com/repos/$REPO_SLUG"
private const val WORKFLOW_BRANCH = "SovaSU"
private const val WORKFLOW_FILE = "build-manager"

enum class UpdateChannel(val preferenceValue: String) {
    STABLE("stable"),
    BETA("beta"),
    NIGHTLY("nightly");

    companion object {
        fun fromPreference(value: String?) = entries.firstOrNull {
            it.preferenceValue == value
        } ?: NIGHTLY
    }
}

enum class UpdateDownloadStage {
    DOWNLOADING,
    EXTRACTING,
}

data class UpdateDownloadProgress(
    val stage: UpdateDownloadStage = UpdateDownloadStage.DOWNLOADING,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
) {
    val fraction: Float?
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else null
}

private fun isSpoofedAppBuild(): Boolean {
    val pkg = BuildConfig.APPLICATION_ID
    return pkg != "anhiutangerinee.kittisu" &&
        !pkg.startsWith("anhiutangerinee.kittisu.")
}

fun checkManagerUpdate(channel: UpdateChannel): LatestVersionInfo {
    if (!isNetworkAvailable(ksuApp)) return LatestVersionInfo()
    val spoofed = isSpoofedAppBuild()
    val artifactName = if (spoofed) "Spoofed-Manager-release" else "Manager-release"

    return runCatching {
        when (channel) {
            UpdateChannel.STABLE -> requestJson("$API_ROOT/releases/latest")?.let {
                ManagerUpdateParser.release(it, Build.SUPPORTED_ABIS.toList(), spoofed)
            }
            UpdateChannel.BETA -> requestArray("$API_ROOT/releases?per_page=20")?.let {
                ManagerUpdateParser.beta(it, Build.SUPPORTED_ABIS.toList(), spoofed)
            }
            UpdateChannel.NIGHTLY -> requestJson(
                "$API_ROOT/actions/artifacts?name=$artifactName&per_page=20"
            )?.let { artifactsJson ->
                val baseInfo = ManagerUpdateParser.nightly(
                    response = artifactsJson,
                    currentCommit = BuildConfig.GIT_COMMIT,
                    artifactName = artifactName,
                )
                if (baseInfo.commitSha.isNotBlank()) {
                    val commitMsg = fetchCommitMessage(baseInfo.commitSha)
                    if (!commitMsg.isNullOrBlank()) {
                        baseInfo.copy(
                            changelog = "Workflow $WORKFLOW_BRANCH • ${baseInfo.commitSha.take(7)}\n\n$commitMsg"
                        )
                    } else {
                        baseInfo
                    }
                } else {
                    baseInfo
                }
            }
        } ?: LatestVersionInfo()
    }.getOrDefault(LatestVersionInfo())
}

private fun fetchCommitMessage(sha: String): String? {
    return runCatching {
        requestJson("$API_ROOT/commits/$sha")
            ?.optJSONObject("commit")
            ?.optString("message")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun requestJson(url: String): JSONObject? = request(url)?.let(::JSONObject)
private fun requestArray(url: String): JSONArray? = request(url)?.let(::JSONArray)

private fun request(url: String): String? = ksuApp.okhttpClient.newCall(
    Request.Builder()
        .url(url)
        .header("User-Agent", "SovaSU-Manager")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .build()
).execute().use { response ->
    if (response.isSuccessful) response.body?.string() else null
}

/**
 * Downloads the update (either direct .apk or nightly.link workflow artifact .zip),
 * extracts the exact ABI-matched .apk when needed, and returns the ready-to-install APK file.
 */
suspend fun downloadAndPrepareManagerUpdate(
    context: Context,
    info: LatestVersionInfo,
    onProgress: (UpdateDownloadProgress) -> Unit,
): Result<File> = withContext(Dispatchers.IO) {
    val updateDir = File(context.cacheDir, "app_update").apply {
        deleteRecursively()
        mkdirs()
    }
    val urlsToTry = listOfNotNull(
        info.downloadUrl.takeIf { it.isNotBlank() },
        info.fallbackUrl.takeIf { it.isNotBlank() && it != info.downloadUrl },
    )
    if (urlsToTry.isEmpty()) {
        return@withContext Result.failure(IllegalArgumentException("Empty download URL"))
    }

    val rawTarget = File(updateDir, if (info.isZipArtifact) "artifact.zip" else "sovasu_update.apk")
    var lastError: Throwable? = null
    var downloadedOk = false

    for (url in urlsToTry) {
        coroutineContext.ensureActive()
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SovaSU-Manager")
                .build()
            ksuApp.okhttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code} from $url")
                }
                val body = resp.body ?: throw IllegalStateException("Empty response body")
                val total = body.contentLength()
                var downloaded = 0L
                onProgress(UpdateDownloadProgress(UpdateDownloadStage.DOWNLOADING, 0L, total))

                body.byteStream().use { input ->
                    FileOutputStream(rawTarget).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var lastEmit = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 120L || (total > 0 && downloaded >= total)) {
                                lastEmit = now
                                onProgress(
                                    UpdateDownloadProgress(
                                        stage = UpdateDownloadStage.DOWNLOADING,
                                        downloadedBytes = downloaded,
                                        totalBytes = total,
                                    )
                                )
                            }
                        }
                    }
                }
            }
            if (rawTarget.isFile && rawTarget.length() > 100_000L) {
                downloadedOk = true
                break
            } else {
                throw IllegalStateException("Downloaded file is unexpectedly small (${rawTarget.length()} bytes)")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Download failed from $url", t)
            lastError = t
            rawTarget.delete()
        }
    }

    if (!downloadedOk) {
        return@withContext Result.failure(lastError ?: IllegalStateException("Download failed"))
    }

    if (!info.isZipArtifact) {
        return@withContext Result.success(rawTarget)
    }

    // Extract the exact ABI-matched APK from the workflow artifact zip
    return@withContext try {
        onProgress(UpdateDownloadProgress(UpdateDownloadStage.EXTRACTING, 0L, -1L))
        val outApk = File(updateDir, "sovasu_update.apk")
        ZipFile(rawTarget).use { zip ->
            val apkEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                .toList()
            val bestName = ManagerUpdateParser.selectBestApkName(
                names = apkEntries.map { it.name },
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                preferSpoofed = isSpoofedAppBuild(),
            ) ?: throw IllegalStateException("No APK found inside workflow artifact zip")

            val entry = apkEntries.first { it.name == bestName }
            zip.getInputStream(entry).use { input ->
                FileOutputStream(outApk).use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                }
            }
        }
        rawTarget.delete()
        if (!outApk.isFile || outApk.length() < 100_000L) {
            throw IllegalStateException("Extracted APK is invalid")
        }
        Result.success(outApk)
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to extract APK from artifact zip", t)
        rawTarget.delete()
        Result.failure(t)
    }
}

fun installDownloadedApk(context: Context, apkFile: File): Result<Unit> {
    return runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

internal object ManagerUpdateParser {
    private val versionCodePattern = Regex("_(\\d+)(?:[-_]|\\.apk$)")

    fun selectBestApkName(
        names: List<String>,
        supportedAbis: List<String>,
        preferSpoofed: Boolean = false,
    ): String? {
        val candidates = names.filter { name ->
            name.endsWith(".apk", ignoreCase = true) &&
                !name.contains("debug", ignoreCase = true) &&
                (preferSpoofed == name.contains("spoof", ignoreCase = true))
        }.ifEmpty {
            names.filter { it.endsWith(".apk", ignoreCase = true) }
        }
        if (candidates.isEmpty()) return null

        // 1. Exact ABI match following device priority order
        for (abi in supportedAbis) {
            val match = candidates.firstOrNull { it.contains(abi, ignoreCase = true) }
            if (match != null) return match
        }
        // 2. Universal APK fallback
        candidates.firstOrNull { it.contains("universal", ignoreCase = true) }?.let { return it }
        // 3. First available APK
        return candidates.first()
    }

    fun beta(
        releases: JSONArray,
        supportedAbis: List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty(),
        preferSpoofed: Boolean = false,
    ): LatestVersionInfo {
        var stableFallback: JSONObject? = null
        for (index in 0 until releases.length()) {
            val releaseJson = releases.optJSONObject(index) ?: continue
            if (releaseJson.optBoolean("draft")) continue
            if (releaseJson.optBoolean("prerelease")) {
                return release(releaseJson, supportedAbis, preferSpoofed)
            }
            if (stableFallback == null) stableFallback = releaseJson
        }
        return stableFallback?.let { release(it, supportedAbis, preferSpoofed) } ?: LatestVersionInfo()
    }

    fun release(
        release: JSONObject,
        supportedAbis: List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty(),
        preferSpoofed: Boolean = false,
    ): LatestVersionInfo {
        val assets = release.optJSONArray("assets") ?: return LatestVersionInfo()
        val assetByName = LinkedHashMap<String, JSONObject>()
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                assetByName[name] = asset
            }
        }
        val bestName = selectBestApkName(assetByName.keys.toList(), supportedAbis, preferSpoofed)
            ?: return LatestVersionInfo()
        val asset = assetByName[bestName] ?: return LatestVersionInfo()
        val versionCode = versionCodePattern.find(bestName)?.groupValues?.get(1)?.toIntOrNull()
            ?: return LatestVersionInfo()
        return LatestVersionInfo(
            versionCode = versionCode,
            downloadUrl = asset.optString("browser_download_url"),
            changelog = release.optString("body"),
            versionName = release.optString("tag_name"),
            isZipArtifact = false,
        )
    }

    fun nightly(
        response: JSONObject,
        currentCommit: String,
        artifactName: String = "Manager-release",
        targetBranch: String = WORKFLOW_BRANCH,
    ): LatestVersionInfo {
        val artifacts = response.optJSONArray("artifacts") ?: return LatestVersionInfo()
        for (index in 0 until artifacts.length()) {
            val artifact = artifacts.optJSONObject(index) ?: continue
            val run = artifact.optJSONObject("workflow_run") ?: continue
            val sha = run.optString("head_sha")
            val branch = run.optString("head_branch")
            if (artifact.optBoolean("expired") ||
                !branch.equals(targetBranch, ignoreCase = true) ||
                sha.isBlank()
            ) continue
            if (currentCommit.isNotBlank() &&
                (currentCommit.startsWith(sha) || sha.startsWith(currentCommit))
            ) {
                return LatestVersionInfo()
            }
            val runId = run.optLong("id")
            if (runId <= 0) continue
            val shortSha = sha.take(7)
            return LatestVersionInfo(
                downloadUrl = "https://nightly.link/$REPO_SLUG/actions/runs/$runId/$artifactName.zip",
                fallbackUrl = "https://nightly.link/$REPO_SLUG/workflows/$WORKFLOW_FILE/$targetBranch/$artifactName.zip",
                changelog = "Workflow $targetBranch • $shortSha",
                versionName = "$targetBranch-$shortSha",
                commitSha = sha,
                isZipArtifact = true,
            )
        }
        return LatestVersionInfo()
    }
}
