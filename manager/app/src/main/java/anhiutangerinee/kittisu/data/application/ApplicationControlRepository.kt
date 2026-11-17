package anhiutangerinee.kittisu.data.application

import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApplicationControlRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    suspend fun ensureManagerInstalled(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (Natives.isFullFeatured() && ksuCliRepository.rootAvailable()) {
                ksuCliRepository.install()
            }
        }
    }

    suspend fun reboot(reason: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { ksuCliRepository.reboot(reason) }
    }
}
