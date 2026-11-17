package anhiutangerinee.kittisu.domain.usecase

import java.io.File
import anhiutangerinee.kittisu.data.logging.BugreportRepository

class GenerateBugreportUseCase(
    private val repository: BugreportRepository,
) {
    operator fun invoke(): File = repository.create()
}
