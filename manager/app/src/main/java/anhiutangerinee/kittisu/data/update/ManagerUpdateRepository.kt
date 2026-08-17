package anhiutangerinee.kittisu.data.update

import anhiutangerinee.kittisu.BuildConfig
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.activity.util.isNetworkAvailable
import anhiutangerinee.kittisu.ui.util.module.LatestVersionInfo
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

enum class UpdateChannel(val preferenceValue: String) {
    STABLE("stable"),
    BETA("beta"),
    NIGHTLY("nightly");

    companion object {
        fun fromPreference(value: String?) = entries.firstOrNull {
            it.preferenceValue == value
        } ?: STABLE
    }
}

private const val API_ROOT = "https://api.github.com/repos/terebiko/KittiSU"

fun checkManagerUpdate(channel: UpdateChannel): LatestVersionInfo {
    if (!isNetworkAvailable(ksuApp)) return LatestVersionInfo()
    return runCatching {
        when (channel) {
            UpdateChannel.STABLE -> requestJson("$API_ROOT/releases/latest")?.let {
                ManagerUpdateParser.release(it)
            }
            UpdateChannel.BETA -> requestArray("$API_ROOT/releases?per_page=20")?.let {
                ManagerUpdateParser.beta(it)
            }
            UpdateChannel.NIGHTLY -> requestJson(
                "$API_ROOT/actions/artifacts?name=Manager-release&per_page=20"
            )?.let {
                ManagerUpdateParser.nightly(it, BuildConfig.GIT_COMMIT)
            }
        } ?: LatestVersionInfo()
    }.getOrDefault(LatestVersionInfo())
}

private fun requestJson(url: String): JSONObject? = request(url)?.let(::JSONObject)
private fun requestArray(url: String): JSONArray? = request(url)?.let(::JSONArray)

private fun request(url: String): String? = ksuApp.okhttpClient.newCall(
    Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .build()
).execute().use { response ->
    if (response.isSuccessful) response.body?.string() else null
}

internal object ManagerUpdateParser {
    private val versionCodePattern = Regex("_(\\d+)(?:[-_]|\\.apk$)")

    fun beta(releases: JSONArray): LatestVersionInfo {
        var stableFallback: JSONObject? = null
        for (index in 0 until releases.length()) {
            val releaseJson = releases.optJSONObject(index) ?: continue
            if (releaseJson.optBoolean("draft")) continue
            if (releaseJson.optBoolean("prerelease")) return release(releaseJson)
            if (stableFallback == null) stableFallback = releaseJson
        }
        return stableFallback?.let(::release) ?: LatestVersionInfo()
    }

    fun release(release: JSONObject): LatestVersionInfo {
        val assets = release.optJSONArray("assets") ?: return LatestVersionInfo()
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true) ||
                name.contains("spoof", ignoreCase = true)
            ) continue
            val versionCode = versionCodePattern.find(name)?.groupValues?.get(1)?.toIntOrNull()
                ?: continue
            return LatestVersionInfo(
                versionCode = versionCode,
                downloadUrl = asset.optString("browser_download_url"),
                changelog = release.optString("body"),
                versionName = release.optString("tag_name"),
            )
        }
        return LatestVersionInfo()
    }

    fun nightly(response: JSONObject, currentCommit: String): LatestVersionInfo {
        val artifacts = response.optJSONArray("artifacts") ?: return LatestVersionInfo()
        for (index in 0 until artifacts.length()) {
            val artifact = artifacts.optJSONObject(index) ?: continue
            val run = artifact.optJSONObject("workflow_run") ?: continue
            val sha = run.optString("head_sha")
            if (artifact.optBoolean("expired") || run.optString("head_branch") != "dev" ||
                sha.isBlank()
            ) continue
            if (currentCommit.startsWith(sha) || sha.startsWith(currentCommit)) {
                return LatestVersionInfo()
            }
            val id = artifact.optLong("id")
            val runId = run.optLong("id")
            if (id <= 0 || runId <= 0) continue
            val shortSha = sha.take(7)
            return LatestVersionInfo(
                downloadUrl = "https://github.com/terebiko/KittiSU/actions/runs/$runId/artifacts/$id",
                changelog = "Development build from commit `$shortSha`.",
                versionName = "nightly-$shortSha",
                commitSha = sha,
            )
        }
        return LatestVersionInfo()
    }
}
