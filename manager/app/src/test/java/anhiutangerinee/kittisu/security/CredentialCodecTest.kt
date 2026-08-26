package anhiutangerinee.kittisu.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialCodecTest {
    @Test
    fun hash_verifiesOriginalCredentialButNotWrongCredential() {
        val encoded = CredentialCodec.hash("correct horse battery staple".toCharArray())

        assertTrue(encoded.startsWith("kittisu-pbkdf2-sha256$1$210000$"))
        assertTrue(CredentialCodec.verify("correct horse battery staple".toCharArray(), encoded))
        assertFalse(CredentialCodec.verify("wrong".toCharArray(), encoded))
    }

    @Test
    fun verify_failsClosedForMalformedCredentials() {
        listOf(
            "",
            "plaintext",
            "kittisu-pbkdf2-sha256$2$210000$c2FsdA==$aGFzaA==",
            "kittisu-pbkdf2-sha256$1$209999$c2FsdA==$aGFzaA==",
            "kittisu-pbkdf2-sha256$1$210000$not-base64$not-base64",
        ).forEach { encoded ->
            assertFalse(encoded, CredentialCodec.verify("secret".toCharArray(), encoded))
        }
    }
}
