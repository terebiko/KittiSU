package anhiutangerinee.kittisu.ui.util.module

import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.Immutable
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.activity.util.isNetworkAvailable
import kotlinx.parcelize.Parcelize
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

const val TAG = "ModuleRepoApi"

data class ModuleDetail(
    val readme: String,
    val latestTag: String,
    val latestTime: String,
    val latestAssetName: String?,
    val latestAssetUrl: String?,
    val releases: List<ReleaseInfo>,
    val homepageUrl: String?,
    val sourceUrl: String?,
    val url: String?
)

@Immutable
@Parcelize
data class ReleaseInfo(
    val name: String,
    val tagName: String,
    val publishedAt: String,
    val descriptionHTML: String,
    val assets: List<ReleaseAssetInfo>
) : Parcelable

@Immutable
@Parcelize
data class ReleaseAssetInfo(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val downloadCount: Int
) : Parcelable

fun fetchModuleDetail(moduleId: String): ModuleDetail? {
    if (!isNetworkAvailable(ksuApp)) return null
    val url = "https://modules.kernelsu.org/api/v1/modules/$moduleId.json"
    return runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "SovaSU-Manager")
            .build()
        ksuApp.okhttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            val obj = JSONObject(body)

            fun stripTicks(s: String): String {
                val t = s.trim()
                return if (t.startsWith("`") && t.endsWith("`") && t.length >= 2) t.substring(1, t.length - 1) else t
            }
            fun optCleanString(o: JSONObject, key: String): String {
                if (o.isNull(key)) return ""
                return stripTicks(o.optString(key, ""))
            }

            val homepageUrl = optCleanString(obj, "homepage")
                .ifBlank { optCleanString(obj, "homepageUrl") }
                .ifBlank { null }
            val sourceObj = obj.optJSONObject("source")
            val sourceUrl = (sourceObj?.let { optCleanString(it, "url") } ?: "")
                .ifBlank { optCleanString(obj, "sourceUrl") }
                .ifBlank { null }
            val urlRepo = optCleanString(obj, "url").ifBlank { sourceUrl }

            val releasesArray = obj.optJSONArray("releases")
            val releases = if (releasesArray != null) {
                (0 until releasesArray.length()).mapNotNull { rIdx ->
                    val r = releasesArray.optJSONObject(rIdx) ?: return@mapNotNull null
                    val rtag = optCleanString(r, "tag")
                        .ifBlank { optCleanString(r, "tagName") }
                        .ifBlank { optCleanString(r, "version") }
                    val rname = optCleanString(r, "name")
                        .ifBlank { rtag }
                    val publishedAt = optCleanString(r, "publishedAt")
                    val descHtml = optCleanString(r, "changelogHtml")
                        .ifBlank { optCleanString(r, "descriptionHTML") }

                    val assets = mutableListOf<ReleaseAssetInfo>()
                    val zipObj = r.optJSONObject("zip")
                    if (zipObj != null) {
                        val zUrl = optCleanString(zipObj, "url")
                        val zName = optCleanString(zipObj, "name").ifBlank { zUrl.substringAfterLast('/') }
                        val zSize = zipObj.optLong("size", 0L)
                        if (zName.isNotEmpty() && zUrl.isNotEmpty()) {
                            assets.add(ReleaseAssetInfo(zName, zUrl, zSize, 0))
                        }
                    }
                    val assetsArray = r.optJSONArray("releaseAssets")
                    if (assetsArray != null) {
                        for (aIdx in 0 until assetsArray.length()) {
                            val a = assetsArray.optJSONObject(aIdx) ?: continue
                            val aname = optCleanString(a, "name")
                            val adl = optCleanString(a, "downloadUrl")
                            val asz = a.optLong("size", 0L)
                            val dcnt = when (val dcAny = a.opt("downloadCount")) {
                                is Number -> dcAny.toInt()
                                is String -> dcAny.toIntOrNull() ?: 0
                                else -> 0
                            }
                            if (aname.isNotEmpty() && adl.isNotEmpty()) {
                                assets.add(ReleaseAssetInfo(aname, adl, asz, dcnt))
                            }
                        }
                    }

                    ReleaseInfo(
                        name = rname,
                        tagName = rtag.ifBlank { rname },
                        publishedAt = publishedAt,
                        descriptionHTML = descHtml,
                        assets = assets
                    )
                }
            } else emptyList()

            val firstRel = releases.firstOrNull()
            val firstAsset = firstRel?.assets?.firstOrNull()
            val stableObj = obj.optJSONObject("stable")
            val latestTag = firstRel?.tagName?.ifBlank { firstRel.name }
                ?: stableObj?.let { optCleanString(it, "version") }
                ?: ""
            val latestTime = firstRel?.publishedAt?.ifBlank { null }
                ?: stableObj?.let { optCleanString(it, "publishedAt") }
                ?: ""
            val readme = optCleanString(obj, "readmeHTML")
                .ifBlank { firstRel?.descriptionHTML.orEmpty() }
                .ifBlank { optCleanString(obj, "summary") }

            return@use ModuleDetail(
                readme = readme,
                latestTag = latestTag,
                latestTime = latestTime,
                latestAssetName = firstAsset?.name,
                latestAssetUrl = firstAsset?.downloadUrl,
                releases = releases,
                homepageUrl = homepageUrl,
                sourceUrl = sourceUrl,
                url = urlRepo
            )
        }
    }.getOrElse {
        Log.e(TAG, "Module Repo Api Calling FAILED for $moduleId", it)
        null
    }
}