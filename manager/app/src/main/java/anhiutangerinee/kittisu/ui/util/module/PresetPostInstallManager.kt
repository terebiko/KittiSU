package anhiutangerinee.kittisu.ui.util.module

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import anhiutangerinee.kittisu.ksuApp
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages pending post-install bash scripts for a preset.
 * Scripts are stored as a JSON file on disk; execution state lives in SharedPreferences.
 */
object PresetPostInstallManager {

    private const val TAG = "PresetPostInstallManager"
    private const val PREFS_NAME = "prefs_post_install"
    private const val KEY_EXECUTED = "executed"

    private const val SCRIPTS_DIR = "post_install"
    private const val PENDING_SCRIPTS_FILE = "pending.json"

    data class PendingScripts(
        val presetId: String,
        val presetDestination: String,
        val scripts: List<PostInstallScript>
    )

    fun hasPendingScript(context: Context = ksuApp): Boolean {
        if (isExecuted(context)) return false
        return getPendingFile(context).exists()
    }

    fun loadPendingScripts(context: Context = ksuApp): PendingScripts? {
        if (isExecuted(context)) return null
        val file = getPendingFile(context)
        if (!file.exists()) return null
        val json = runCatching { file.readText() }.getOrElse {
            Log.e(TAG, "failed to read pending scripts", it)
            return null
        }
        return parsePendingScripts(json)
    }

    fun savePendingScripts(
        context: Context = ksuApp,
        presetId: String,
        presetDestination: String,
        baseUrl: String,
        scripts: List<PostInstallScript>
    ) {
        if (scripts.isEmpty()) {
            clearPendingScripts(context)
            return
        }
        val resolved = scripts.map { s ->
            val url = if (s.path.startsWith("http://", ignoreCase = true) || s.path.startsWith("https://", ignoreCase = true)) {
                s.path
            } else {
                val base = baseUrl.removeSuffix("/")
                val relative = s.path.removePrefix("/")
                "$base/$relative"
            }
            s.copy(path = url)
        }
        val dir = File(context.filesDir, SCRIPTS_DIR).apply { mkdirs() }
        val file = File(dir, PENDING_SCRIPTS_FILE)
        val json = JSONObject().apply {
            put("presetId", presetId)
            put("presetDestination", presetDestination)
            put("scripts", JSONArray().apply {
                for (s in resolved) {
                    put(JSONObject().apply {
                        put("name", s.name)
                        put("path", s.path)
                    })
                }
            })
        }
        runCatching {
            file.writeText(json.toString())
        }.onFailure {
            Log.e(TAG, "failed to write pending scripts", it)
            return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_EXECUTED, false)
        }
    }

    fun clearPendingScripts(context: Context = ksuApp) {
        runCatching { getPendingFile(context).delete() }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_EXECUTED, true)
        }
    }

    fun markExecuted(context: Context = ksuApp) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_EXECUTED, true)
        }
    }

    private fun isExecuted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_EXECUTED, true)
    }

    private fun getPendingFile(context: Context): File {
        return File(File(context.filesDir, SCRIPTS_DIR), PENDING_SCRIPTS_FILE)
    }

    private fun parsePendingScripts(json: String): PendingScripts? {
        return runCatching {
            val obj = JSONObject(json)
            val scriptsArr = obj.optJSONArray("scripts") ?: return@runCatching null
            val scripts = (0 until scriptsArr.length()).mapNotNull { i ->
                val so = scriptsArr.optJSONObject(i) ?: return@mapNotNull null
                val name = so.optString("name", "").ifBlank { return@mapNotNull null }
                val path = so.optString("path", "").ifBlank { return@mapNotNull null }
                PostInstallScript(name = name, path = path)
            }
            if (scripts.isEmpty()) return@runCatching null
            PendingScripts(
                presetId = obj.optString("presetId", ""),
                presetDestination = obj.optString("presetDestination", ""),
                scripts = scripts
            )
        }.getOrElse {
            Log.e(TAG, "failed to parse pending scripts", it)
            null
        }
    }

    /**
     * Writes the script to a temporary executable file and runs it through a root shell.
     * Returns the shell result code.
     */
    fun runScript(
        script: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit
    ): Shell.Result {
        val dir = File(ksuApp.filesDir, SCRIPTS_DIR).apply { mkdirs() }
        val scriptFile = File(dir, "run_${System.currentTimeMillis()}.sh")
        return try {
            scriptFile.writeText(script)
            scriptFile.setExecutable(true, false)
        } catch (e: Exception) {
            Log.e(TAG, "failed to write script file", e)
            onStderr("Failed to write script file: ${e.message}\n")
            return object : Shell.Result {
                override fun getCode() = 1
                override fun getOut() = emptyList<String>()
                override fun getErr() = listOf(e.message ?: "write failed")
            }
        }

        val mainHandler = Handler(Looper.getMainLooper())
        val stdoutCallback = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                mainHandler.post { onStdout(s ?: "") }
            }
        }
        val stderrCallback = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                mainHandler.post { onStderr(s ?: "") }
            }
        }

        return try {
            val shell = anhiutangerinee.kittisu.ui.util.getRootShell()
            shell.newJob().add("/system/bin/sh", scriptFile.absolutePath)
                .to(stdoutCallback, stderrCallback).exec()
        } catch (e: Exception) {
            Log.e(TAG, "failed to execute script", e)
            onStderr("Failed to execute script: ${e.message}\n")
            object : Shell.Result {
                override fun getCode() = 1
                override fun getOut() = emptyList<String>()
                override fun getErr() = listOf(e.message ?: "exec failed")
            }
        } finally {
            runCatching { scriptFile.delete() }
        }
    }
}
