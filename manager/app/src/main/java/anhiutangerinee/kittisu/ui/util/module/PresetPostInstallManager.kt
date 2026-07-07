package anhiutangerinee.kittisu.ui.util.module

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import anhiutangerinee.kittisu.ksuApp
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages a single pending post-install bash script for a preset.
 * The script is written to disk; metadata lives in a small SharedPreferences file.
 */
object PresetPostInstallManager {

    private const val TAG = "PresetPostInstallManager"
    private const val PREFS_NAME = "prefs_post_install"
    private const val KEY_SCRIPT_NAME = "script_name"
    private const val KEY_PRESET_ID = "preset_id"
    private const val KEY_PRESET_DESTINATION = "preset_destination"
    private const val KEY_SAVED_AT = "saved_at"
    private const val KEY_EXECUTED = "executed"

    private const val SCRIPTS_DIR = "post_install"
    private const val PENDING_SCRIPT_FILE = "pending.sh"

    data class PendingScript(
        val name: String,
        val script: String,
        val presetId: String,
        val presetDestination: String,
        val savedAt: Long,
        val executed: Boolean
    )

    fun hasPendingScript(context: Context = ksuApp): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_EXECUTED, true)) return false
        return getScriptFile(context).exists()
    }

    fun loadPendingScript(context: Context = ksuApp): PendingScript? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_EXECUTED, true)) return null
        val file = getScriptFile(context)
        if (!file.exists()) return null
        val script = runCatching { file.readText() }.getOrElse {
            Log.e(TAG, "failed to read pending script", it)
            return null
        }
        if (script.isBlank()) return null
        return PendingScript(
            name = prefs.getString(KEY_SCRIPT_NAME, null).orEmpty(),
            script = script,
            presetId = prefs.getString(KEY_PRESET_ID, null).orEmpty(),
            presetDestination = prefs.getString(KEY_PRESET_DESTINATION, null).orEmpty(),
            savedAt = prefs.getLong(KEY_SAVED_AT, 0L),
            executed = false
        )
    }

    fun savePendingScript(
        context: Context = ksuApp,
        name: String,
        script: String,
        presetId: String,
        presetDestination: String
    ) {
        val dir = File(context.filesDir, SCRIPTS_DIR).apply { mkdirs() }
        val file = File(dir, PENDING_SCRIPT_FILE)
        runCatching {
            file.writeText(script)
        }.onFailure {
            Log.e(TAG, "failed to write pending script", it)
            return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_SCRIPT_NAME, name)
            putString(KEY_PRESET_ID, presetId)
            putString(KEY_PRESET_DESTINATION, presetDestination)
            putLong(KEY_SAVED_AT, System.currentTimeMillis())
            putBoolean(KEY_EXECUTED, false)
        }
    }

    fun clearPendingScript(context: Context = ksuApp) {
        runCatching { getScriptFile(context).delete() }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_EXECUTED, true)
        }
    }

    fun markExecuted(context: Context = ksuApp) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_EXECUTED, true)
        }
    }

    private fun getScriptFile(context: Context): File {
        return File(File(context.filesDir, SCRIPTS_DIR), PENDING_SCRIPT_FILE)
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
        scriptFile.writeText(script)
        scriptFile.setExecutable(true, false)

        val stdoutCallback = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                onStdout(s ?: "")
            }
        }
        val stderrCallback = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                onStderr(s ?: "")
            }
        }

        return try {
            val shell = anhiutangerinee.kittisu.ui.util.getRootShell()
            shell.newJob().add("sh ${scriptFile.absolutePath}")
                .to(stdoutCallback, stderrCallback).exec()
        } finally {
            runCatching { scriptFile.delete() }
        }
    }
}
