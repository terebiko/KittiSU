package anhiutangerinee.kittisu.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellQuoteTest {
    @Test
    fun shellQuote_escapesSingleQuotes() {
        assertEquals("'a'\\''b; touch /tmp/pwned'", "a'b; touch /tmp/pwned".shellQuote())
    }
}
