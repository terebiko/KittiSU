package anhiutangerinee.kittisu.ui.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.system.Os
import android.util.Log
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

private fun savePatchedBootToDownloads(
    context: Context,
    patchedFile: File,
    outFilename: String,
): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, outFilename)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { output ->
                patchedFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return null
            val finishValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, finishValues, null, null)
            "/storage/emulated/0/Download/$outFilename"
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore save failed", e)
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    } else {
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, outFilename)
            patchedFile.inputStream().use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Legacy Download save failed", e)
            null
        }
    }
}

/**
 * Patches a stock boot.img using the KernelPatch (APatch / FolkPatch) workflow:
 * kptools unpack -> kptools patch with kpimg + SuperKey -> kptools repack.
 * Works without root for PATCH_ONLY, and with root for DIRECT_INSTALL / INACTIVE_SLOT.
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
            onStdout("$msg\n")
            onFinish(false, 1)
            return false
        }
        if (!busyboxLib.isFile) {
            val msg = "- busybox binary is missing in nativeLibraryDir."
            onStderr("$msg\n")
            onStdout("$msg\n")
            onFinish(false, 1)
            return false
        }

        fun linkOrCopyExec(src: File, targetName: String) {
            val dest = File(patchDir, targetName)
            try {
                Os.symlink(src.absolutePath, dest.absolutePath)
            } catch (_: Throwable) {
                src.copyTo(dest, overwrite = true)
                dest.setExecutable(true)
            }
        }

        linkOrCopyExec(kptoolsLib, "kptools")
        linkOrCopyExec(busyboxLib, "busybox")

        for (assetName in listOf("kp_boot_patch.sh", "kp_boot_extract.sh", "kp_util_functions.sh", "kpimg")) {
            val dest = File(patchDir, assetName)
            context.assets.open(assetName).use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (assetName.endsWith(".sh")) {
                dest.setExecutable(true)
            }
        }

        if (kpimgUri != null) {
            onStdout("- Using custom kpimg file...\n")
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
            onStdout("$msg\n")
            onFinish(false, 1)
            return false
        }

        if (mode == FolkPatchMode.PATCH_ONLY) {
            if (bootUri == null) {
                onStderr("- No boot.img file selected!\n")
                onFinish(false, 1)
                return false
            }
            onStdout("- Copying selected boot image...\n")
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

            val pb = ProcessBuilder(
                "sh", "-c", "./busybox sh kp_boot_patch.sh \"$0\" \"$@\"",
                effectiveKey, srcBoot.absolutePath
            )
            pb.environment()["ASH_STANDALONE"] = "1"
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
            val savedPath = savePatchedBootToDownloads(context, newBoot, outFilename)
            if (savedPath != null) {
                onStdout("- Patched boot image saved to:")
                onStdout("  $savedPath")
                onStdout("- Flash via fastboot: fastboot flash boot $outFilename")
                onStdout("- Remember your SuperKey to manage root after reboot!")
                onFinish(false, 0)
                return true
            } else {
                onStdout("- Failed to write patched boot image to Downloads.")
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
            val extractCmd = if (isNextSlot) "./busybox sh kp_boot_extract.sh true" else "./busybox sh kp_boot_extract.sh"
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
                    .add("cd ${patchDir.absolutePath.shellQuote()}")
                    .add("./busybox sh kp_boot_patch.sh ${effectiveKey.shellQuote()} ${bootDev.shellQuote()} true")
                    .to(stdoutCb, stderrCb)
                    .exec()
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
