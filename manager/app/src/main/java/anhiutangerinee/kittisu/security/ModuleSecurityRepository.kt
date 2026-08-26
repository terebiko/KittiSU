package anhiutangerinee.kittisu.security

import android.util.Log
import anhiutangerinee.kittisu.ui.util.execKsud
import anhiutangerinee.kittisu.ui.util.listModules as listModulesJson
import anhiutangerinee.kittisu.ui.util.toggleModule
import anhiutangerinee.kittisu.ui.util.uninstallModule

data class ModuleState(
    val dirId: String,
    val enabled: Boolean,
    val remove: Boolean,
)

object ModuleSecurityRepository {

    private const val TAG = "ModuleSecurity"

    fun listModules(): List<ModuleState> = runCatching {
        val array = org.json.JSONArray(listModulesJson())
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            ModuleState(
                dirId = obj.optString("dir_id").trim(),
                enabled = obj.optBoolean("enabled"),
                remove = obj.optBoolean("remove"),
            )
        }.filter { it.dirId.isNotEmpty() }
    }.getOrElse {
        Log.e(TAG, "listModules failed", it)
        emptyList()
    }

    fun setEnabled(dirId: String, enable: Boolean): Boolean = toggleModule(dirId, enable)

    fun uninstall(dirId: String): Boolean = uninstallModule(dirId)
    fun saveFeatures(): Boolean = execKsud("feature save", true)
}
