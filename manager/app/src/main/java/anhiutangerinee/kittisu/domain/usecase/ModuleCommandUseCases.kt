package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.data.module.ModuleRepository

class SetModuleEnabledUseCase(
    private val repository: ModuleRepository,
) {
    suspend operator fun invoke(moduleId: String, enabled: Boolean) = repository.setModuleEnabled(moduleId, enabled)
}

class SetModuleRemovedUseCase(
    private val repository: ModuleRepository,
) {
    suspend operator fun invoke(moduleId: String, removed: Boolean) = repository.setModuleRemoved(moduleId, removed)
}
