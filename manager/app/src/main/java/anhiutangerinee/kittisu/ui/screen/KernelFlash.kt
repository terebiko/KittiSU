package anhiutangerinee.kittisu.ui.screen.kernelFlash

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.FlashOutputScreen
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import anhiutangerinee.kittisu.ui.util.showReplacingSnackbar
import anhiutangerinee.kittisu.ui.viewmodel.FlashState
import anhiutangerinee.kittisu.ui.viewmodel.FlashingStatus
import anhiutangerinee.kittisu.ui.viewmodel.KernelFlashUiAction
import anhiutangerinee.kittisu.ui.viewmodel.KernelFlashUiEvent
import anhiutangerinee.kittisu.ui.viewmodel.KernelFlashViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KernelFlashScreen(
    kernelUri: String,
    selectedSlot: String? = null,
    skipKsud: Boolean = false,
) {
    val context = LocalContext.current

    val snackBarHost = LocalSnackbarHost.current
    val viewModel = koinViewModel<KernelFlashViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val flashState = uiState.flash
    val horizonFlashComplete = stringResource(R.string.horizon_flash_complete)
    val logText = buildString {
        append(flashState.logs.joinToString("\n"))
        if (flashState.error.isNotEmpty()) append("\n${flashState.error}\n")
        if (flashState.isCompleted) append("\n$horizonFlashComplete\n\n\n")
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is KernelFlashUiEvent.Error -> if (event.message.isNotBlank()) {
                    snackBarHost.showReplacingSnackbar(event.message)
                }
            }
        }
    }

    // 开始刷写
    LaunchedEffect(kernelUri, selectedSlot, skipKsud) {
        viewModel.dispatch(KernelFlashUiAction.Start(kernelUri, selectedSlot, skipKsud))
    }

    LaunchedEffect(flashState.isCompleted, uiState.autoExit) {
        if (flashState.isCompleted && uiState.autoExit) {
            delay(1500.milliseconds)
            viewModel.dispatch(KernelFlashUiAction.ConsumeAutoExit)
            (context as? ComponentActivity)?.finish()
        }
    }

    val navigator = LocalNavigator.current

    BackHandler(flashState.isFlashing) {
        // deny
    }

    val commonState = FlashState(
        status = when {
            flashState.error.isNotEmpty() -> FlashingStatus.FAILED
            flashState.isCompleted -> FlashingStatus.SUCCESS
            else -> FlashingStatus.FLASHING
        },
        output = logText,
        showReboot = flashState.isCompleted,
        successTitleRes = R.string.kernel_flash_complete,
    )
    FlashOutputScreen(
        state = commonState,
        onBack = {
            if (!flashState.isFlashing) navigator.pop()
        },
        logFilePrefix = "KernelSU_kernel_flash_log",
        logContent = uiState.fullLog,
        onReboot = { viewModel.dispatch(KernelFlashUiAction.Reboot) },
    )
}
