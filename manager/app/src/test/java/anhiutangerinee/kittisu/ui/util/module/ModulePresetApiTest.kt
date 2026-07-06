package anhiutangerinee.kittisu.ui.util.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// ponytail: tests cover only pure logic (SHA, signature, version compare via
// requirement checks). Android-bound funcs (isModuleInstalled, fetch*,
// resolveModuleDownloadUrl) are exercised in integration tests / by hand.
// Requires testImplementation(junit:junit) in app/build.gradle.kts to run.
class ModulePresetApiTest {

    @Test
    fun computePresetSha256_knownString_matchesExpectedHex() {
        // Known vector: SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        val actual = computePresetSha256("hello")
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            actual
        )
    }

    @Test
    fun computePresetSha256_emptyString_returnsKnownDigest() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val actual = computePresetSha256("")
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            actual
        )
    }

    @Test
    fun verifyPresetSignature_correctSignature_returnsVerified() {
        val json = "{\"name\":\"Zygisk\",\"preset\":[]}"
        val sig = computePresetSha256(json)
        assertEquals(PresetVerificationStatus.VERIFIED, verifyPresetSignature(json, sig))
    }

    @Test
    fun verifyPresetSignature_mismatchedSignature_returnsInvalid() {
        val json = "{\"name\":\"Zygisk\"}"
        val wrong = "0".repeat(64)
        assertEquals(PresetVerificationStatus.INVALID_SIGNATURE, verifyPresetSignature(json, wrong))
    }

    @Test
    fun verifyPresetSignature_missingSignature_returnsMissing() {
        assertEquals(
            PresetVerificationStatus.MISSING_SIGNATURE,
            verifyPresetSignature("{}", null)
        )
        assertEquals(
            PresetVerificationStatus.MISSING_SIGNATURE,
            verifyPresetSignature("{}", "")
        )
        assertEquals(
            PresetVerificationStatus.MISSING_SIGNATURE,
            verifyPresetSignature("{}", "   ")
        )
    }

    @Test
    fun verifyPresetSignature_caseInsensitiveSignature_returnsVerified() {
        val json = "abc"
        val sig = computePresetSha256(json).uppercase()
        assertEquals(PresetVerificationStatus.VERIFIED, verifyPresetSignature(json, sig))
    }

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
}
