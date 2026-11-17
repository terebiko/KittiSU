package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.data.flash.FlashRepository

class CheckFlashModuleMountUseCase(private val repository: FlashRepository) {
    suspend operator fun invoke(uri: String) = repository.moduleNeedsMount(uri)
}
