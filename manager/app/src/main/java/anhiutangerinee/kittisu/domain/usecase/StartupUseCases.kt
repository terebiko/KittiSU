package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.data.startup.StartupRepository

class ObserveStartupStateUseCase(
    private val repository: StartupRepository,
) {
    operator fun invoke() = repository.state
}
