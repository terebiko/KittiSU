package anhiutangerinee.kittisu.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationCodecTest {

    @Test
    fun roundtrip_preservesAllFields() {
        val document = OperationDocument(
            state = OperationState.LOCKDOWN_ACTIVE,
            apps = listOf(GrantedAppSnapshot("com.example", 10042)),
            enabledModules = listOf("module_a", "module_b"),
            failedIds = listOf("module_c"),
            phase = "modules",
            bootId = "boot-123",
        )

        assertEquals(document, OperationCodec.decode(OperationCodec.encode(document)))
    }

    @Test
    fun decode_rejectsUnsupportedVersionAndState() {
        val valid = OperationDocument(OperationState.RESETTING, phase = "revoke")

        val wrongVersion = OperationCodec.encode(valid).replace("\"version\":1", "\"version\":2")
        assertTrue(wrongVersion != OperationCodec.encode(valid))
        assertThrows(IllegalArgumentException::class.java) { OperationCodec.decode(wrongVersion) }

        val wrongState = """{"version":1,"state":"SOMETHING_ELSE","apps":[],
            "enabledModules":[],"failedIds":[],"phase":"","bootId":""}"""
        assertThrows(IllegalArgumentException::class.java) { OperationCodec.decode(wrongState) }
    }

    @Test
    fun decode_rejectsGarbage() {
        assertThrows(IllegalArgumentException::class.java) { OperationCodec.decode("not json") }
    }

    @Test
    fun decode_acceptsLegacyIdleDocument() {
        val idle = """{"version":1,"state":"IDLE"}"""
        val decoded = OperationCodec.decode(idle)
        assertEquals(OperationState.IDLE, decoded.state)
        assertTrue(decoded.apps.isEmpty() && decoded.enabledModules.isEmpty())
    }
}
