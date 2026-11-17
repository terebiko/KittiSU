package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.data.application.ApplicationControlRepository

class RebootUseCase(
    private val repository: ApplicationControlRepository,
) {
    suspend operator fun invoke(reason: String = "") = repository.reboot(reason)
}
