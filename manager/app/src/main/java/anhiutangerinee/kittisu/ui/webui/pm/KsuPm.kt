package anhiutangerinee.kittisu.ui.webui.pm

import android.content.pm.ApplicationInfo
import android.webkit.JavascriptInterface
import androidx.core.content.pm.PackageInfoCompat
import anhiutangerinee.kittisu.ui.viewmodel.SuperUserViewModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Package-manager bridge exposed to the WebUI as `ksu.pm()`.
 */
class KsuPm {
    companion object {
        private const val KSU_PM_VERSION = 1
    }

    @JavascriptInterface
    fun version(): Int = KSU_PM_VERSION

    @JavascriptInterface
    fun listPackages(type: String): String {
        val packageNames = SuperUserViewModel.apps
            .filter { appInfo ->
                val flags = appInfo.packageInfo.applicationInfo?.flags ?: 0
                val isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
                when (type.lowercase()) {
                    "system" -> isSystem
                    "user" -> !isSystem
                    else -> true
                }
            }
            .map { it.packageName }
            .distinct()
            .sorted()

        val jsonArray = JSONArray()
        for (pkgName in packageNames) {
            jsonArray.put(pkgName)
        }
        return jsonArray.toString()
    }

    @JavascriptInterface
    fun getPackagesInfo(packageNamesJson: String): String {
        val packageNames = JSONArray(packageNamesJson)
        val appsByPackage = SuperUserViewModel.apps.groupBy { it.packageName }
        val jsonArray = JSONArray()
        for (i in 0 until packageNames.length()) {
            val pkgName = packageNames.getString(i)
            val matches = appsByPackage[pkgName]
            if (matches.isNullOrEmpty()) {
                val error = JSONObject()
                error.put("packageName", pkgName)
                error.put("error", "Package not found or inaccessible")
                jsonArray.put(error)
                continue
            }
            for (pkg in matches.sortedBy { it.uid }) {
                jsonArray.put(buildInfo(pkg))
            }
        }
        return jsonArray.toString()
    }

    private fun buildInfo(appInfo: SuperUserViewModel.AppInfo): JSONObject = JSONObject().apply {
        val pkg = appInfo.packageInfo
        val app = pkg.applicationInfo
        val uid = app?.uid ?: -1
        put("packageName", pkg.packageName)
        put("versionName", pkg.versionName ?: "")
        put("versionCode", PackageInfoCompat.getLongVersionCode(pkg))
        put("appLabel", appInfo.label)
        put("isSystem", if (app != null) ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) else JSONObject.NULL)
        put("uid", if (uid >= 0) uid else JSONObject.NULL)
        put("userId", if (uid >= 0) uid / 100000 else JSONObject.NULL)
    }
}
