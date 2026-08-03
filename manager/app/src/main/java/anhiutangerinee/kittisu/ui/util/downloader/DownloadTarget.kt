package anhiutangerinee.kittisu.ui.util.downloader

import java.io.File

internal fun resolveDownloadTarget(directory: File, fileName: String): File {
    require(fileName.isNotBlank() && fileName == File(fileName).name) { "Invalid file name" }
    val canonicalDirectory = directory.canonicalFile
    val dotIndex = fileName.lastIndexOf('.')
    val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
    val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""

    var index = 0
    while (true) {
        val candidateName = if (index == 0) fileName else "$baseName ($index)$extension"
        val candidate = File(canonicalDirectory, candidateName)
        require(candidate.canonicalFile.parentFile == canonicalDirectory) { "Invalid file name" }
        if (!candidate.exists()) return candidate
        index++
    }
}
