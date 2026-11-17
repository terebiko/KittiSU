package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.data.susfs.SuSFSRepository

class GetSuSFSStatusUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke() = repository.getStatus()
}
