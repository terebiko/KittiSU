package anhiutangerinee.kittisu.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SecurityJsonCodecTest {
    @Test
    fun config_roundTripsEveryLockMethod() {
        LockMethod.entries.forEach { method ->
            val config = LockConfig(
                method = method,
                encodedCredential = "kittisu-pbkdf2-sha256$1$210000$c2FsdHNhbHRzYWx0MTIzNA==$aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXNoaGFzaGhhc2g=",
                relockTimeoutMillis = 120_000,
                maxFailedAttempts = 5,
                biometricEnabled = true,
            )

            assertEquals(config, SecurityJson.decodeConfig(SecurityJson.encode(config)))
        }
    }

    @Test
    fun runtime_roundTripsPersistedCooldownAndLockdown() {
        val runtime = LockRuntime(
            failedAttempts = 7,
            cooldownLevel = 3,
            cooldownDeadlineMillis = 456_789,
            cooldownBootId = "boot-id",
            lockdown = true,
        )

        assertEquals(runtime, SecurityJson.decodeRuntime(SecurityJson.encode(runtime)))
    }

    @Test
    fun codecs_rejectUnsupportedMissingAndInvalidSchemas() {
        listOf(
            "{}",
            """{"version":2,"method":"PIN","encodedCredential":"hash","relockTimeoutMillis":0,"maxFailedAttempts":3,"biometricEnabled":false}""",
            """{"version":1,"method":"NONE","encodedCredential":"hash","relockTimeoutMillis":0,"maxFailedAttempts":3,"biometricEnabled":false}""",
            """{"version":1,"method":"PIN","encodedCredential":"plaintext","relockTimeoutMillis":0,"maxFailedAttempts":3,"biometricEnabled":false}""",
            """{"version":1,"method":"PIN","encodedCredential":"hash","relockTimeoutMillis":-1,"maxFailedAttempts":3,"biometricEnabled":false}""",
        ).forEach { json ->
            assertThrows(json, IllegalArgumentException::class.java) {
                SecurityJson.decodeConfig(json)
            }
        }

        listOf(
            "{}",
            """{"version":2,"failedAttempts":0,"cooldownLevel":0,"cooldownDeadlineMillis":0,"cooldownBootId":"","lockdown":false}""",
            """{"version":1,"failedAttempts":-1,"cooldownLevel":0,"cooldownDeadlineMillis":0,"cooldownBootId":"","lockdown":false}""",
            """{"version":1,"failedAttempts":0,"cooldownLevel":"zero","cooldownDeadlineMillis":0,"cooldownBootId":"","lockdown":false}""",
        ).forEach { json ->
            assertThrows(json, IllegalArgumentException::class.java) {
                SecurityJson.decodeRuntime(json)
            }
        }
    }
}
