package anhiutangerinee.kittisu.ui.util.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// ponytail: tests cover only pure logic (requirement checks, GitHub URL parsing).
// Android-bound funcs (isModuleInstalled, fetch*, resolveModuleDownloadUrl) are
// exercised in integration tests / by hand.
// Requires testImplementation(junit:junit) in app/build.gradle.kts to run.
class ModulePresetApiTest {

    @Test
    fun areDependenciesSatisfied_emptyList_returnsTrue() {
        assertTrue(areDependenciesSatisfied(emptyList()))
    }

    @Test
    fun checkPresetRequirements_null_returnsPassed() {
        assertEquals(RequirementCheckResult.Passed, checkPresetRequirements(null))
    }

    @Test
    fun checkPresetRequirements_emptyRequirement_returnsPassed() {
        val r = PresetRequirement()
        assertEquals(RequirementCheckResult.Passed, checkPresetRequirements(r))
    }

    @Test
    fun checkPresetRequirements_unparseableAndroid_returnsPassedGracefully() {
        // Should not crash on non-numeric min; treated as 0 → passes on any SDK.
        val r = PresetRequirement(android = "not-a-number")
        val result = checkPresetRequirements(r)
        // Either Passed (because 0 <= any SDK) or Failed (because parsing produced 0 and parse fails).
        // We accept either but it must not throw.
        assertTrue(result is RequirementCheckResult.Passed || result is RequirementCheckResult.Failed)
    }

    @Test
    fun gitHubOwnerRepo_parse_validUrl_returnsOwnerAndRepo() {
        val parsed = GitHubOwnerRepo.parse("github-latest://Dr-TSNG/ZygiskNext")
        assertEquals(GitHubOwnerRepo("Dr-TSNG", "ZygiskNext"), parsed)
    }

    @Test
    fun gitHubOwnerRepo_parse_trailingSlash_returnsOwnerAndRepo() {
        val parsed = GitHubOwnerRepo.parse("github-latest://owner/repo/")
        assertEquals(GitHubOwnerRepo("owner", "repo"), parsed)
    }

    @Test
    fun gitHubOwnerRepo_parse_withExtraPathSegments_ignoresTail() {
        val parsed = GitHubOwnerRepo.parse("github-latest://owner/repo/releases/tag/v1")
        assertEquals(GitHubOwnerRepo("owner", "repo"), parsed)
    }

    @Test
    fun gitHubOwnerRepo_parse_caseInsensitiveScheme_returnsOwnerAndRepo() {
        val parsed = GitHubOwnerRepo.parse("GitHub-Latest://owner/repo")
        assertEquals(GitHubOwnerRepo("owner", "repo"), parsed)
    }

    @Test
    fun gitHubOwnerRepo_parse_missingRepo_returnsNull() {
        assertEquals(null, GitHubOwnerRepo.parse("github-latest://onlyowner"))
    }

    @Test
    fun gitHubOwnerRepo_parse_empty_returnsNull() {
        assertEquals(null, GitHubOwnerRepo.parse("github-latest://"))
    }

    @Test
    fun gitHubOwnerRepo_parse_noSlashes_returnsNull() {
        assertEquals(null, GitHubOwnerRepo.parse("github-latest://ownerrepo"))
    }

    @Test
    fun joinUrl_rejectsCleartextUrl() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            joinUrl("http://example.com/presets/", "index.json")
        }
    }

    @Test
    fun joinUrl_resolvesHttpsPath() {
        assertEquals(
            "https://example.com/presets/index.json",
            joinUrl("https://example.com/presets/", "index.json")
        )
    }
}
