package anhiutangerinee.kittisu.security

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Fingerprint
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * Full-screen security gate. Renders the input matching the configured method,
 * shows cooldown countdown and lockdown warnings, and hosts the destructive-reset link.
 *
 * [biometricPrompt] is supplied by the host (needs a FragmentActivity) or null when
 * biometric unlock is unavailable/unsupported on this kernel+device combination.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.TwoTone.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (lockdown) {
                stringResource(R.string.security_lockdown_active)
            } else {
                stringResource(R.string.security_locked_title)
            },
            style = MaterialTheme.typography.titleLarge,
        )
        if (lockdown) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.security_lockdown_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(24.dp))

        CooldownGate { remaining ->
            val enabled = remaining == 0L
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (method) {
                    LockMethod.PATTERN -> PatternLockView(
                        modifier = Modifier.fillMaxWidth(0.7f),
                        enabled = enabled,
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
                        modifier = Modifier.width(280.dp),
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
                    )
                }
            }
        }

        errorKey?.let { key ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(key),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (biometricPrompt != null && !resetOnly) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { biometricPrompt.invoke() }) {
                Icon(Icons.TwoTone.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.security_biometric_unlock))
            }
        }

        Spacer(Modifier.height(32.dp))
        TextButton(onClick = { showResetDialogs = true }) {
            Text(
                stringResource(R.string.security_reset_link),
                color = MaterialTheme.colorScheme.outline,
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
        if (remaining > 0) {
            Spacer(Modifier.height(12.dp))
            val minutes = remaining / 60000
            val seconds = (remaining % 60000) / 1000
            Text(
                text = stringResource(
                    R.string.security_cooldown_remaining,
                    "%d:%02d".format(minutes, seconds),
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
            )
        }
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
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            label = { Text(stringResource(R.string.security_password_label)) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { onSubmit(value) }, enabled = enabled) {
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
        icon = { Icon(Icons.TwoTone.Lock, contentDescription = null) },
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
                    )
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
