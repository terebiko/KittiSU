package anhiutangerinee.kittisu.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleDeepLinkTest {
    @Test
    fun validatesModuleIdsBeforeBuildingCommandsOrPaths() {
        assertTrue(isValidModuleId("valid.module-1"))
        assertFalse(isValidModuleId("../escape"))
        assertFalse(isValidModuleId("a/b"))
        assertFalse(isValidModuleId("a;touch_pwned"))
    }
}
