package zako.zako.zako.zakoui.screen.kernelFlash.state

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.util.flashAnyKernel
import anhiutangerinee.kittisu.ui.util.install
import anhiutangerinee.kittisu.ui.util.rootAvailable
import anhiutangerinee.kittisu.utils.AssetsUtil
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class FlashState(
    val isFlashing: Boolean = false,
    val isCompleted: Boolean = false,
    val progress: Float = 0f,
    val currentStep: String = "",
    val logs: List<String> = emptyList(),
    val error: String = ""
)

class HorizonKernelState {
    private val _state = MutableStateFlow(FlashState())
    private val fullLogs = ConcurrentLinkedQueue<String>()
    val state: StateFlow<FlashState> = _state.asStateFlow()

    fun updateProgress(progress: Float) {
        _state.update { it.copy(progress = progress) }
    }

    fun updateStep(step: String) {
        _state.update { it.copy(currentStep = step) }
    }

    fun addLog(log: String) {
        fullLogs.add(log)
        _state.update { it.copy(logs = it.logs + log) }
    }

    fun addConsoleLog(log: String) {
        fullLogs.add(log)
    }

    fun getFullLog(): String = fullLogs.joinToString("\n")

    fun setError(error: String) {
        _state.update { it.copy(isFlashing = false, error = error) }
    }

    fun startFlashing() {
        fullLogs.clear()
        _state.update {
            it.copy(
                isFlashing = true,
                isCompleted = false,
                progress = 0f,
                currentStep = "",
                logs = emptyList(),
                error = ""
            )
        }
    }

    fun completeFlashing() {
        _state.update { it.copy(isFlashing = false, isCompleted = true, progress = 1f) }
    }

    fun reset() {
        fullLogs.clear()
        _state.value = FlashState()
    }
}

class HorizonKernelWorker(
    private val context: Context,
    private val state: HorizonKernelState,
    private val slot: String? = null,
    private val kpmPatchEnabled: Boolean = false,
    private val kpmUndoPatch: Boolean = false
) : Thread() {
    var uri: Uri? = null
    private var onFlashComplete: (() -> Unit)? = null

    fun setOnFlashCompleteListener(listener: () -> Unit) {
        onFlashComplete = listener
    }

    override fun run() {
        state.startFlashing()
        state.updateStep(context.getString(R.string.horizon_preparing))

        val zipFile = File(context.cacheDir, "anykernel3.zip")
        val workDir = File(context.cacheDir, "kpm-kernel-patch")
        try {
            if (!rootAvailable()) {
                state.setError(context.getString(R.string.root_required))
                return
            }

            state.updateStep(context.getString(R.string.horizon_copying_files))
            state.updateProgress(0.2f)
            copyToCache(zipFile)

            if (kpmPatchEnabled || kpmUndoPatch) {
                state.updateStep(context.getString(R.string.kpm_preparing_tools))
                state.updateProgress(0.45f)
                patchKpm(zipFile, workDir)
            }

            state.updateStep(context.getString(R.string.horizon_flashing))
            state.updateProgress(0.7f)
            if (!flashAnyKernel(zipFile, slot, ::handleOutput, ::handleConsoleOutput)) {
                state.setError(context.getString(R.string.flash_failed_message))
                return
            }

            runCatching { install() }.onFailure {
                Log.w(TAG, "Failed to refresh ksud after a successful kernel flash", it)
            }
            state.updateStep(context.getString(R.string.horizon_flash_complete_status))
            state.completeFlashing()
            Handler(Looper.getMainLooper()).post { onFlashComplete?.invoke() }
        } catch (error: Exception) {
            state.setError(error.message ?: context.getString(R.string.horizon_unknown_error))
        } finally {
            zipFile.delete()
            workDir.deleteRecursively()
        }
    }

    private fun copyToCache(zipFile: File) {
        zipFile.delete()
        val source = uri ?: throw IOException(context.getString(R.string.horizon_copy_failed))
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException(context.getString(R.string.horizon_copy_failed))
        input.use { sourceStream ->
            zipFile.outputStream().use { output -> sourceStream.copyTo(output) }
        }
        if (!zipFile.isFile) throw IOException(context.getString(R.string.horizon_copy_failed))
    }

    private fun patchKpm(zipFile: File, workDir: File) {
        workDir.deleteRecursively()
        val extracted = File(workDir, "extracted").apply { mkdirs() }
        val kptools = File(workDir, "kptools")
        val kpimg = File(workDir, "kpimg")
        AssetsUtil.exportFiles(context, "kptools", kptools.absolutePath)
        AssetsUtil.exportFiles(context, "kpimg", kpimg.absolutePath)
        if (!kptools.isFile || !kpimg.isFile) {
            throw IOException("Local KPM tool extraction failed")
        }
        runCommand("chmod a+rx ${kptools.absolutePath}")
        runCommand("cd ${extracted.absolutePath} && unzip -o ${quote(zipFile.absolutePath)}")

        val imagePath = Shell.cmd("find ${extracted.absolutePath} -name '*Image*' -type f")
            .exec().out.firstOrNull()?.trim().orEmpty()
        if (imagePath.isEmpty()) throw IOException(context.getString(R.string.kpm_image_file_not_found))

        val image = File(imagePath)
        val imageDir = image.parentFile ?: throw IOException("Image has no parent directory")
        kptools.copyTo(File(imageDir, "kptools"), overwrite = true)
        kpimg.copyTo(File(imageDir, "kpimg"), overwrite = true)
        val operation = if (kpmUndoPatch) "-u" else "-p"
        runCommand(
            "cd ${imageDir.absolutePath} && chmod a+rx kptools && " +
                "./kptools $operation -s 123 -i ${quote(image.name)} -k kpimg -o oImage && " +
                "mv oImage ${quote(image.name)}"
        )
        File(imageDir, "kptools").delete()
        File(imageDir, "kpimg").delete()
        repackZipFolder(extracted, zipFile)
        state.addLog(
            context.getString(
                if (kpmUndoPatch) R.string.kpm_undo_patch_success else R.string.kpm_patch_success
            )
        )
    }

    private fun repackZipFolder(sourceDir: File, destination: File) {
        val temporary = File(destination.parentFile, "${destination.name}.patched")
        FileOutputStream(temporary).use { output ->
            ZipOutputStream(output).use { zip ->
                sourceDir.walkTopDown().filter(File::isFile).forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(sourceDir).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    }

    private fun runCommand(command: String) {
        val result = Shell.cmd(command).exec()
        if (!result.isSuccess) throw IOException("Command failed (${result.code}): $command")
    }

    private fun handleOutput(line: String) {
        Log.i(TAG, line)
        state.addLog(line)
        when {
            line.contains("extracting", ignoreCase = true) -> state.updateProgress(0.75f)
            line.contains("installing", ignoreCase = true) -> state.updateProgress(0.85f)
            line.contains("complete", ignoreCase = true) -> state.updateProgress(0.95f)
        }
    }

    private fun handleConsoleOutput(line: String) {
        Log.i(TAG, line)
        state.addConsoleLog(line)
    }

    private fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private companion object {
        const val TAG = "HorizonKernelWorker"
    }
}
