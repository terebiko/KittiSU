package anhiutangerinee.kittisu.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import anhiutangerinee.kittisu.R

const val CUSTOM_MONOSPACE_FONT_KEY = "custom_monospace_font"

private val jetBrainsMono = FontFamily(Font(R.font.jetbrains_mono))

@Composable
fun rememberTerminalFontFamily(): FontFamily {
    val context = LocalContext.current
    return remember(context) {
        val useBundledFont = context
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(CUSTOM_MONOSPACE_FONT_KEY, false)
        if (useBundledFont) jetBrainsMono else FontFamily.Monospace
    }
}
