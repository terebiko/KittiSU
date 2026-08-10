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

@SuppressLint("RestrictedApi")
object KsuCli {
    var SHELL: Shell = createRootShell()
    val GLOBAL_MNT_SHELL: Shell = createRootShell(true)
}

fun getRootShell(globalMnt: Boolean = false): Shell {
    return if (globalMnt) KsuCli.GLOBAL_MNT_SHELL else {
        KsuCli.SHELL
    }
}

fun generateMainShellBuilder(): Shell.Builder {
    val builder = Shell.Builder.create()
    try {
        builder.setCommands(getKsuDaemonPath(), "debug", "su")
        builder.build()
    } catch (e: Throwable) {
        Log.w(TAG, "ksu failed: ", e)
        try {
            builder.setCommands("su")
            builder.build()
        } catch (e: Throwable) {
            Log.e(TAG, "su failed: ", e)
            builder.setCommands("sh")
            builder.build()
        }
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
    Shell.enableVerboseLogging = BuildConfig.DEBUG
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
    return if (newShell) {
        withNewRootShell {
            ShellUtils.fastCmdResult(this, "${getKsuDaemonPath()} $args")
        }
    } else {
        ShellUtils.fastCmdResult(getRootShell(), "${getKsuDaemonPath()} $args")
    }
}

fun backupModules(path: String): Boolean {
    val quoted = path.shellQuote()
    return execKsud("module backup $quoted", true) &&
        ShellUtils.fastCmdResult(getRootShell(), "chmod 0644 $quoted")
}

fun inspectModuleBackup(path: String): List<String>? {
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
    val ids = moduleIds.joinToString(" ") { it.shellQuote() }
    return execKsud("module restore ${path.shellQuote()} $ids", true)
}

data class BootRecoveryState(val failures: Int, val modules: List<String>)

fun getBootRecoveryState(): BootRecoveryState? {
    val output = mutableListOf<String>()
    val result = getRootShell().newJob()
        .add("${getKsuDaemonPath()} recovery")
        .to(output, null)
        .exec()
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
    val result = getRootShell().newJob()
        .add("${getKsuDaemonPath()} kernel dynamic-manager get")
        .to(ArrayList<String>(), null).exec()
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
    val shell = getRootShell()
    val out = shell.newJob()
        .add("${getKsuDaemonPath()} feature check $feature").to(ArrayList<String>(), null).exec().out
    out.firstOrNull()?.trim().orEmpty()
}

suspend fun getFeaturePersistValue(feature: String): Long? = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val out = shell.newJob()
        .add("${getKsuDaemonPath()} feature get --config $feature").to(ArrayList<String>(), null)
        .exec().out
    val valueLine = out.firstOrNull { it.trim().startsWith("Value:") } ?: return@withContext null
    valueLine.substringAfter("Value:").trim().toLongOrNull()
}
fun install() {
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
    val shell = getRootShell()

    val out = shell.newJob()
        .add("${getKsuDaemonPath()} module list").to(ArrayList(), null).exec().out
    return out.joinToString("\n").ifBlank { "[]" }
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
        val cmd = "module install ${file.absolutePath}"
        val result = flashWithIO("${getKsuDaemonPath()} $cmd", onStdout, onStderr)
        Log.i("KernelSU", "install module $uri result: $result")

        file.delete()

        onFinish(result.isSuccess, result.code)
        return result.isSuccess
    }
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
    val shell = getRootShell()
    return shell.isRoot
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
    val shell = getRootShell(true)
    val result = shell.newJob().add("which magisk").exec()
    Log.i(TAG, "has magisk: ${result.isSuccess}")
    return result.isSuccess
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
    val escapedTemplate = template.replace("\"", "\\\"")
    val cmd = """${getKsuDaemonPath()} profile set-template "$id" "$escapedTemplate'""""
    return shell.newJob().add(cmd)
        .to(ArrayList(), null).exec().isSuccess
}

fun deleteAppProfileTemplate(id: String): Boolean {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile delete-template '${id}'")
        .to(ArrayList(), null).exec().isSuccess
}
// KPM控制
internal fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"

fun loadKpmModule(path: String, args: String? = null): String {
    val shell = getRootShell()
    val cmd = buildString {
        append(getKsuDaemonPath()).append(" kpm load ").append(path.shellQuote())
        args?.let { append(' ').append(it.shellQuote()) }
    }
    return ShellUtils.fastCmd(shell, cmd)
}

fun unloadKpmModule(name: String): String {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} kpm unload ${name.shellQuote()}"
    return ShellUtils.fastCmd(shell, cmd)
}

fun getKpmModuleCount(): Int {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} kpm num"
    val result = ShellUtils.fastCmd(shell, cmd)
    return result.trim().toIntOrNull() ?: 0
}

fun runCmd(shell: Shell, cmd: String): String {
    return shell.newJob()
        .add(cmd)
        .to(mutableListOf<String>(), null)
        .exec().out
        .joinToString("\n")
}

fun listKpmModules(): String {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} kpm list"
    return try {
        runCmd(shell, cmd).trim()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to list KPM modules", e)
        ""
    }
}

fun getKpmModuleInfo(name: String): String {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} kpm info ${name.shellQuote()}"
    return try {
        runCmd(shell, cmd).trim()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get KPM module info: $name", e)
        ""
    }
}

fun controlKpmModule(name: String, args: String? = null): Int {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} kpm control ${name.shellQuote()} ${(args ?: "").shellQuote()}"
    val result = runCmd(shell, cmd)
    return result.trim().toIntOrNull() ?: -1
}

fun getKpmVersion(): String {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} kpm version"
    val result = ShellUtils.fastCmd(shell, cmd)
    return result.trim()
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

fun getSuSFSStatus(): String {
    val shell = getRootShell()
    return ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} susfs status").trim()
}

fun getSuSFSVersion(): String {
    val shell = getRootShell()
    val result = ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} susfs version")
    return result
}

fun getSuSFSFeatures(): String {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} susfs features"
    return runCmd(shell, cmd)
}

fun getMetaModuleImplement(): String {
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
