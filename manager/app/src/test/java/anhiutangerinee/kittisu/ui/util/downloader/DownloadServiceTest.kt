package anhiutangerinee.kittisu.ui.util.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class DownloadServiceTest {
    private val directory = File(System.getProperty("java.io.tmpdir"), "kittisu-download-test")

    @Test
    fun resolveAvailableTarget_rejectsTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveDownloadTarget(directory, "../escaped.zip")
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveDownloadTarget(directory, "/tmp/escaped.zip")
        }
    }

    @Test
    fun resolveAvailableTarget_keepsFileInsideDirectory() {
        assertEquals(
            File(directory.canonicalFile, "module.zip"),
            resolveDownloadTarget(directory, "module.zip")
        )
    }
}
