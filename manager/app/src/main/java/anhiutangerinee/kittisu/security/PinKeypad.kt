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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Backspace
import androidx.compose.material.icons.twotone.CheckCircle
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Material You numeric keypad for PIN entry, mirroring the SystemUI bouncer
 * (NumPadKey): round tonal keys with klondike letter hints, dot progress
 * indicator, delete + explicit confirm actions and per-key haptics.
 * The buffer clears after every submit so retries never reuse stale input.
 */
@Composable
fun PinKeypad(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLength: Int = 12,
    onSubmit: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    fun submit(current: String) {
        if (current.isEmpty()) return
        onSubmit(current)
        pin = ""
    }

    fun append(digit: Char) {
        if (pin.length >= maxLength) return
        pin += digit
        // Auto-confirm fixed-length PINs, like the system bouncer auto confirm.
        if (pin.length == maxLength) submit(pin)
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
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            append(digit)
                        },
                        content = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = digit.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center,
                                )
                                // AOSP "klondike" letter hints (NumPadKey).
                                KLONDIKE[digit]?.let { letters ->
                                    Text(
                                        text = letters,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            PinKey(
                enabled = enabled && pin.isNotEmpty(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    submit(pin)
                },
                content = {
                    Icon(
                        imageVector = Icons.TwoTone.CheckCircle,
                        contentDescription = null,
                        tint = if (pin.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            PinKey(
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    append('0')
                },
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
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    pin = pin.dropLast(1)
                },
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

/** AOSP lockscreen_num_pad_klondike: letter hints shown under each digit. */
private val KLONDIKE = mapOf(
    '2' to "ABC",
    '3' to "DEF",
    '4' to "GHI",
    '5' to "JKL",
    '6' to "MNO",
    '7' to "PQRS",
    '8' to "TUV",
    '9' to "WXYZ",
)
