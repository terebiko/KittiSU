package anhiutangerinee.kittisu.ui.util.module

data class LatestVersionInfo(
    val versionCode : Int = 0,
    val downloadUrl : String = "",
    val changelog : String = "",
    val versionName: String = "",
    val commitSha: String = "",
) {
    fun isNewerThan(currentVersionCode: Long, currentCommit: String): Boolean =
        if (commitSha.isNotBlank()) !currentCommit.startsWith(commitSha) &&
            !commitSha.startsWith(currentCommit)
        else versionCode > currentVersionCode
}
