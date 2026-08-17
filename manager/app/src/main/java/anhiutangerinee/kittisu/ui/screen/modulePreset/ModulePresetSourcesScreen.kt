package anhiutangerinee.kittisu.ui.screen.modulePreset

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.ConfirmResult
import anhiutangerinee.kittisu.ui.component.SwipeableSnackbarHost
import anhiutangerinee.kittisu.ui.component.rememberConfirmDialog
import anhiutangerinee.kittisu.ui.component.settings.AppBackButton
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.blurEffect
import anhiutangerinee.kittisu.ui.theme.blurSource
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import anhiutangerinee.kittisu.ui.util.module.PresetSource
import anhiutangerinee.kittisu.ui.viewmodel.ModulePresetViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulePresetSourcesScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = viewModel<ModulePresetViewModel>()
    val snackBarHost = LocalSnackbarHost.current
    val confirmDialog = rememberConfirmDialog()
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    val invalidUrlMsg = stringResource(R.string.preset_invalid_url)
    val deleteSourceTitle = stringResource(R.string.preset_delete_source)

    LaunchedEffect(Unit) {
        if (viewModel.sources.isEmpty()) viewModel.loadSources(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.preset_sources)) },
                navigationIcon = {
                    AppBackButton(onClick = { navigator.pop() })
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (ThemeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                    scrolledContainerColor = if (ThemeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.TwoTone.Add, contentDescription = stringResource(R.string.preset_add_source))
            }
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .blurSource(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding(),
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 80.dp
            )
        ) {
            items(viewModel.sources, key = { it.id }) { src ->
                SourceRow(
                    source = src,
                    deleteTitle = deleteSourceTitle,
                    onDelete = {
                        scope.launch {
                            val r = confirmDialog.awaitConfirm(
                                title = deleteSourceTitle,
                                content = src.baseUrl
                            )
                            if (r == ConfirmResult.Confirmed) {
                                viewModel.removeCustomSource(src.id)
                                viewModel.refreshPresets(context)
                            }
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showAdd) {
        AddSourceDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, url ->
                showAdd = false
                if (viewModel.addCustomSource(name, url)) {
                    viewModel.refreshPresets(context)
                } else {
                    scope.launch { snackBarHost.showSnackbar(invalidUrlMsg) }
                }
            }
        )
    }
}

@Composable
private fun SourceRow(source: PresetSource, deleteTitle: String, onDelete: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = source.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = source.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (source.isOfficial) {
                PresetBadge(
                    text = stringResource(R.string.preset_official),
                    color = MaterialTheme.colorScheme.secondaryContainer
                )
            } else {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.TwoTone.Delete,
                        contentDescription = stringResource(R.string.preset_delete_source),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSourceDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    // ponytail: require http(s) scheme + non-empty host; final check happens in VM.
    val valid = url.startsWith("http://") || url.startsWith("https://")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_add_source)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.preset_source_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.preset_source_url)) },
                    singleLine = true,
                    isError = url.isNotBlank() && !(url.startsWith("http://") || url.startsWith("https://")),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, url) }, enabled = valid) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
