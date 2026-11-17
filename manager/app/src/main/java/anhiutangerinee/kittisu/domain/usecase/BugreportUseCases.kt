package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.data.logging.BugreportRepository
import java.io.File

class GenerateBugreportUseCase(
    private val repository: BugreportRepository,
) {
    operator fun invoke(): File = repository.create()
}
