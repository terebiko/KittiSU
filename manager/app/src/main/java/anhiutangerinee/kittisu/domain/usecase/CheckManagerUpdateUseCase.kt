package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.data.update.ManagerUpdateRepository
import anhiutangerinee.kittisu.domain.model.ManagerUpdateChannel
import anhiutangerinee.kittisu.domain.model.ManagerUpdateInfo

class CheckManagerUpdateUseCase(
    private val repository: ManagerUpdateRepository,
) {
    suspend operator fun invoke(channel: ManagerUpdateChannel): ManagerUpdateInfo? = when (channel) {
        ManagerUpdateChannel.STABLE -> repository.checkStableUpdate()
        ManagerUpdateChannel.BETA -> repository.checkBetaUpdate()
    }
}
