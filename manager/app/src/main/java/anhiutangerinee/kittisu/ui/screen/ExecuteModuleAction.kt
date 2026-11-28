package anhiutangerinee.kittisu.ui.screen

import android.annotation.SuppressLint
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.KeyEventBlocker
import anhiutangerinee.kittisu.ui.component.SwipeableSnackbarHost
import anhiutangerinee.kittisu.ui.component.settings.AppBackButton
import anhiutangerinee.kittisu.ui.navigation.DeepLinkResolver
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.navigation.ModuleDeepLink
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.blurEffect
import anhiutangerinee.kittisu.ui.theme.blurSource
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import anhiutangerinee.kittisu.ui.util.runModuleAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ExecuteModuleActionScreen(moduleId: String) {
    var text by rememberSaveable { mutableStateOf("") }
    var tempText : String
	val logContent = remember { StringBuilder() }	
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()
    var isActionRunning by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val activity = LocalActivity.current
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    BackHandler(enabled = isActionRunning) {
        // Disable back button if action is running
    }

    val fromShortcut = remember(activity) {
        DeepLinkResolver.parseModuleDeepLink(context, activity?.intent?.data) is ModuleDeepLink.Action
    }

    var antiBrickScanDone by remember(moduleId) { mutableStateOf(false) }
    var antiBrickApproved by remember(moduleId) { mutableStateOf(false) }
    var antiBrickBlocked by remember(moduleId) { mutableStateOf(false) }
    var antiBrickFindings by remember(moduleId) {
        mutableStateOf<List<anhiutangerinee.kittisu.ui.util.AntiBrickFinding>>(emptyList())
    }
    val blockedTimeoutMsg = stringResource(R.string.anti_brick_blocked_timeout)
    val blockedCancelMsg = stringResource(R.string.anti_brick_blocked_cancel)

    LaunchedEffect(moduleId) {
        if (!anhiutangerinee.kittisu.ui.util.AntiBrickGuard.isEnabled(context)) {
            antiBrickScanDone = true
            antiBrickApproved = true
            return@LaunchedEffect
        }
        val scan = withContext(Dispatchers.IO) {
            anhiutangerinee.kittisu.ui.util.AntiBrickGuard.scanFile("/data/adb/modules/$moduleId/action.sh")
        }
        antiBrickFindings = scan.findings
        if (!scan.isDangerous) {
            antiBrickApproved = true
        }
        antiBrickScanDone = true
    }

    if (antiBrickScanDone && antiBrickFindings.isNotEmpty() && !antiBrickApproved && !antiBrickBlocked) {
        anhiutangerinee.kittisu.ui.component.AntiBrickConfirmDialog(
            targetLabel = "$moduleId/action.sh",
            findings = antiBrickFindings,
            onConfirm = { antiBrickApproved = true },
            onTimeoutOrCancel = { timedOut ->
                antiBrickBlocked = true
                isActionRunning = false
                val msg = if (timedOut) blockedTimeoutMsg else blockedCancelMsg
                text = "[SovaSU Anti-Brick] $msg\n"
                logContent.append(text)
            }
        )
    }

    LaunchedEffect(antiBrickScanDone, antiBrickApproved) {
        if (!antiBrickScanDone || !antiBrickApproved || text.isNotEmpty()) {
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runModuleAction(
                moduleId = moduleId,
                antiBrickConfirmed = antiBrickFindings.isNotEmpty() && antiBrickApproved,
                onStdout = {
                    tempText = "$it\n"
                    if (tempText.startsWith("[H[J")) { // clear command
                        text = tempText.substring(6)
                    } else {
                        text += tempText
                    }
                    logContent.append(it).append("\n")
                },
                onStderr = {
                    logContent.append(it).append("\n")
                }
            ).let { actionResult ->
                if (actionResult && fromShortcut) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.module_action_success),
                            Toast.LENGTH_SHORT
                        ).show()

                        activity?.finishAndRemoveTask()
                    }
                }
            }
        }
        isActionRunning = false
    }

    val hasCustomBackdrop = ThemeConfig.isFullScreenBackgroundEnabled

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        if (hasCustomBackdrop) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.44f))
            )
        }

        Scaffold(
            topBar = {
                TopBar(
                    isActionRunning = isActionRunning,
                    onBack = {
                        navigator.pop()
                    },
                    onSave = {
                        if (!isActionRunning) {
                            scope.launch {
                                val format = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                                val date = format.format(Date())
                                val file = File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                    "KernelSU_module_action_log_${date}.log"
                                )
                                file.writeText(logContent.toString())
                                snackBarHost.showSnackbar("Log saved to ${file.absolutePath}")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                if (!isActionRunning) {
                    val navigator = LocalNavigator.current
                    ExtendedFloatingActionButton(
                        text = { Text(text = stringResource(R.string.close)) },
                        icon = { Icon(Icons.TwoTone.Close, contentDescription = null) },
                        onClick = {
                            navigator.pop()
                        }
                    )
                }
            },
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) }
        ) { innerPadding ->
            KeyEventBlocker {
                it.key == Key.VolumeDown || it.key == Key.VolumeUp
            }
            LaunchedEffect(text) {
                state.animateScrollToItem(1)
            }
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp
                    )
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .blurSource(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                    alpha = if (hasCustomBackdrop) 0.94f else 1f
                ),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 4.dp,
                shadowElevation = if (hasCustomBackdrop) 6.dp else 2.dp,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            ) {
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = text,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(64.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    isActionRunning: Boolean,
    onBack: () -> Unit = {},
    onSave: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior,
) {
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(
        ),
        title = { Text(stringResource(R.string.action)) },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            AppBackButton(
                onClick = onBack
            )
        },
        actions = {
            IconButton(
                onClick = onSave,
                enabled = !isActionRunning
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Save,
                    contentDescription = stringResource(id = R.string.save_log),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =
                if (ThemeConfig.isEnableBlur || ThemeConfig.isFullScreenBackgroundEnabled)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor =
                if (ThemeConfig.isEnableBlur || ThemeConfig.isFullScreenBackgroundEnabled)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer,
        ),
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp))
    )
}
