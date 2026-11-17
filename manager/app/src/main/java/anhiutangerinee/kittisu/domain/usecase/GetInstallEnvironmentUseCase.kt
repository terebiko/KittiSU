package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.data.flash.FlashRepository
import anhiutangerinee.kittisu.domain.model.InstallEnvironment

class GetInstallEnvironmentUseCase(
    private val repository: FlashRepository,
) {
    fun cached(): InstallEnvironment? = repository.installEnvironment.value

    suspend operator fun invoke(forceRefresh: Boolean = false) = repository.getInstallEnvironment(forceRefresh)
}
