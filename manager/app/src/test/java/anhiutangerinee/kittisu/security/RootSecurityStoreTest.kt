package anhiutangerinee.kittisu.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RootSecurityStoreTest {
    @Test
    fun readConfig_distinguishesMissingValidAndCorrupt() {
        val config = LockConfig(
            LockMethod.PIN,
            "kittisu-pbkdf2-sha256$1$210000$c2FsdHNhbHRzYWx0MTIzNA==$aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXNoaGFzaGhhc2g=",
            0,
            3,
            false,
        )
        val validJson = SecurityJson.encode(config)

        assertEquals(StoreRead.Missing, storeReturning(RootCommandResult(MISSING_EXIT_CODE)).readConfig())
        assertEquals(StoreRead.Valid(config), storeReturning(RootCommandResult(0, validJson)).readConfig())
        assertTrue(storeReturning(RootCommandResult(0, "{}")).readConfig() is StoreRead.Corrupt)
        assertTrue(storeReturning(RootCommandResult(1, "")).readConfig() is StoreRead.Corrupt)
        assertTrue(RootSecurityStore { error("root unavailable") }.readConfig() is StoreRead.Corrupt)
    }

    @Test
    fun writeRuntime_usesProtectedSameDirectoryAtomicReplacementAndQuotesPayload() {
        val commands = mutableListOf<String>()
        val store = RootSecurityStore { command ->
            commands += command
            RootCommandResult(0)
        }
        val runtime = LockRuntime(cooldownBootId = "boot'; touch /tmp/pwned")

        assertTrue(store.writeRuntime(runtime))

        val command = commands.single()
        assertTrue(command.contains("umask 077"))
        assertTrue(command.contains("mkdir -p '/data/adb/ksu/.kittisu_manager_lock'"))
        assertTrue(command.contains("chown 0:0 '/data/adb/ksu/.kittisu_manager_lock'"))
        assertTrue(command.contains("chmod 0700 '/data/adb/ksu/.kittisu_manager_lock'"))
        assertTrue(command.contains("mktemp '/data/adb/ksu/.kittisu_manager_lock/runtime.json.tmp.XXXXXX'"))
        assertTrue(command.contains("chmod 0600 \"\$tmp\""))
        assertTrue(command.contains("mv -f \"\$tmp\" '/data/adb/ksu/.kittisu_manager_lock/runtime.json'"))
        assertTrue(command.contains("'\\''; touch /tmp/pwned"))
        assertTrue(!RootSecurityStore { error("root unavailable") }.writeRuntime(runtime))
    }

    @Test
    fun operationStorage_requiresVersionOneAndUsesOperationPath() {
        val commands = mutableListOf<String>()
        val store = RootSecurityStore { command ->
            commands += command
            RootCommandResult(0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            store.writeOperation("""{"version":2}""")
        }
        assertTrue(store.writeOperation("""{"version":1,"pending":false}"""))
        assertTrue(commands.single().contains("/operation.json"))
        assertTrue(storeReturning(RootCommandResult(0, """{"version":2}""")).readOperation() is StoreRead.Corrupt)
    }

    private fun storeReturning(result: RootCommandResult) = RootSecurityStore { result }
}
