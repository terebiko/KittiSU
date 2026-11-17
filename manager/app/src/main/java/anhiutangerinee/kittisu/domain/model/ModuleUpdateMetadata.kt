package anhiutangerinee.kittisu.domain.model

data class ModuleUpdateMetadata(
    val zipUrl: String,
    val version: String,
    val changelog: String,
    val versionCode: Int = 0,
)
