package anhiutangerinee.kittisu.ui.component

import androidx.compose.runtime.Composable
import anhiutangerinee.kittisu.KernelPatchManager
import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.ui.util.rootAvailable

@Composable
inline fun KsuIsValid(
    content: @Composable () -> Unit
) {
    if (ksuIsValid())
        content()
}

@Volatile
private var tested = false
@Volatile
private var ksuNativeValid = false

fun resetKsuValidCache() {
    tested = false
}

/**
 * True only when the real KernelSU kernel driver is present and matches version requirements.
 */
fun isKsuNativeValid(): Boolean {
    if (!tested) {
        val isManager = runCatching { Natives.isManager }.getOrDefault(false)
        val ksuVersion = if (isManager) runCatching { Natives.version }.getOrNull() else null
        ksuNativeValid = ksuVersion != null && ksuVersion >= Natives.MINIMAL_SUPPORTED_KERNEL
        tested = true
    }
    return ksuNativeValid
}

/**
 * True when EITHER KernelSU driver OR KernelPatch (APatch / FolkPatch) backend is active.
 */
fun ksuIsValid(): Boolean {
    return isKsuNativeValid() || KernelPatchManager.isKernelPatchActive
}

/**
 * Check is full feature
 */
fun isFullFeatured(): Boolean {
    return ksuIsValid() && (!isKsuNativeValid() || !Natives.requireNewKernel()) && rootAvailable()
}
