package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.data.packageinfo.SuperUserRepository

class GetSuperUserAppGroupUseCase(private val repository: SuperUserRepository) {
    suspend operator fun invoke(uid: Int, primaryPackageName: String) = repository.getAppGroup(uid, primaryPackageName)
}
