package anhiutangerinee.kittisu.ui.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import anhiutangerinee.kittisu.KernelPatchManager
import anhiutangerinee.kittisu.ksuApp
import com.topjohnwu.superuser.CallbackList
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "BootImagePatcher"

enum class FolkPatchMode {
    PATCH_ONLY,
    DIRECT_INSTALL,
    INACTIVE_SLOT,
}

fun isValidSuperKey(superKey: String): Boolean {
    return superKey.length in 8..63 &&
        superKey.all { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' } &&
        superKey.any { it.isDigit() } &&
        superKey.any { it.isLetter() }
}

fun generateRandomSuperKey(length: Int = 12): String {
    val letters = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ"
    val digits = "23456789"
    val all = letters + digits
    val chars = mutableListOf(
        letters.random(),
        digits.random(),
    )
    repeat((length - 2).coerceAtLeast(6)) {
        chars.add(all.random())
    }
    chars.shuffle()
    return chars.joinToString("")
}

internal fun saveFileToDownloads(
    context: Context,
    srcFile: File,
    outFilename: String,
    onError: ((String) -> Unit)? = null,
): String? {
    val errors = mutableListOf<String>()

    // Tier 1: Android 10+ Scoped Storage via MediaStore.Downloads
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, outFilename)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    val outStream = resolver.openOutputStream(uri)
                        ?: throw IllegalStateException("openOutputStream returned null")
                    outStream.use { output ->
                        srcFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    val finishValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(uri, finishValues, null, null)
                    return "/storage/emulated/0/Download/$outFilename"
                } catch (e: Exception) {
                    Log.e(TAG, "MediaStore copy failed", e)
                    errors.add("MediaStore: ${e.message}")
                    runCatching { resolver.delete(uri, null, null) }
                }
            } else {
                errors.add("MediaStore: insert returned null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore insert failed", e)
            errors.add("MediaStore: ${e.message}")
        }
    }

    // Tier 2: Direct filesystem write to public Download folder
    try {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, outFilename)
        srcFile.inputStream().use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outFile.absolutePath
    } catch (e: Exception) {
        Log.e(TAG, "Direct Download write failed", e)
        errors.add("Direct: ${e.message}")
    }

    // Tier 3: Root shell copy to public Download folder (handles rooted devices / root-owned srcFile)
    if (rootAvailable()) {
        try {
            val dstPath = "/storage/emulated/0/Download/$outFilename"
            val copyRes = withNewRootShell {
                newJob()
                    .add("mkdir -p /storage/emulated/0/Download")
                    .add("cp -f ${srcFile.absolutePath.shellQuote()} ${dstPath.shellQuote()} && chmod 0664 ${dstPath.shellQuote()}")
                    .exec()
            }
            if (copyRes.isSuccess) {
                return dstPath
            }
            errors.add("Root cp exited with code ${copyRes.code}")
        } catch (e: Exception) {
            Log.e(TAG, "Root Download copy failed", e)
            errors.add("Root: ${e.message}")
        }
    }

    // Tier 4: App external Downloads directory (always writable on Android 10-16 without extra permissions)
    try {
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (extDir != null) {
            if (!extDir.exists()) extDir.mkdirs()
            val outFile = File(extDir, outFilename)
            srcFile.inputStream().use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return outFile.absolutePath
        }
    } catch (e: Exception) {
        Log.e(TAG, "App external Download save failed", e)
        errors.add("AppExt: ${e.message}")
    }

    onError?.invoke(errors.joinToString("; "))
    return null
}

/**
 * Patches a stock boot.img using the KernelPatch (APatch / FolkPatch) workflow:
 * kptools unpack -> kptools patch with kpimg + SuperKey -> kptools repack.
 * Executes `libbusybox.so` & `libkptools.so` directly from `nativeLibraryDir`
 * so Android 10–16 W^X SELinux restrictions never block execution.
 */
fun patchBootImageFolkPatch(
    bootUri: Uri?,
    kpimgUri: Uri?,
    superKey: String,
    mode: FolkPatchMode,
    onFinish: (Boolean, Int) -> Unit,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): Boolean {
    val context = ksuApp
    val patchDir = File(context.cacheDir, "kp_patch")
    try {
        patchDir.deleteRecursively()
        patchDir.mkdirs()

        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val kptoolsLib = File(nativeLibDir, "libkptools.so")
        val busyboxLib = File(nativeLibDir, "libbusybox.so")

        if (!kptoolsLib.isFile) {
            val msg = "- kptools binary is only available on ARM64 (arm64-v8a) devices (current ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"})."
            onStderr("$msg\n")
            onStdout(msg)
            onFinish(false, 1)
            return false
        }
        if (!busyboxLib.isFile) {
            val msg = "- busybox binary is missing in nativeLibraryDir."
            onStderr("$msg\n")
            onStdout(msg)
            onFinish(false, 1)
            return false
        }

        for (assetName in listOf("kp_boot_patch.sh", "kp_boot_extract.sh", "kp_util_functions.sh", "kpimg")) {
            val dest = File(patchDir, assetName)
            context.assets.open(assetName).use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        if (kpimgUri != null) {
            onStdout("- Using custom kpimg file...")
            val customKpimg = File(patchDir, "kpimg")
            context.contentResolver.openInputStream(kpimgUri)?.use { input ->
                customKpimg.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                onStderr("- Cannot read custom kpimg file!\n")
                onFinish(false, 1)
                return false
            }
        }

        val effectiveKey = superKey.trim()
        if (!isValidSuperKey(effectiveKey)) {
            val msg = "- Invalid SuperKey! Must be 8-63 alphanumeric characters containing both letters and digits."
            onStderr("$msg\n")
            onStdout(msg)
            onFinish(false, 1)
            return false
        }

        if (mode == FolkPatchMode.PATCH_ONLY) {
            if (bootUri == null) {
                onStderr("- No boot.img file selected!\n")
                onFinish(false, 1)
                return false
            }
            onStdout("- Copying selected boot image...")
            val srcBoot = File(patchDir, "boot.img")
            context.contentResolver.openInputStream(bootUri)?.use { input ->
                srcBoot.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                onStderr("- Cannot read selected boot.img!\n")
                onFinish(false, 1)
                return false
            }

            // Execute busybox & kptools directly from nativeLibraryDir (apk_data_file)
            // to stay 100% compliant with Android 10+ W^X / SELinux app_data_file execute restrictions.
            val pb = ProcessBuilder(
                busyboxLib.absolutePath,
                "sh",
                "kp_boot_patch.sh",
                effectiveKey,
                srcBoot.absolutePath
            )
            pb.environment()["ASH_STANDALONE"] = "1"
            pb.environment()["KPTOOLS"] = kptoolsLib.absolutePath
            pb.environment()["BUSYBOX"] = busyboxLib.absolutePath
            pb.directory(patchDir)
            pb.redirectErrorStream(true)

            val process = pb.start()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    onStdout(line ?: "")
                }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                onStdout("- Patch process exited with code: $exitCode")
                onFinish(false, exitCode)
                return false
            }

            val newBoot = File(patchDir, "new-boot.img")
            if (!newBoot.isFile) {
                onStdout("- Patch failed: new-boot.img was not generated.")
                onFinish(false, 1)
                return false
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFilename = "sovasu_boot_patched_${timestamp}.img"
            var saveError: String? = null
            val savedPath = saveFileToDownloads(context, newBoot, outFilename) { saveError = it }
            if (savedPath != null) {
                // Remember SuperKey automatically so SovaSU auto-unlocks after user flashes & reboots
                KernelPatchManager.saveSuperKey(effectiveKey)
                onStdout("- Patched boot image saved to:")
                onStdout("  $savedPath")
                onStdout("- Flash via fastboot: fastboot flash boot $outFilename")
                onStdout("- SuperKey has been saved in SovaSU — it will auto-activate after reboot!")
                onFinish(false, 0)
                return true
            } else {
                onStdout("- Failed to write patched boot image to Downloads${saveError?.let { ": $it" } ?: "."}")
                onFinish(false, 1)
                return false
            }
        } else {
            // DIRECT_INSTALL or INACTIVE_SLOT (requires root)
            val isNextSlot = mode == FolkPatchMode.INACTIVE_SLOT
            val stdoutCb = object : CallbackList<String?>() {
                override fun onAddElement(s: String?) {
                    onStdout(s ?: "")
                }
            }
            val stderrCb = object : CallbackList<String?>() {
                override fun onAddElement(s: String?) {
                    onStderr(s ?: "")
                }
            }

            val extractOut = ArrayList<String>()
            val bbPath = busyboxLib.absolutePath.shellQuote()
            val kpPath = kptoolsLib.absolutePath.shellQuote()
            val extractCmd = if (isNextSlot) "$bbPath sh kp_boot_extract.sh true" else "$bbPath sh kp_boot_extract.sh"
            val extractRes = withNewRootShell {
                newJob()
                    .add("export ASH_STANDALONE=1")
                    .add("cd ${patchDir.absolutePath.shellQuote()}")
                    .add(extractCmd)
                    .to(extractOut, stderrCb)
                    .exec()
            }
            extractOut.forEach { onStdout(it) }
            val bootDev = extractOut.firstOrNull { it.startsWith("BOOTIMAGE=") }?.removePrefix("BOOTIMAGE=")?.trim()
            if (!extractRes.isSuccess || bootDev.isNullOrEmpty()) {
                onStdout("- Failed to locate boot partition device.")
                onFinish(false, if (extractRes.code != 0) extractRes.code else 1)
                return false
            }

            val patchRes = withNewRootShell {
                newJob()
                    .add("export ASH_STANDALONE=1")
                    .add("export KPTOOLS=$kpPath")
                    .add("export BUSYBOX=$bbPath")
                    .add("cd ${patchDir.absolutePath.shellQuote()}")
                    .add("$bbPath sh kp_boot_patch.sh ${effectiveKey.shellQuote()} ${bootDev.shellQuote()} true")
                    .to(stdoutCb, stderrCb)
                    .exec()
            }
            if (patchRes.isSuccess) {
                KernelPatchManager.saveSuperKey(effectiveKey)
            }
            onFinish(patchRes.isSuccess, patchRes.code)
            return patchRes.isSuccess
        }
    } catch (e: Throwable) {
        Log.e(TAG, "patchBootImageFolkPatch error", e)
        onStderr("- Error: ${e.message}\n")
        onStdout("- Error: ${e.message}")
        onFinish(false, 1)
        return false
    } finally {
        runCatching { patchDir.deleteRecursively() }
    }
}
