package anhiutangerinee.kittisu.security

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Fence
import androidx.compose.material.icons.twotone.Fingerprint
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Password
import androidx.compose.material.icons.twotone.Pattern
import androidx.compose.material.icons.twotone.Pin
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.settings.SegmentedColumn
import anhiutangerinee.kittisu.ui.component.settings.SettingsBaseWidget
import anhiutangerinee.kittisu.ui.component.settings.SettingsDropdownWidget
import anhiutangerinee.kittisu.ui.component.settings.SettingsJumpPageWidget
import anhiutangerinee.kittisu.ui.component.settings.SettingsSwitchWidget
import kotlinx.coroutines.launch

private val RELOCK_OPTIONS_MINUTES = listOf(0, 1, 5, 15)
private const val DEFAULT_MAX_ATTEMPTS = 5
private const val DEFAULT_RELOCK_MINUTES = 1

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun SecuritySettingsScreen() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    val store = remember { RootSecurityStore() }

    val currentConfig by produceState<LockConfig?>(initialValue = null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            when (val read = store.readConfig()) {
                is StoreRead.Valid -> read.value
                else -> null
            }
        }
    }
    var showMethodChooser by remember { mutableStateOf(false) }
    var setupMethod by remember { mutableStateOf<LockMethod?>(null) }
    var initialEnable by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.security_settings_title)) },
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                SegmentedColumn(
                    title = stringResource(R.string.security_settings_section),
                    content = {
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Lock,
                                title = stringResource(R.string.security_enable),
                                description = stringResource(R.string.security_enable_summary),
                                checked = currentConfig != null,
                                onCheckedChange = { checked ->
                                    if (checked && currentConfig == null) {
                                        initialEnable = true
                                        showMethodChooser = true
                                    } else if (!checked) {
                                        showDisableDialog = true
                                    }
                                },
                            )
                        }

                        currentConfig?.let { config ->
                            item {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Fence,
                                    title = stringResource(R.string.security_change_secret),
                                    description = stringResource(R.string.security_change_secret_summary),
                                    onClick = {
                                        initialEnable = false
                                        showMethodChooser = true
                                    },
                                )
                            }
                            item {
                                RelockTimeoutDropdown(config) { minutes ->
                                    scope.launch {
                                        ManagerSecurity.updateConfig {
                                            it.copy(relockTimeoutMillis = minutes * 60_000L)
                                        }
                                    }
                                }
                            }
                            item {
                                MaxAttemptsDropdown(config) { attempts ->
                                    scope.launch {
                                        ManagerSecurity.updateConfig {
                                            it.copy(maxFailedAttempts = attempts)
                                        }
                                    }
                                }
                            }
                            item {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Fingerprint,
                                    title = stringResource(R.string.security_biometric),
                                    description = stringResource(R.string.security_biometric_summary),
                                    checked = config.biometricEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            ManagerSecurity.updateConfig {
                                                it.copy(biometricEnabled = enabled)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            }

            item {
                SegmentedColumn(
                    title = stringResource(R.string.security_lockdown_section),
                    content = {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Shield,
                                title = stringResource(R.string.security_lockdown_info_title),
                                description = stringResource(R.string.security_lockdown_info_summary),
                                onClick = null,
                            ) {}
                        }
                    },
                )
            }
        }
    }

    if (showMethodChooser) {
        MethodChooserDialog(
            onDismiss = { showMethodChooser = false },
            onSelected = { method ->
                showMethodChooser = false
                setupMethod = method
            },
        )
    }

    setupMethod?.let { method ->
        SecretSetupDialog(
            method = method,
            title = stringResource(
                if (initialEnable) R.string.security_enable
                else R.string.security_change_secret
            ),
            onDismiss = { setupMethod = null },
            onCompleted = { encoded ->
                scope.launch {
                    if (initialEnable || currentConfig == null) {
                        ManagerSecurity.configureLock(
                            LockConfig(
                                method = method,
                                encodedCredential = encoded,
                                relockTimeoutMillis = DEFAULT_RELOCK_MINUTES * 60_000L,
                                maxFailedAttempts = DEFAULT_MAX_ATTEMPTS,
                                biometricEnabled = false,
                            )
                        )
                    } else {
                        ManagerSecurity.updateConfig {
                            it.copy(method = method, encodedCredential = encoded)
                        }
                    }
                    setupMethod = null
                }
            },
        )
    }

    if (showDisableDialog) {
        DisableLockDialog(
            onDismiss = { showDisableDialog = false },
            onConfirmed = {
                scope.launch { ManagerSecurity.disableLock() }
                showDisableDialog = false
            },
        )
    }
}

@Composable
private fun DisableLockDialog(onDismiss: () -> Unit, onConfirmed: () -> Unit) {
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.security_disable_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.security_disable_confirm_hint))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it; error = false },
                    singleLine = true,
                    isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val ok = ManagerSecurity.verifySecret(secret.toCharArray())
                    if (ok) {
                        error = false
                        onDismiss()
                        onConfirmed()
                    } else {
                        error = true
                    }
                }
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun MethodChooserDialog(onDismiss: () -> Unit, onSelected: (LockMethod) -> Unit) {
    val options = listOf(
        LockMethod.PASSWORD to R.string.security_method_password,
        LockMethod.PIN to R.string.security_method_pin,
        LockMethod.PATTERN to R.string.security_method_pattern,
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.security_choose_method)) },
        text = {
            androidx.compose.foundation.layout.Column {
                options.forEach { (method, labelRes) ->
                    SettingsBaseWidget(
                        icon = when (method) {
                            LockMethod.PASSWORD -> Icons.TwoTone.Password
                            LockMethod.PIN -> Icons.TwoTone.Pin
                            LockMethod.PATTERN -> Icons.TwoTone.Pattern
                        },
                        title = stringResource(labelRes),
                        onClick = { onSelected(method) },
                    ) {}
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun RelockTimeoutDropdown(config: LockConfig, onSelect: (Int) -> Unit) {
    val labels = RELOCK_OPTIONS_MINUTES.map {
        if (it == 0) stringResource(R.string.security_timeout_immediately)
        else stringResource(R.string.security_timeout_minutes, it)
    }
    var selectedIndex by remember(config.relockTimeoutMillis) {
        mutableIntStateOf(
            RELOCK_OPTIONS_MINUTES.indexOf((config.relockTimeoutMillis / 60000L).toInt())
                .coerceAtLeast(0)
        )
    }
    SettingsDropdownWidget(
        icon = Icons.TwoTone.Timer,
        title = stringResource(R.string.security_relock_timeout),
        items = labels,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index ->
            selectedIndex = index
            onSelect(RELOCK_OPTIONS_MINUTES[index])
        },
    )
}

@Composable
private fun MaxAttemptsDropdown(config: LockConfig, onSelect: (Int) -> Unit) {
    val attempts = (3..10).toList()
    val labels = attempts.map { stringResource(R.string.security_max_attempts_value, it) }
    var selectedIndex by remember(config.maxFailedAttempts) {
        mutableIntStateOf(attempts.indexOf(config.maxFailedAttempts).coerceAtLeast(0))
    }
    SettingsDropdownWidget(
        icon = Icons.TwoTone.Fence,
        title = stringResource(R.string.security_max_attempts),
        description = stringResource(R.string.security_max_attempts_summary),
        items = labels,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index ->
            selectedIndex = index
            onSelect(attempts[index])
        },
    )
}
