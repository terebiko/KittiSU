package anhiutangerinee.kittisu.ui.util

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Environment
import android.os.Parcelable
import android.os.SystemClock
import android.system.Os
import android.util.Log
import anhiutangerinee.kittisu.BuildConfig
import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.ksuApp
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Properties

/**
 * @author weishu
 * @date 2023/1/1.
 */
private const val TAG = "KsuCli"

private fun getKsuDaemonPath(): String {
    return ksuApp.applicationInfo.nativeLibraryDir + File.separator + "libksud.so"
}

internal fun hasKsuDaemon(): Boolean {
    return File(getKsuDaemonPath()).isFile
}

/**
 * True when the kernel driver recognizes this process as an authorized KernelSU manager.
 */
internal val hasKsuManager: Boolean by lazy {
    runCatching {
        Natives.isManager && Natives.version > 0
    }.getOrDefault(false)
}

@SuppressLint("RestrictedApi")
object KsuCli {
    // Create shells lazily: the old eager init spawned two root shells as soon as
    // this object was touched, which could happen on the main thread at startup.
    val SHELL: Shell by lazy { createRootShell() }
    val GLOBAL_MNT_SHELL: Shell by lazy { createRootShell(true) }
}

fun getRootShell(globalMnt: Boolean = false): Shell {
    return if (globalMnt) KsuCli.GLOBAL_MNT_SHELL else {
        KsuCli.SHELL
    }
}

fun generateMainShellBuilder(): Shell.Builder {
    // Called from Application.onCreate on the main thread.
    // Only bind libksud.so as the MainShell command when the kernel driver actually
    // recognizes this manager; otherwise let libsu fall back cleanly to "sh" so
    // MainShell.isInitMain never gets poisoned on non-rooted devices.
    val builder = Shell.Builder.create()
    if (hasKsuManager && hasKsuDaemon()) {
        builder.setCommands(getKsuDaemonPath(), "debug", "su")
    }
    return builder
}

inline fun <T> withNewRootShell(
    globalMnt: Boolean = false,
    block: Shell.() -> T
): T {
    return createRootShell(globalMnt).use(block)
}

fun createRootShell(globalMnt: Boolean = false): Shell {
    // Verbose libsu logging is extremely chatty and adds measurable overhead to
    // every shell command. Keep it opt-in instead of enabling it for all debug builds.
    Shell.enableVerboseLogging = false
    val builder = Shell.Builder.create()
    return try {
        if (globalMnt) {
            builder.build(getKsuDaemonPath(), "debug", "su", "-g")
        } else {
            builder.build(getKsuDaemonPath(), "debug", "su")
        }
    } catch (e: Throwable) {
        Log.w(TAG, "ksu failed: ", e)
        try {
            if (globalMnt) {
                builder.build("su", "-mm")
            } else {
                builder.build("su")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "su failed: ", e)
            builder.build("sh")
        }
    }
}

fun execKsud(args: String, newShell: Boolean = false): Boolean {
    if (!hasKsuDaemon()) return false
    return if (newShell) {
        withNewRootShell {
            ShellUtils.fastCmdResult(this, "${getKsuDaemonPath()} $args")
        }
    } else {
        ShellUtils.fastCmdResult(getRootShell(), "${getKsuDaemonPath()} $args")
    }
}

fun backupModules(path: String): Boolean {
    if (!hasKsuDaemon()) return false
    val quoted = path.shellQuote()
    return execKsud("module backup $quoted", true) &&
        ShellUtils.fastCmdResult(getRootShell(), "chmod 0644 $quoted")
}

fun inspectModuleBackup(path: String): List<String>? {
    if (!hasKsuDaemon()) return null
    val output = mutableListOf<String>()
    val result = getRootShell().newJob()
        .add("${getKsuDaemonPath()} module inspect-backup ${path.shellQuote()}")
        .to(output, null)
        .exec()
    if (!result.isSuccess) return null
    return runCatching {
        val json = JSONArray(output.joinToString("\n"))
        List(json.length()) { json.getString(it) }
    }.getOrNull()
}

fun restoreModules(path: String, moduleIds: Collection<String> = emptyList()): Boolean {
    if (!hasKsuDaemon()) return false
    val ids = moduleIds.joinToString(" ") { it.shellQuote() }
    return execKsud("module restore ${path.shellQuote()} $ids", true)
}

data class BootRecoveryState(val failures: Int, val modules: List<String>)

fun getBootRecoveryState(): BootRecoveryState? {
    if (!hasKsuDaemon() || !hasKsuManager) return null
    val output = mutableListOf<String>()
    val result = runCatching {
        getRootShell().newJob()
            .add("${getKsuDaemonPath()} recovery")
            .to(output, null)
            .exec()
    }.getOrNull() ?: return null
    if (!result.isSuccess) return null
    return runCatching {
        val json = JSONObject(output.joinToString("\n"))
        val modules = json.getJSONArray("modules")
        BootRecoveryState(
            json.getInt("failures"),
            List(modules.length()) { modules.getString(it) },
        )
    }.getOrNull()
}

fun resetBootRecovery(): Boolean = execKsud("recovery --reset", true)

fun setDynamicManagerApk(apkPath: String): Boolean =
    execKsud("kernel dynamic-manager set-apk ${apkPath.shellQuote()}", true)

fun clearDynamicManager(): Boolean =
    execKsud("kernel dynamic-manager clear", true)

suspend fun getDynamicManagerConfig(): Natives.DynamicManagerConfig? = withContext(Dispatchers.IO) {
    if (!hasKsuDaemon() || !hasKsuManager) return@withContext null
    val result = runCatching {
        getRootShell().newJob()
            .add("${getKsuDaemonPath()} kernel dynamic-manager get")
            .to(ArrayList<String>(), null).exec()
    }.getOrNull() ?: return@withContext null
    if (!result.isSuccess) return@withContext null
    parseDynamicManagerConfig(result.out.joinToString("\n"))
}

internal fun parseDynamicManagerConfig(output: String): Natives.DynamicManagerConfig? {
    val match = Regex("""size:\s*(\d+),\s*hash:\s*([0-9a-fA-F]{64})""")
        .matchEntire(output.trim())
        ?: return null
    return Natives.DynamicManagerConfig(
        match.groupValues[1].toIntOrNull() ?: return null,
        match.groupValues[2],
    )
}

suspend fun getFeatureStatus(feature: String): String = withContext(Dispatchers.IO) {
    if (!hasKsuDaemon() || !hasKsuManager) return@withContext "unsupported"
    runCatching {
        val shell = getRootShell()
        val out = shell.newJob()
            .add("${getKsuDaemonPath()} feature check $feature").to(ArrayList<String>(), null).exec().out
        out.firstOrNull()?.trim().orEmpty()
    }.getOrDefault("unsupported")
}

suspend fun getFeaturePersistValue(feature: String): Long? = withContext(Dispatchers.IO) {
    if (!hasKsuDaemon() || !hasKsuManager) return@withContext null
    runCatching {
        val shell = getRootShell()
        val out = shell.newJob()
            .add("${getKsuDaemonPath()} feature get --config $feature").to(ArrayList<String>(), null)
            .exec().out
        val valueLine = out.firstOrNull { it.trim().startsWith("Value:") } ?: return@withContext null
        valueLine.substringAfter("Value:").trim().toLongOrNull()
    }.getOrNull()
}

data class SettingsFeatureBatch(
    val suStatus: String = "unsupported",
    val suPersistValue: Long? = null,
    val umountStatus: String = "unsupported",
    val adbRootStatus: String = "unsupported",
    val adbRootEnabled: Boolean = false,
    val shellSuAdbOnlyStatus: String = "unsupported",
    val shellSuAdbOnlyEnabled: Boolean = false,
    val sulogStatus: String = "unsupported",
    val selinuxHideStatus: String = "unsupported",
    val bootRecoveryState: BootRecoveryState? = null,
    val isToolkitInstalled: Boolean = false,
    val isKpatchNextInstalled: Boolean = false,
    val isRootAvailable: Boolean = false,
)

/**
 * Loads all Settings screen kernel/daemon states in a single background IO pass,
 * avoiding 12+ separate ksud subprocesses and preventing any Main-Thread shell calls.
 */
suspend fun loadSettingsFeatureBatch(): SettingsFeatureBatch = withContext(Dispatchers.IO) {
    if (!hasKsuDaemon() || !hasKsuManager) {
        return@withContext SettingsFeatureBatch(isRootAvailable = rootAvailable())
    }
    runCatching {
        val shell = getRootShell()
        val ksud = getKsuDaemonPath()
        val script = """
            echo "@@SU_STATUS@@"; $ksud feature check su_compat 2>/dev/null
            echo "@@SU_PERSIST@@"; $ksud feature get --config su_compat 2>/dev/null
            echo "@@UMOUNT_STATUS@@"; $ksud feature check kernel_umount 2>/dev/null
            echo "@@ADB_STATUS@@"; $ksud feature check adb_root 2>/dev/null
            echo "@@ADB_PERSIST@@"; $ksud feature get --config adb_root 2>/dev/null
            echo "@@SHELL_SU_STATUS@@"; $ksud feature check shell_su_adb_only 2>/dev/null
            echo "@@SHELL_SU_PERSIST@@"; $ksud feature get --config shell_su_adb_only 2>/dev/null
            echo "@@SULOG_STATUS@@"; $ksud feature check sulog 2>/dev/null
            echo "@@SELINUX_STATUS@@"; $ksud feature check selinux_hide 2>/dev/null
            echo "@@RECOVERY@@"; $ksud recovery 2>/dev/null
            echo "@@TOOLKIT@@"; [ -f /data/adb/modules/ksu_toolkit/webroot/index.html ] && echo 1 || echo 0
            echo "@@KPATCH@@"; [ -f /data/adb/modules/KPatch-Next/webroot/index.html ] && echo 1 || echo 0
        """.trimIndent()
        val out = ArrayList<String>()
        shell.newJob().add(script).to(out, null).exec()

        val sections = HashMap<String, MutableList<String>>()
        var currentKey: String? = null
        for (line in out) {
            val trimmed = line.trim()
            if (trimmed.startsWith("@@") && trimmed.endsWith("@@")) {
                currentKey = trimmed
                sections[trimmed] = mutableListOf()
            } else if (currentKey != null) {
                sections[currentKey]?.add(trimmed)
            }
        }

        fun firstLine(key: String): String =
            sections[key]?.firstOrNull { it.isNotEmpty() }.orEmpty()

        fun persistLong(key: String): Long? =
            sections[key]?.firstOrNull { it.startsWith("Value:") }
                ?.substringAfter("Value:")?.trim()?.toLongOrNull()

        val recoveryJson = sections["@@RECOVERY@@"]?.joinToString("\n").orEmpty()
        val recoveryState = if (recoveryJson.isNotBlank()) {
            runCatching {
                val json = JSONObject(recoveryJson)
                val modules = json.getJSONArray("modules")
                BootRecoveryState(
                    json.getInt("failures"),
                    List(modules.length()) { modules.getString(it) },
                )
            }.getOrNull()
        } else null

        SettingsFeatureBatch(
            suStatus = firstLine("@@SU_STATUS@@"),
            suPersistValue = persistLong("@@SU_PERSIST@@"),
            umountStatus = firstLine("@@UMOUNT_STATUS@@"),
            adbRootStatus = firstLine("@@ADB_STATUS@@"),
            adbRootEnabled = persistLong("@@ADB_PERSIST@@") == 1L,
            shellSuAdbOnlyStatus = firstLine("@@SHELL_SU_STATUS@@"),
            shellSuAdbOnlyEnabled = persistLong("@@SHELL_SU_PERSIST@@") == 1L,
            sulogStatus = firstLine("@@SULOG_STATUS@@"),
            selinuxHideStatus = firstLine("@@SELINUX_STATUS@@"),
            bootRecoveryState = recoveryState,
            isToolkitInstalled = firstLine("@@TOOLKIT@@") == "1",
            isKpatchNextInstalled = firstLine("@@KPATCH@@") == "1",
            isRootAvailable = shell.isRoot,
        )
    }.getOrElse {
        Log.w(TAG, "loadSettingsFeatureBatch failed", it)
        SettingsFeatureBatch(isRootAvailable = false)
    }
}

fun install() {
    if (!File(getKsuDaemonPath()).isFile) {
        Log.w(TAG, "skip install: libksud.so is missing")
        return
    }
    val start = SystemClock.elapsedRealtime()
    val libadbroot = File(ksuApp.applicationInfo.nativeLibraryDir, "libadbroot.so").absolutePath
    val dataPath = ksuApp.applicationInfo.deviceProtectedDataDir
    val result = execKsud(
        "install --libadbroot ${libadbroot.shellQuote()} --data-path ${dataPath.shellQuote()}",
        true
    )
    Log.w(TAG, "install result: $result, cost: ${SystemClock.elapsedRealtime() - start}ms")
}

fun hasMetaModule(): Boolean {
    return getMetaModuleImplement() != "None"
}

fun listModules(): String {
    if (!hasKsuDaemon() || !hasKsuManager) return "[]"
    return runCatching {
        val shell = getRootShell()
        val out = shell.newJob()
            .add("${getKsuDaemonPath()} module list").to(ArrayList(), null).exec().out
        out.joinToString("\n").ifBlank { "[]" }
    }.getOrDefault("[]")
}

fun getModuleCount(): Int {
    val result = listModules()
    runCatching {
        val array = JSONArray(result)
        return array.length()
    }.getOrElse { return 0 }
}

fun getSuperuserCount(): Int {
    return Natives.getSuperuserCount()
}

fun toggleModule(id: String, enable: Boolean): Boolean {
    val cmd = if (enable) {
        "module enable $id"
    } else {
        "module disable $id"
    }
    val result = execKsud(cmd, true)
    Log.i(TAG, "$cmd result: $result")
    return result
}

fun uninstallModule(id: String): Boolean {
    val cmd = "module uninstall $id"
    val result = execKsud(cmd, true)
    Log.i(TAG, "uninstall module $id result: $result")
    return result
}

fun undoUninstallModule(id: String): Boolean {
    val cmd = "module undo-uninstall $id"
    val result = execKsud(cmd, true)
    Log.i(TAG, "undo uninstall module $id result: $result")
    return result
}

private fun flashWithIO(
    cmd: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): Shell.Result {

    val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStdout(s ?: "")
        }
    }

    val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStderr(s ?: "")
        }
    }

    return withNewRootShell {
        newJob().add(cmd).to(stdoutCallback, stderrCallback).exec()
    }
}

fun flashAnyKernel(
    zipFile: File,
    slot: String?,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): Boolean {
    val command = buildString {
        append(getKsuDaemonPath()).append(" anykernel3 ").append(zipFile.absolutePath.shellQuote())
        slot?.let { append(" --slot ").append(it.shellQuote()) }
    }
    val result = flashWithIO(command, onStdout, onStderr)
    Log.i(TAG, "AnyKernel3 flash result: ${result.isSuccess}, code: ${result.code}")
    return result.isSuccess
}

fun flashModule(
    uri: Uri,
    onFinish: (Boolean, Int) -> Unit,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): Boolean {
    val resolver = ksuApp.contentResolver
    with(resolver.openInputStream(uri)) {
        val file = File(ksuApp.cacheDir, "module.zip")
        file.outputStream().use { output ->
            this?.copyTo(output)
        }
        try {
            val cmd = "module install ${file.absolutePath}"
            val result = flashWithIO("${getKsuDaemonPath()} $cmd", onStdout, onStderr)
            Log.i("KernelSU", "install module $uri result: $result")

            onFinish(result.isSuccess && moduleNeedsReboot(file), result.code)
            return result.isSuccess
        } finally {
            file.delete()
        }
    }
}

private val MODULE_ID_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9._-]+$")

private fun moduleNeedsReboot(zip: File): Boolean {
    val id = runCatching {
        java.util.zip.ZipFile(zip).use { archive ->
            val entry = archive.getEntry("module.prop") ?: return@use null
            archive.getInputStream(entry).bufferedReader().useLines { lines ->
                lines.map { it.trim() }.firstOrNull { it.startsWith("id=") }
                    ?.substringAfter('=')?.trim()
            }
        }
    }.getOrNull()
    if (id == null || !MODULE_ID_REGEX.matches(id)) return true
    val dir = "/data/adb/modules/$id".shellQuote()
    return runCatching {
        !ShellUtils.fastCmdResult(getRootShell(), "[ -d $dir ] && [ ! -e $dir/update ]")
    }.getOrDefault(true)
}

fun runModuleAction(
    moduleId: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStdout(s ?: "")
        }
    }

    val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStderr(s ?: "")
        }
    }

    val result = withNewRootShell(true) {
        newJob().add("${getKsuDaemonPath()} module action ${moduleId.shellQuote()}")
            .to(stdoutCallback, stderrCallback).exec()
    }

    Log.i("KernelSU", "Module runAction result: $result")

    return result.isSuccess
}

fun restoreBoot(
    onFinish: (Boolean, Int) -> Unit, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val magiskboot = File(ksuApp.applicationInfo.nativeLibraryDir, "libmagiskboot.so")
    val result = flashWithIO(
        "${getKsuDaemonPath()} boot-restore -f",
        onStdout,
        onStderr
    )
    onFinish(result.isSuccess, result.code)
    return result.isSuccess
}

fun uninstallPermanently(
    onFinish: (Boolean, Int) -> Unit, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val result =
        flashWithIO("${getKsuDaemonPath()} uninstall --package-name ${BuildConfig.APPLICATION_ID}", onStdout, onStderr)
    onFinish(result.isSuccess, result.code)
    return result.isSuccess
}

@Parcelize
sealed class LkmSelection : Parcelable {
    data class LkmUri(val uri: Uri) : LkmSelection()
    data class KmiString(val value: String) : LkmSelection()
    data object KmiNone : LkmSelection()
}

fun installBoot(
    bootUri: Uri?,
    lkm: LkmSelection,
    ota: Boolean,
    partition: String?,
    backup: Boolean,
    onFinish: (Boolean, Int) -> Unit,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): Boolean {
    val resolver = ksuApp.contentResolver

    val bootFile = bootUri?.let { uri ->
        with(resolver.openInputStream(uri)) {
            val bootFile = File(ksuApp.cacheDir, "boot.img")
            bootFile.outputStream().use { output ->
                this?.copyTo(output)
            }

            bootFile
        }
    }

    var cmd = "boot-patch"

    cmd += if (bootFile == null) {
        // no boot.img, use -f to force install
        " -f"
    } else {
        " -b ${bootFile.absolutePath}"
    }

    if (ota) {
        cmd += " -u"
    }

    if (backup) {
        val backupDir = File(
            ksuApp.applicationInfo.deviceProtectedDataDir,
            "boot_backup"
        ).absolutePath
        cmd += " --backup --backup-dir ${backupDir.shellQuote()}"
    }

    var lkmFile: File? = null
    when (lkm) {
        is LkmSelection.LkmUri -> {
            lkmFile = with(resolver.openInputStream(lkm.uri)) {
                val file = File(ksuApp.cacheDir, "kernelsu-tmp-lkm.ko")
                file.outputStream().use { output ->
                    this?.copyTo(output)
                }

                file
            }
            cmd += " -m ${lkmFile.absolutePath}"
        }

        is LkmSelection.KmiString -> {
            cmd += " --kmi ${lkm.value}"
        }

        LkmSelection.KmiNone -> {
            // do nothing
        }
    }

    // output dir
    val downloadsDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    cmd += " -o $downloadsDir"

    partition?.let { part ->
        cmd += " --partition $part"
    }

    val result = flashWithIO("${getKsuDaemonPath()} $cmd", onStdout, onStderr)
    Log.i("KernelSU", "install boot result: ${result.isSuccess}")

    bootFile?.delete()
    lkmFile?.delete()

    // if boot uri is empty, it is direct install, when success, we should show reboot button
    onFinish(bootUri == null && result.isSuccess, result.code)

    if (bootUri == null && result.isSuccess) {
        install()
    }

    return result.isSuccess
}

fun reboot(reason: String = "") {
    if (reason == "soft_reboot") {
        execKsud("soft-reboot", true)
        return
    }
    val shell = getRootShell()
    if (reason == "recovery") {
        // KEYCODE_POWER = 26, hide incorrect "Factory data reset" message
        ShellUtils.fastCmd(shell, "/system/bin/input keyevent 26")
    }
    ShellUtils.fastCmd(shell, "/system/bin/svc power reboot $reason || /system/bin/reboot $reason")
}

fun rootAvailable(): Boolean {
    return runCatching {
        val shell = getRootShell()
        shell.isRoot
    }.getOrDefault(false)
}


suspend fun getCurrentKmi(): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info current-kmi"
    ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd")
}

suspend fun getSupportedKmis(): List<String> = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info supported-kmis"
    val out = shell.newJob().add("${getKsuDaemonPath()} $cmd").to(ArrayList(), null).exec().out
    out.filter { it.isNotBlank() }.map { it.trim() }
}

suspend fun isAbDevice(): Boolean = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info is-ab-device"
    ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim().toBoolean()
}

suspend fun getDefaultPartition(): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    if (shell.isRoot) {
        val cmd = "boot-info default-partition"
        ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim()
    } else {
        if (!Os.uname().release.contains("android12-")) "init_boot" else "boot"
    }
}

suspend fun getSlotSuffix(ota: Boolean): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = if (ota) {
        "boot-info slot-suffix --ota"
    } else {
        "boot-info slot-suffix"
    }
    ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim()
}

suspend fun getAvailablePartitions(): List<String> = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info available-partitions"
    val out = shell.newJob().add("${getKsuDaemonPath()} $cmd").to(ArrayList(), null).exec().out
    out.filter { it.isNotBlank() }.map { it.trim() }
}

fun hasMagisk(): Boolean {
    if (!hasKsuManager) return false
    return runCatching {
        val shell = getRootShell(true)
        val result = shell.newJob().add("which magisk").exec()
        Log.i(TAG, "has magisk: ${result.isSuccess}")
        result.isSuccess
    }.getOrDefault(false)
}

fun isSepolicyValid(rules: String?): Boolean {
    if (rules == null) {
        return true
    }
    val shell = getRootShell()
    val result =
        shell.newJob().add("${getKsuDaemonPath()} sepolicy check '$rules'").to(ArrayList(), null)
            .exec()
    return result.isSuccess
}

fun getSepolicy(pkg: String): String {
    val shell = getRootShell()
    val result =
        shell.newJob().add("${getKsuDaemonPath()} profile get-sepolicy $pkg").to(ArrayList(), null)
            .exec()
    Log.i(TAG, "code: ${result.code}, out: ${result.out}, err: ${result.err}")
    return result.out.joinToString("\n")
}

fun setSepolicy(pkg: String, rules: String): Boolean {
    val shell = getRootShell()
    val result = shell.newJob().add("${getKsuDaemonPath()} profile set-sepolicy $pkg '$rules'")
        .to(ArrayList(), null).exec()
    Log.i(TAG, "set sepolicy result: ${result.code}")
    return result.isSuccess
}

fun listAppProfileTemplates(): List<String> {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile list-templates").to(ArrayList(), null)
        .exec().out
}

fun getAppProfileTemplate(id: String): String {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile get-template '${id}'")
        .to(ArrayList(), null).exec().out.joinToString("\n")
}

fun setAppProfileTemplate(id: String, template: String): Boolean {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} profile set-template ${id.shellQuote()} ${template.shellQuote()}"
    return shell.newJob().add(cmd)
        .to(ArrayList(), null).exec().isSuccess
}

fun deleteAppProfileTemplate(id: String): Boolean {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile delete-template '${id}'")
        .to(ArrayList(), null).exec().isSuccess
}
internal fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"

fun runCmd(shell: Shell, cmd: String): String {
    return shell.newJob()
        .add(cmd)
        .to(mutableListOf<String>(), null)
        .exec().out
        .joinToString("\n")
}

fun forceStopApp(packageName: String) {
    val shell = getRootShell()
    val result = shell.newJob().add("am force-stop $packageName").exec()
    Log.i(TAG, "force stop $packageName result: $result")
}

fun launchApp(packageName: String) {

    val shell = getRootShell()
    val result =
        shell.newJob()
            .add("cmd package resolve-activity --brief $packageName | tail -n 1 | xargs cmd activity start-activity -n")
            .exec()
    Log.i(TAG, "launch $packageName result: $result")
}

fun restartApp(packageName: String) {
    forceStopApp(packageName)
    launchApp(packageName)
}

fun isWebuiModuleInstalled(modId: String): Boolean {
    if (!hasKsuDaemon() || !hasKsuManager) return false
    return runCatching {
        ShellUtils.fastCmdResult(
            getRootShell(),
            "[ -f /data/adb/modules/${modId.shellQuote()}/webroot/index.html ]"
        )
    }.getOrDefault(false)
}

fun getSuSFSStatus(): String {
    if (!hasKsuDaemon() || !hasKsuManager) return ""
    val shell = getRootShell()
    return ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} susfs show variant").trim()
}

fun getSuSFSVersion(): String {
    if (!hasKsuDaemon() || !hasKsuManager) return ""
    val shell = getRootShell()
    val result = ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} susfs show version")
    return result
}

fun getSuSFSFeatures(): String {
    if (!hasKsuDaemon() || !hasKsuManager) return ""
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} susfs show enabled_features"
    return runCmd(shell, cmd)
}

fun getMetaModuleImplement(): String {
    if (!hasKsuManager) return "None"
    try {
        val metaModuleProp = SuFile.open("/data/adb/metamodule/module.prop")
        if (!metaModuleProp.isFile) {
            Log.i(TAG, "Meta module implement: None")
            return "None"
        }

        val prop = Properties()
        prop.load(metaModuleProp.newInputStream())

        val name = prop.getProperty("name")
        Log.i(TAG, "Meta module implement: $name")
        return name
    } catch (_ : Throwable) {
        Log.i(TAG, "Meta module implement: None")
        return "None"
    }
}

fun getZygiskImplement(): String {
    if (!hasKsuManager) return "None"
    val zygiskModuleIds = listOf(
        "zygisksu",
        "rezygisk",
        "shirokozygisk"
    )

    for (moduleId in zygiskModuleIds) {
        // 忽略禁用/即将删除
        if (SuFile.open("/data/adb/modules/$moduleId/disable").isFile || SuFile.open("/data/adb/modules/$moduleId/remove").isFile) continue

        // 读取prop
        val propFile = SuFile.open("/data/adb/modules/$moduleId/module.prop")
        if (!propFile.isFile) continue

        val prop = Properties()
        prop.load(propFile.newInputStream())

        val name = prop.getProperty("name")
        Log.i(TAG, "Zygisk implement: $name")
        return name
    }

    Log.i(TAG, "Zygisk implement: None")
    return "None"
}

fun addKernelUmountPath(path: String, flags: Int): Boolean {
    val shell = getRootShell()
    val flagsArg = if (flags >= 0) "--flags $flags" else ""
    val cmd = "${getKsuDaemonPath()} kernel umount add $path $flagsArg"
    val result = ShellUtils.fastCmdResult(shell, cmd)
    Log.i(TAG, "add umount path $path result: $result")
    return result
}

fun removeKernelUmountPath(path: String): Boolean {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} kernel umount del $path"
    val result = ShellUtils.fastCmdResult(shell, cmd)
    Log.i(TAG, "remove umount path $path result: $result")
    return result
}

fun listKernelUmountPaths(): String {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} kernel umount list"
    return try {
        runCmd(shell, cmd).trim()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to list umount paths", e)
        ""
    }
}

fun addUmountConfigUmountPath(path: String, flags: Int): Boolean {
    val shell = getRootShell()
    val flagsArg = if (flags >= 0) "--flags $flags" else ""
    val cmd = "${getKsuDaemonPath()} umount-config add $path $flagsArg"
    val result = ShellUtils.fastCmdResult(shell, cmd)
    Log.i(TAG, "add umount path $path result: $result")
    return result
}

fun removeUmountConfigUmountPath(path: String): Boolean {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} umount-config del $path"
    val result = ShellUtils.fastCmdResult(shell, cmd)
    Log.i(TAG, "remove umount path $path result: $result")
    return result
}

fun listUmountConfigUmountPaths(): String {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} umount-config list"
    return try {
        runCmd(shell, cmd).trim()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to list umount paths", e)
        ""
    }
}
