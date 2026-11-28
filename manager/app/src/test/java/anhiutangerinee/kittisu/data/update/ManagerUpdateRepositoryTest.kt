package anhiutangerinee.kittisu.data.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerUpdateRepositoryTest {
    @Test
    fun release_prefersNormalApkAndMatchesAbiAndParsesVersionCode() {
        val release = JSONObject()
            .put("tag_name", "v5.0-beta1")
            .put("body", "notes")
            .put("assets", JSONArray()
                .put(JSONObject().put("name", "Spoofed-SovaSU_v5.0_40100-arm64-v8a-release.apk"))
                .put(JSONObject()
                    .put("name", "SovaSU_v5.0_40100-arm64-v8a-release.apk")
                    .put("browser_download_url", "https://example.invalid/arm64.apk"))
                .put(JSONObject()
                    .put("name", "SovaSU_v5.0_40100-x86_64-release.apk")
                    .put("browser_download_url", "https://example.invalid/x86_64.apk")))

        val armResult = ManagerUpdateParser.release(release, listOf("arm64-v8a"))
        assertEquals(40100, armResult.versionCode)
        assertEquals("v5.0-beta1", armResult.versionName)
        assertEquals("https://example.invalid/arm64.apk", armResult.downloadUrl)
        assertFalse(armResult.isZipArtifact)

        val x86Result = ManagerUpdateParser.release(release, listOf("x86_64"))
        assertEquals("https://example.invalid/x86_64.apk", x86Result.downloadUrl)
    }

    @Test
    fun nightly_usesNewestSovaSuWorkflowArtifactAndStopsAtCurrentCommit() {
        val current = "1234567890abcdef"
        val currentArtifact = artifact(9, current, "SovaSU")
        val olderArtifact = artifact(8, "abcdef1234567890", "SovaSU")
        val otherBranchArtifact = artifact(10, "fedcba9876543210", "dev")

        assertFalse(
            ManagerUpdateParser.nightly(
                JSONObject().put("artifacts", JSONArray().put(otherBranchArtifact).put(currentArtifact).put(olderArtifact)),
                current,
            ).isNewerThan(1, current)
        )

        val update = ManagerUpdateParser.nightly(
            JSONObject().put("artifacts", JSONArray().put(otherBranchArtifact).put(olderArtifact)),
            current,
        )
        assertTrue(update.isNewerThan(1, current))
        assertEquals("SovaSU-abcdef1", update.versionName)
        assertTrue(update.isZipArtifact)
        assertEquals(
            "https://nightly.link/terebiko/KittiSU/actions/runs/1008/Manager-release.zip",
            update.downloadUrl,
        )
    }

    @Test
    fun nightlyRelease_selectsSingleAbiApkDirectlyAndStopsAtCurrentCommit() {
        val current = "1234567890abcdef"
        val newerSha = "abcdef1234567890"
        val release = JSONObject()
            .put("tag_name", "nightly")
            .put("name", "SovaSU Nightly (abcdef1)")
            .put("body", "Workflow SovaSU • abcdef1\nCommit: $newerSha\n\nfeat: anti-brick")
            .put("assets", JSONArray()
                .put(JSONObject()
                    .put("name", "Spoofed-SovaSU_20261128_35500-arm64-v8a-release.apk")
                    .put("browser_download_url", "https://example.invalid/spoofed-arm64.apk"))
                .put(JSONObject()
                    .put("name", "SovaSU_20261128_35500-arm64-v8a-release.apk")
                    .put("browser_download_url", "https://example.invalid/arm64.apk"))
                .put(JSONObject()
                    .put("name", "SovaSU_20261128_35500-universal-release.apk")
                    .put("browser_download_url", "https://example.invalid/universal.apk")))

        val update = ManagerUpdateParser.nightlyRelease(release, current, listOf("arm64-v8a"))!!
        assertTrue(update.isNewerThan(35400, current))
        assertFalse(update.isZipArtifact)
        assertEquals("https://example.invalid/arm64.apk", update.downloadUrl)
        assertEquals("SovaSU-abcdef1", update.versionName)
        assertEquals(35500, update.versionCode)

        val sameCommit = ManagerUpdateParser.nightlyRelease(release, newerSha, listOf("arm64-v8a"))!!
        assertFalse(sameCommit.isNewerThan(35400, newerSha))
    }

    @Test
    fun selectBestApkName_fallsBackToUniversalWhenAbiMissing() {
        val entries = listOf(
            "SovaSU_20261124_35481-arm64-v8a-release.apk",
            "SovaSU_20261124_35481-universal-release.apk",
        )
        assertEquals(
            "SovaSU_20261124_35481-universal-release.apk",
            ManagerUpdateParser.selectBestApkName(entries, listOf("armeabi-v7a")),
        )
    }

    private fun artifact(id: Long, sha: String, branch: String) = JSONObject()
        .put("id", id)
        .put("expired", false)
        .put("workflow_run", JSONObject()
            .put("id", 1000 + id)
            .put("head_branch", branch)
            .put("head_sha", sha))
}
