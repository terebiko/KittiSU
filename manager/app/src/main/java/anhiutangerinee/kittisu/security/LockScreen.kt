package anhiutangerinee.kittisu.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Fingerprint
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import anhiutangerinee.kittisu.R
import kotlinx.coroutines.delay

/**
 * Material You full-screen security gate. Renders the input matching the configured
 * method inside a tonal rounded container, shows cooldown as a pill and hosts the
 * destructive-reset link.
 *
 * [biometricPrompt] is supplied by the host (needs a FragmentActivity) or null when
 * biometric unlock is unavailable.
 */
@Composable
fun LockScreen(
    method: LockMethod,
    lockdown: Boolean,
    resetOnly: Boolean = false,
    biometricPrompt: (() -> Unit)? = null,
    onVerified: (CharArray) -> Unit,
    onPatternVerified: (String) -> Unit,
    onDestructiveReset: () -> Unit,
) {
    var errorKey by remember { mutableStateOf<Int?>(null) }
    var showResetDialogs by remember { mutableStateOf(false) }
    // Surface verification feedback (wrong secret / cooldown / restore failure).
    val remoteError by ManagerSecurity.lastMessageKey.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Hero icon inside a tonal circle, like system credential prompts.
        Surface(
            shape = CircleShape,
            color = if (lockdown) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.TwoTone.Lock,
                    contentDescription = null,
                    tint = if (lockdown) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (lockdown) {
                stringResource(R.string.security_lockdown_active)
            } else {
                stringResource(R.string.security_locked_title)
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (lockdown) stringResource(R.string.security_lockdown_hint)
            else stringResource(R.string.security_locked_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = if (lockdown) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.85f),
        )
        Spacer(Modifier.height(28.dp))

        CooldownGate { remaining ->
            val enabled = remaining == 0L
            // Tonal rounded container hosting the active credential input.
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(
                        vertical = if (method == LockMethod.PIN || method == LockMethod.PATTERN) 24.dp else 20.dp,
                        horizontal = if (method == LockMethod.PASSWORD) 16.dp else 12.dp,
                    ),
                ) {
                    when (method) {
                        LockMethod.PATTERN -> PatternLockView(
                            modifier = Modifier.fillMaxWidth(0.62f),
                            enabled = enabled,
                            errorColor = if (errorKey != null) MaterialTheme.colorScheme.error
                            else androidx.compose.ui.graphics.Color.Unspecified,
                            onPatternCompleted = { dots ->
                                if (dots.size < 4) {
                                    errorKey = R.string.security_pattern_too_short
                                } else {
                                    errorKey = null
                                    onPatternVerified(dots.joinToString(","))
                                }
                            },
                        )

                        LockMethod.PIN -> PinKeypad(
                            modifier = Modifier.width(300.dp),
                            enabled = enabled,
                            onSubmit = { pin ->
                                if (pin.length < 6) {
                                    errorKey = R.string.security_pin_too_short
                                } else {
                                    errorKey = null
                                    onVerified(pin.toCharArray())
                                }
                            },
                        )

                        LockMethod.PASSWORD -> PasswordField(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            onSubmit = { password ->
                                if (password.isEmpty()) return@PasswordField
                                errorKey = null
                                onVerified(password.toCharArray())
                            },
                        )                    }
                }
            }

            errorKey?.let { key ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(key),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            remoteError?.let { key ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(id = messageResForKey(key)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (remaining > 0) {
                Spacer(Modifier.height(14.dp))
                CooldownPill(remaining)
            }
        }

        if (biometricPrompt != null && !resetOnly) {
            Spacer(Modifier.height(22.dp))
            FilledTonalIconButton(
                onClick = { biometricPrompt.invoke() },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Fingerprint,
                    contentDescription = stringResource(R.string.security_biometric_unlock),
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        TextButton(onClick = { showResetDialogs = true }) {
            Text(
                stringResource(R.string.security_reset_link),
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }

    if (showResetDialogs) {
        DestructiveResetDialogs(
            onDismiss = { showResetDialogs = false },
            onConfirmed = {
                showResetDialogs = false
                onDestructiveReset()
            },
        )
    }
}

/** Blocks the inputs while a cooldown is active and ticks the countdown. */
@Composable
private fun CooldownGate(content: @Composable (Long) -> Unit) {
    var remaining by remember { mutableLongStateOf(ManagerSecurity.cooldownRemainingMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            remaining = ManagerSecurity.cooldownRemainingMs()
            delay(if (remaining > 0) 500L else 1500L)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content(remaining)
    }
}

/** Error-container pill showing the remaining cooldown time. */
@Composable
private fun CooldownPill(remainingMs: Long) {
    val minutes = remainingMs / 60000
    val seconds = (remainingMs % 60000) / 1000
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = stringResource(
                R.string.security_cooldown_remaining,
                "%d:%02d".format(minutes, seconds),
            ),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PasswordField(modifier: Modifier, enabled: Boolean, onSubmit: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            enabled = enabled,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            label = { Text(stringResource(R.string.security_password_label)) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Button(
            shape = RoundedCornerShape(16.dp),
            onClick = {
                onSubmit(value)
                value = "" // never reuse stale input on retry
            },
            enabled = enabled && value.isNotEmpty(),
        ) {
            Text(stringResource(android.R.string.ok))
        }
    }
}

/** Two consecutive confirmation dialogs guarding the destructive reset. */
@Composable
fun DestructiveResetDialogs(onDismiss: () -> Unit, onConfirmed: () -> Unit) {
    var stage by remember { mutableStateOf(1) }
    var confirmEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(stage) {
        if (stage == 1) {
            confirmEnabled = true
        } else {
            // Second warning requires a deliberate wait to avoid accidental taps.
            confirmEnabled = false
            delay(3000)
            confirmEnabled = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp),
                )
            }
        },
        title = { Text(stringResource(R.string.security_reset_warning_title)) },
        text = {
            Text(
                if (stage == 1) stringResource(R.string.security_reset_warning_1)
                else stringResource(R.string.security_reset_warning_2)
            )
        },
        confirmButton = {
            TextButton(enabled = confirmEnabled, onClick = {
                if (stage == 1) stage = 2 else onConfirmed()
            }) {
                Text(
                    stringResource(
                        if (stage == 1) android.R.string.ok else R.string.security_reset_confirm
                    ),
                    color = if (stage == 2) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

fun messageResForKey(key: String): Int = when (key) {
    "security_restoring" -> R.string.security_restoring
    "security_lockdown_entering" -> R.string.security_lockdown_entering
    "security_restore_failed" -> R.string.security_restore_failed
    "security_reset_failed" -> R.string.security_reset_failed
    "security_wrong_secret" -> R.string.security_wrong_secret
    "security_cooldown_active" -> R.string.security_cooldown_active
    "security_biometric_failed" -> R.string.security_biometric_failed
    else -> R.string.operation_failed
}

@Composable
fun WorkingOverlay(messageKey: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(id = messageResForKey(messageKey)))
        }
    }
}
