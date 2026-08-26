package anhiutangerinee.kittisu.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Material You numeric keypad for PIN entry, styled after the system lock screen:
 * round tonal keys, dot progress indicator, delete and confirm actions.
 */
@Composable
fun PinKeypad(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLength: Int = 12,
    onSubmit: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    fun append(digit: Char) {
        if (pin.length < maxLength) pin += digit
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Dot progress indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(16.dp),
        ) {
            repeat(maxOf(pin.length, 6).coerceAtMost(maxLength)) { index ->
                val active = index < pin.length
                Box(
                    modifier = Modifier
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        val keys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9')
        keys.chunked(3).forEach { rowKeys ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                rowKeys.forEach { digit ->
                    PinKey(
                        enabled = enabled,
                        onClick = { append(digit) },
                        content = {
                            Text(
                                text = digit.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // Invisible spacer keeps the 0 key centered like the system keypad.
            Box(modifier = Modifier.size(76.dp))
            PinKey(
                enabled = enabled,
                onClick = { append('0') },
                content = {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                },
            )
            PinKey(
                enabled = enabled && pin.isNotEmpty(),
                onClick = { pin = pin.dropLast(1) },
                content = {
                    Icon(
                        imageVector = Icons.AutoMirrored.TwoTone.Backspace,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PinKey(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(76.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
