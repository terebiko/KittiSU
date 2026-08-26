package anhiutangerinee.kittisu.security

import anhiutangerinee.kittisu.ui.util.getRootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BootIdReader {
    suspend fun currentBootId(): String = withContext(Dispatchers.IO) {
        runCatching {
            getRootShell().newJob()
                .add("cat /proc/sys/kernel/random/boot_id")
                .to(mutableListOf<String>(), null)
                .exec()
                .out
                .firstOrNull()?.trim().orEmpty()
        }.getOrDefault("")
    }
}
