package anhiutangerinee.kittisu.data.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerUpdateRepositoryTest {
    @Test
    fun release_prefersNormalApkAndParsesVersionCode() {
        val release = JSONObject()
            .put("tag_name", "v5.0-beta1")
            .put("body", "notes")
            .put("assets", JSONArray()
                .put(JSONObject().put("name", "Spoofed-KittiSU_v5.0_40100-release.apk"))
                .put(JSONObject()
                    .put("name", "KittiSU_v5.0_40100-release.apk")
                    .put("browser_download_url", "https://example.invalid/manager.apk")))

        val result = ManagerUpdateParser.release(release)

        assertEquals(40100, result.versionCode)
        assertEquals("v5.0-beta1", result.versionName)
        assertEquals("https://example.invalid/manager.apk", result.downloadUrl)
    }

    @Test
    fun nightly_usesNewestDevArtifactAndStopsAtCurrentCommit() {
        val current = "1234567890abcdef"
        val currentArtifact = artifact(9, current)
        val olderArtifact = artifact(8, "abcdef1234567890")

        assertFalse(
            ManagerUpdateParser.nightly(
                JSONObject().put("artifacts", JSONArray().put(currentArtifact).put(olderArtifact)),
                current,
            ).isNewerThan(1, current)
        )

        val update = ManagerUpdateParser.nightly(
            JSONObject().put("artifacts", JSONArray().put(olderArtifact)),
            current,
        )
        assertTrue(update.isNewerThan(1, current))
        assertEquals("nightly-abcdef1", update.versionName)
    }

    private fun artifact(id: Long, sha: String) = JSONObject()
        .put("id", id)
        .put("expired", false)
        .put("workflow_run", JSONObject()
            .put("id", 1000 + id)
            .put("head_branch", "dev")
            .put("head_sha", sha))
}
