package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.data.AppSettingsRepository
import anhiutangerinee.kittisu.data.startup.ApplicationInitializationRepository
import anhiutangerinee.kittisu.data.startup.StartupRepository

class InitializeApplicationUseCase(
    private val settingsRepository: AppSettingsRepository,
    private val startupRepository: StartupRepository,
    private val initializationRepository: ApplicationInitializationRepository,
) {
    suspend operator fun invoke() {
        runCatching {
            settingsRepository.preload()
            initializationRepository.initialize()
        }.onSuccess {
            startupRepository.markReady()
        }.onFailure { error ->
            startupRepository.markFailed(error)
        }
    }
}
