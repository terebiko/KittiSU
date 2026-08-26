package anhiutangerinee.kittisu.security

import org.json.JSONArray
import org.json.JSONObject

enum class OperationState {
    LOCKDOWN_ENTERING,
    LOCKDOWN_ACTIVE,
    LOCKDOWN_EXITING,
    RESETTING,
    REBOOT_REQUIRED,
}

data class GrantedAppSnapshot(
    val packageName: String,
    val uid: Int,
)

data class OperationDocument(
    val state: OperationState,
    val apps: List<GrantedAppSnapshot> = emptyList(),
    val enabledModules: List<String> = emptyList(),
    val failedIds: List<String> = emptyList(),
    val phase: String = "",
    val bootId: String = "",
)

object OperationCodec {
    private const val VERSION = 1

    fun encode(document: OperationDocument): String {
        val apps = JSONArray()
        document.apps.forEach { app ->
            apps.put(JSONObject().put("packageName", app.packageName).put("uid", app.uid))
        }
        return JSONObject()
            .put("version", VERSION)
            .put("state", document.state.name)
            .put("apps", apps)
            .put("enabledModules", JSONArray(document.enabledModules))
            .put("failedIds", JSONArray(document.failedIds))
            .put("phase", document.phase)
            .put("bootId", document.bootId)
            .toString()
    }

    fun decode(json: String): OperationDocument {
        val obj = JSONObject(json)
        require(obj.getInt("version") == VERSION) { "Unsupported operation version" }
        val state = runCatching { OperationState.valueOf(obj.getString("state")) }
            .getOrElse { throw IllegalArgumentException("Unsupported operation state") }
        val apps = mutableListOf<GrantedAppSnapshot>()
        val appsArray = obj.optJSONArray("apps") ?: JSONArray()
        for (i in 0 until appsArray.length()) {
            val app = appsArray.getJSONObject(i)
            apps += GrantedAppSnapshot(app.getString("packageName"), app.getInt("uid"))
        }
        return OperationDocument(
            state = state,
            apps = apps,
            enabledModules = stringList(obj, "enabledModules"),
            failedIds = stringList(obj, "failedIds"),
            phase = obj.optString("phase"),
            bootId = obj.optString("bootId"),
        )
    }

    private fun stringList(obj: JSONObject, key: String): List<String> {
        val array = obj.optJSONArray(key) ?: JSONArray()
        return List(array.length()) { array.getString(it) }
    }
}
