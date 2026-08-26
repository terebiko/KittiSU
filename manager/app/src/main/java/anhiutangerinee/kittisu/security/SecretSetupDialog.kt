package anhiutangerinee.kittisu.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import anhiutangerinee.kittisu.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Two-step secret entry (set + confirm). Produces the encoded PBKDF2 credential
 * ready to persist inside [LockConfig].
 */
@Composable
fun SecretSetupDialog(
    method: LockMethod,
    title: String,
    onDismiss: () -> Unit,
    onCompleted: suspend (encodedCredential: String) -> Unit,
) {
    var stage by remember { mutableStateOf(1) }
    var textInput by remember { mutableStateOf("") }
    var confirmedText by remember { mutableStateOf("") }
    var firstPattern by remember { mutableStateOf<String?>(null) }
    var errorKey by remember { mutableStateOf<Int?>(null) }

    fun validateText(value: String): Int? = when {
        value.isEmpty() -> R.string.security_secret_empty
        method == LockMethod.PIN && (value.length < 6 || value.any { !it.isDigit() }) ->
            R.string.security_pin_too_short
        method == LockMethod.PASSWORD && value.length < 8 ->
            R.string.security_password_too_short
        else -> null
    }

    val keyboard = KeyboardOptions(
        keyboardType = if (method == LockMethod.PIN) KeyboardType.NumberPassword else KeyboardType.Password
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (method == LockMethod.PATTERN) {
                    Text(
                        text = stringResource(
                            if (stage == 1) R.string.security_setup_enter
                            else R.string.security_setup_confirm
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    PatternLockView(
                        modifier = Modifier.fillMaxWidth(0.7f),
                        onPatternCompleted = { dots ->
                            val pattern = dots.joinToString(",")
                            when {
                                dots.size < 4 -> errorKey = R.string.security_pattern_too_short
                                stage == 1 -> {
                                    firstPattern = pattern
                                    stage = 2
                                    errorKey = null
                                }

                                else -> {
                                    if (pattern == firstPattern) {
                                        // Completion handled below via LaunchedEffect.
                                        textInput = pattern
                                        confirmedText = pattern
                                    } else {
                                        errorKey = R.string.security_setup_mismatch
                                        stage = 1
                                        firstPattern = null
                                    }
                                }
                            }
                        },
                    )
                } else {
                    Text(
                        text = stringResource(
                            if (stage == 1) R.string.security_setup_enter
                            else R.string.security_setup_confirm
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = if (stage == 1) textInput else confirmedText,
                        onValueChange = {
                            if (stage == 1) textInput = it else confirmedText = it
                            errorKey = null
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = keyboard,
                        isError = errorKey != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                errorKey?.let { key ->
                    Text(
                        text = stringResource(key),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = method != LockMethod.PATTERN &&
                    (if (stage == 1) validateText(textInput) == null else confirmedText.isNotEmpty()),
                onClick = {
                    if (method == LockMethod.PATTERN) return@Button
                    if (stage == 1) {
                        validateText(textInput)?.let { errorKey = it } ?: run { stage = 2 }
                    } else if (confirmedText != textInput) {
                        errorKey = R.string.security_setup_mismatch
                        stage = 1
                        confirmedText = ""
                    }
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )

    // Text methods: OK pressed with matching inputs.
    if (method != LockMethod.PATTERN && stage == 2 && confirmedText == textInput &&
        textInput.isNotEmpty()
    ) {
        CompleteSetup(secret = textInput, onCompleted = onCompleted)
    }

    // Pattern method: second draw matched.
    if (method == LockMethod.PATTERN && textInput.isNotEmpty() && textInput == confirmedText) {
        CompleteSetup(secret = textInput, onCompleted = onCompleted)
    }
}

@Composable
private fun CompleteSetup(secret: String, onCompleted: suspend (String) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(secret) {
        val encoded = withContext(Dispatchers.Default) {
            CredentialCodec.hash(secret.toCharArray())
        }
        onCompleted(encoded)
    }
}
