package anhiutangerinee.kittisu.ui.theme

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicColorPreferencesTest {
    @Test
    fun savedValuesAndInvalidFallbacksAreStable() {
        assertEquals(PaletteStyle.Vibrant, parseDynamicPaletteStyle("Vibrant"))
        assertEquals(PaletteStyle.TonalSpot, parseDynamicPaletteStyle("invalid"))
        assertEquals(ColorSpec.SpecVersion.SPEC_2025, parseDynamicColorSpec("SPEC_2025"))
        assertEquals(ColorSpec.SpecVersion.SPEC_2021, parseDynamicColorSpec(null))
    }
}
