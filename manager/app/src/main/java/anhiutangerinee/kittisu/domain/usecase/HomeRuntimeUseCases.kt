package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.data.network.NetworkStatusRepository
import anhiutangerinee.kittisu.data.system.HomeRuntimeRepository

class GetHomeBasicInfoUseCase(private val repository: HomeRuntimeRepository) {
    suspend operator fun invoke(
        managerUapiVersion: Int,
        includeSelinuxStatus: Boolean = true,
    ) = repository.getBasicInfo(managerUapiVersion, includeSelinuxStatus)
}

class IsNetworkAvailableUseCase(private val repository: NetworkStatusRepository) {
    operator fun invoke() = repository.isAvailable()
}
