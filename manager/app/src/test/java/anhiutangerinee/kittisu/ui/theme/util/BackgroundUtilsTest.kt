package anhiutangerinee.kittisu.ui.theme.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundUtilsTest {
    @Test
    fun cropSize_matchesPortraitScreenWithoutGrowingSource() {
        assertEquals(562 to 1000, calculateCropSize(1600, 1000, 1080, 1920))
        assertEquals(900 to 1600, calculateCropSize(900, 1600, 1080, 1920))
    }
}
