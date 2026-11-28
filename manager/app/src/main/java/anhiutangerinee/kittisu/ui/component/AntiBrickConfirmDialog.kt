package anhiutangerinee.kittisu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.GppBad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.util.AntiBrickFinding
import anhiutangerinee.kittisu.ui.util.AntiBrickGuard
import kotlinx.coroutines.delay

/**
 * High-visibility Anti-Brick confirmation dialog with a strict 10-second countdown.
 * If the user does NOT explicitly confirm within 10 seconds, [onTimeoutOrCancel] is
 * invoked automatically and the dangerous command/script/module is STOPPED.
 */
@Composable
fun AntiBrickConfirmDialog(
    targetLabel: String,
    findings: List<AntiBrickFinding>,
    timeoutSeconds: Int = AntiBrickGuard.CONFIRM_TIMEOUT_SECONDS,
    onConfirm: () -> Unit,
    onTimeoutOrCancel: (timedOut: Boolean) -> Unit,
) {
    var secondsLeft by remember(targetLabel, findings) { mutableIntStateOf(timeoutSeconds) }

    LaunchedEffect(targetLabel, findings) {
        secondsLeft = timeoutSeconds
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
        onTimeoutOrCancel(true)
    }

    AlertDialog(
        onDismissRequest = { onTimeoutOrCancel(false) },
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Icon(
                imageVector = Icons.TwoTone.GppBad,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.anti_brick_dialog_title),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.anti_brick_dialog_desc, targetLabel, secondsLeft),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                LinearProgressIndicator(
                    progress = { (secondsLeft.toFloat() / timeoutSeconds.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0D1117),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        findings.take(12).forEachIndexed { idx, finding ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${idx + 1}. [${finding.source}:L${finding.lineNumber}] ${finding.reason}",
                                    color = Color(0xFFF87171),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 14.sp
                                )
                                Text(
                                    text = "> ${finding.snippet}",
                                    color = Color(0xFFE5E7EB),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                        if (findings.size > 12) {
                            Text(
                                text = "... +${findings.size - 12} more",
                                color = Color(0xFFFACC15),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(R.string.anti_brick_confirm_run, secondsLeft))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onTimeoutOrCancel(false) }) {
                Text(stringResource(R.string.anti_brick_stop))
            }
        }
    )
}
