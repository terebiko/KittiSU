package anhiutangerinee.kittisu.ui.screen

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.ModuleActionOutput
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import anhiutangerinee.kittisu.ui.util.showReplacingSnackbar
import anhiutangerinee.kittisu.ui.viewmodel.ExecuteModuleActionUiAction
import anhiutangerinee.kittisu.ui.viewmodel.ExecuteModuleActionUiEvent
import anhiutangerinee.kittisu.ui.viewmodel.ExecuteModuleActionViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ExecuteModuleActionScreen(moduleId: String, fromShortcut: Boolean) {
    val viewModel = koinViewModel<ExecuteModuleActionViewModel>(
        parameters = { parametersOf(moduleId) },
    )
    val moduleActionState by viewModel.state.collectAsStateWithLifecycle()
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val navigator = LocalNavigator.current

    LaunchedEffect(viewModel, fromShortcut) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ExecuteModuleActionUiEvent.Completed -> {
                    if (event.successful && fromShortcut) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.module_action_success),
                            Toast.LENGTH_SHORT,
                        ).show()
                        activity?.finishAndRemoveTask()
                    }
                }

                is ExecuteModuleActionUiEvent.LogSaved -> {
                    snackBarHost.showReplacingSnackbar("Log saved to ${event.path}")
                }

                is ExecuteModuleActionUiEvent.Error -> {
                    snackBarHost.showReplacingSnackbar(event.message)
                }
            }
        }
    }

    ModuleActionOutput(
        output = moduleActionState.output,
        running = moduleActionState.running,
        title = stringResource(R.string.action),
        onBack = { navigator.pop() },
        onSave = { viewModel.dispatch(ExecuteModuleActionUiAction.SaveLog) },
    )
}
