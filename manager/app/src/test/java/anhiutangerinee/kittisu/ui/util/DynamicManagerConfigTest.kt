package anhiutangerinee.kittisu.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DynamicManagerConfigTest {
    @Test
    fun parsesKsudOutputAndRejectsInvalidValues() {
        val hash = "a".repeat(64)
        val config = parseDynamicManagerConfig("size: 912, hash: $hash")

        assertEquals(912, config?.size)
        assertEquals(hash, config?.hash)
        assertNull(parseDynamicManagerConfig("size: invalid, hash: $hash"))
        assertNull(parseDynamicManagerConfig("size: 912, hash: short"))
    }
}
