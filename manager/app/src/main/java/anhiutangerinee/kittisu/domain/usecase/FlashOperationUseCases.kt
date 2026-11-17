package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.data.flash.FlashRepository
import anhiutangerinee.kittisu.domain.model.FlashOperation

class ExecuteFlashOperationUseCase(private val repository: FlashRepository) {
    operator fun invoke(operation: FlashOperation) = repository.execute(operation)
}
