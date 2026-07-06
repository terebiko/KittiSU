package anhiutangerinee.kittisu.ui.screen.modulePreset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.SwipeableSnackbarHost
import anhiutangerinee.kittisu.ui.component.settings.AppBackButton
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.blurEffect
import anhiutangerinee.kittisu.ui.theme.blurSource
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import anhiutangerinee.kittisu.ui.util.module.LoadedPreset
import anhiutangerinee.kittisu.ui.util.module.PresetEntry
import anhiutangerinee.kittisu.ui.util.module.PresetModule
import anhiutangerinee.kittisu.ui.util.module.PresetRequirement
import anhiutangerinee.kittisu.ui.viewmodel.ModulePresetViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulePresetEditorScreen(preset: LoadedPreset?) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = viewModel<ModulePresetViewModel>()
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val isEdit = preset != null
    val isLocal = preset?.isLocal == true
    val isReadOnly = isEdit && !isLocal

    // ponytail: keep original id so we can delete the old file when id changes on save.
    val originalId = remember { preset?.presetEntry?.id.orEmpty() }

    var presetId by remember { mutableStateOf(preset?.presetEntry?.id.orEmpty()) }
    var destination by remember { mutableStateOf(preset?.presetEntry?.destination.orEmpty()) }
    var author by remember { mutableStateOf(preset?.presetEntry?.author.orEmpty()) }
    var committer by remember { mutableStateOf(preset?.presetEntry?.committer.orEmpty()) }
    var team by remember { mutableStateOf(preset?.presetEntry?.team.orEmpty()) }
    var requiresRebootAtEnd by remember { mutableStateOf(preset?.presetEntry?.requiresRebootAtEnd == true) }
    val modules = remember { mutableStateListOf<PresetModule>().apply { addAll(preset?.presetEntry?.modules.orEmpty()) } }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showAddModule by remember { mutableStateOf(false) }

    val idRequiredMsg = stringResource(R.string.preset_id_required)
    val destinationRequiredMsg = stringResource(R.string.preset_destination_required)
    val savedMsg = stringResource(R.string.preset_saved)
    val idDuplicateMsg = stringResource(R.string.preset_id_exists)
    val moduleIdDuplicateMsg = stringResource(R.string.preset_module_duplicate_id)

    fun validateAndSave() {
        val trimmedId = presetId.trim()
        if (trimmedId.isBlank()) {
            scope.launch { snackBarHost.showSnackbar(idRequiredMsg) }
            return
        }
        if (destination.isBlank()) {
            scope.launch { snackBarHost.showSnackbar(destinationRequiredMsg) }
            return
        }
        if (!isEdit) {
            val clash = viewModel.presets.any { it.isLocal && it.presetEntry.id == trimmedId }
            if (clash) {
                scope.launch { snackBarHost.showSnackbar(idDuplicateMsg) }
                return
            }
        } else if (trimmedId != originalId) {
            val clash = viewModel.presets.any { it.isLocal && it.presetEntry.id == trimmedId }
            if (clash) {
                scope.launch { snackBarHost.showSnackbar(idDuplicateMsg) }
                return
            }
        }
        val entry = PresetEntry(
            id = trimmedId,
            destination = destination.trim(),
            author = author.trim().ifBlank { null },
            committer = committer.trim().ifBlank { null },
            team = team.trim().ifBlank { null },
            requiresRebootAtEnd = requiresRebootAtEnd,
            modules = modules.toList()
        )
        scope.launch {
            if (isEdit && trimmedId != originalId && originalId.isNotBlank()) {
                // ponytail: delete is suspend and awaited before create so the old
                // file is gone before the new one is written.
                viewModel.deleteLocalPreset(context, originalId)
            }
            viewModel.createLocalPreset(context, entry)
            snackBarHost.showSnackbar(savedMsg)
            navigator.pop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.blurEffect(),
                title = {
                    Text(
                        text = stringResource(
                            if (isEdit) R.string.preset_edit else R.string.preset_create
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                actions = {
                    TextButton(onClick = { validateAndSave() }, enabled = !isReadOnly) {
                        Text(stringResource(R.string.preset_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (ThemeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                    scrolledContainerColor = if (ThemeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha)
                )
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .blurSource(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding(),
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            )
        ) {
            if (isReadOnly) {
                item {
                    ReadOnlyWarning()
                    Spacer(Modifier.height(12.dp))
                }
            }
            item {
                PresetInfoCard(
                    presetId = presetId,
                    onPresetIdChange = { presetId = it },
                    destination = destination,
                    onDestinationChange = { destination = it },
                    author = author,
                    onAuthorChange = { author = it },
                    committer = committer,
                    onCommitterChange = { committer = it },
                    team = team,
                    onTeamChange = { team = it },
                    requiresRebootAtEnd = requiresRebootAtEnd,
                    onRequiresRebootChange = { requiresRebootAtEnd = it },
                    enabled = !isReadOnly
                )
                Spacer(Modifier.height(16.dp))
            }
            item {
                ModulesHeader(count = modules.size)
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(modules, key = { i, m -> "$i:${m.moduleId}" }) { index, m ->
                ModuleRow(
                    module = m,
                    enabled = !isReadOnly,
                    canMoveUp = index > 0,
                    canMoveDown = index < modules.size - 1,
                    onEdit = { editingIndex = index },
                    onDelete = { modules.removeAt(index) },
                    onMoveUp = {
                        if (index > 0) {
                            val item = modules.removeAt(index)
                            modules.add(index - 1, item)
                        }
                    },
                    onMoveDown = {
                        if (index < modules.size - 1) {
                            val item = modules.removeAt(index)
                            modules.add(index + 1, item)
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
            item {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showAddModule = true },
                    enabled = !isReadOnly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.preset_add_module))
                }
            }
        }
    }

    if (showAddModule) {
        ModuleEditDialog(
            initial = null,
            excludeIndex = null,
            existingModules = modules,
            onDuplicateId = { scope.launch { snackBarHost.showSnackbar(moduleIdDuplicateMsg) } },
            onDismiss = { showAddModule = false },
            onSave = { m ->
                modules.add(m)
                showAddModule = false
            }
        )
    }
    editingIndex?.let { idx ->
        ModuleEditDialog(
            initial = modules[idx],
            excludeIndex = idx,
            existingModules = modules,
            onDuplicateId = { scope.launch { snackBarHost.showSnackbar(moduleIdDuplicateMsg) } },
            onDismiss = { editingIndex = null },
            onSave = { m ->
                modules[idx] = m
                editingIndex = null
            }
        )
    }
}

@Composable
private fun ReadOnlyWarning() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.preset_read_only),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PresetInfoCard(
    presetId: String,
    onPresetIdChange: (String) -> Unit,
    destination: String,
    onDestinationChange: (String) -> Unit,
    author: String,
    onAuthorChange: (String) -> Unit,
    committer: String,
    onCommitterChange: (String) -> Unit,
    team: String,
    onTeamChange: (String) -> Unit,
    requiresRebootAtEnd: Boolean,
    onRequiresRebootChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            OutlinedTextField(
                value = presetId,
                onValueChange = onPresetIdChange,
                label = { Text(stringResource(R.string.preset_id)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = destination,
                onValueChange = onDestinationChange,
                label = { Text(stringResource(R.string.preset_destination)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = author,
                onValueChange = onAuthorChange,
                label = { Text(stringResource(R.string.preset_author)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = committer,
                onValueChange = onCommitterChange,
                label = { Text(stringResource(R.string.preset_committer)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = team,
                onValueChange = onTeamChange,
                label = { Text(stringResource(R.string.preset_team)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.preset_requires_reboot),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = requiresRebootAtEnd, onCheckedChange = onRequiresRebootChange, enabled = enabled)
            }
        }
    }
}

@Composable
private fun ModulesHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.preset_modules_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.preset_modules_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModuleRow(
    module: PresetModule,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.moduleName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = module.moduleId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = module.directUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onMoveUp, enabled = enabled && canMoveUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = null)
                }
                IconButton(onClick = onMoveDown, enabled = enabled && canMoveDown) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = null)
                }
                IconButton(onClick = onEdit, enabled = enabled) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.preset_module_name))
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.preset_remove_module),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModuleEditDialog(
    initial: PresetModule?,
    excludeIndex: Int?,
    existingModules: List<PresetModule>,
    onDuplicateId: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (PresetModule) -> Unit
) {
    var moduleName by remember { mutableStateOf(initial?.moduleName.orEmpty()) }
    var moduleId by remember { mutableStateOf(initial?.moduleId.orEmpty()) }
    // ponytail: track the editing index so duplicate check can exclude the row being edited.
    val editingIdx = excludeIndex
    var moduleVersion by remember { mutableStateOf(initial?.moduleVersion.orEmpty()) }
    var directUrl by remember { mutableStateOf(initial?.directUrl ?: "repo") }
    var stopIfFail by remember { mutableStateOf(initial?.stopIfFail ?: true) }
    var rebootAfter by remember { mutableStateOf(initial?.rebootAfter ?: false) }
    var dependsOnText by remember { mutableStateOf(initial?.dependsOn?.joinToString(", ").orEmpty()) }
    var reqSusfs by remember { mutableStateOf(initial?.requirement?.susfs.orEmpty()) }
    var reqKernelsu by remember { mutableStateOf(initial?.requirement?.kernelsu.orEmpty()) }
    var reqAndroid by remember { mutableStateOf(initial?.requirement?.android.orEmpty()) }
    var reqMetadata by remember { mutableStateOf(initial?.requirement?.metadata.orEmpty()) }

    val canSave = moduleName.isNotBlank() && moduleId.isNotBlank() && directUrl.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.preset_add_module)) },
        text = {
            LazyColumn {
                item {
                    OutlinedTextField(
                        value = moduleName,
                        onValueChange = { moduleName = it },
                        label = { Text(stringResource(R.string.preset_module_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = moduleId,
                        onValueChange = { moduleId = it },
                        label = { Text(stringResource(R.string.preset_module_id)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = moduleVersion,
                        onValueChange = { moduleVersion = it },
                        label = { Text(stringResource(R.string.preset_module_version)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = directUrl,
                        onValueChange = { directUrl = it },
                        label = { Text(stringResource(R.string.preset_module_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        label = stringResource(R.string.preset_module_stop_if_fail),
                        checked = stopIfFail,
                        onCheckedChange = { stopIfFail = it }
                    )
                    SwitchRow(
                        label = stringResource(R.string.preset_module_reboot_after),
                        checked = rebootAfter,
                        onCheckedChange = { rebootAfter = it }
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = dependsOnText,
                        onValueChange = { dependsOnText = it },
                        label = { Text(stringResource(R.string.preset_module_depends_on)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reqSusfs,
                        onValueChange = { reqSusfs = it },
                        label = { Text(stringResource(R.string.preset_module_req_susfs)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reqKernelsu,
                        onValueChange = { reqKernelsu = it },
                        label = { Text(stringResource(R.string.preset_module_req_kernelsu)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reqAndroid,
                        onValueChange = { reqAndroid = it },
                        label = { Text(stringResource(R.string.preset_module_req_android)) },
                        // ponytail: numeric only so users can type SDK ints without odd chars.
                        keyboardOptions = KeyboardOptions.Default,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reqMetadata,
                        onValueChange = { reqMetadata = it },
                        label = { Text(stringResource(R.string.preset_module_req_metadata)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmedId = moduleId.trim()
                val dup = (0 until existingModules.size).any { i ->
                    i != editingIdx && existingModules[i].moduleId == trimmedId
                }
                if (dup) {
                    onDuplicateId()
                    return@TextButton
                }
                val req = buildRequirement(reqSusfs, reqKernelsu, reqAndroid, reqMetadata)
                val depends = dependsOnText.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val version = moduleVersion.trim().ifBlank { null }
                onSave(
                    PresetModule(
                        moduleName = moduleName.trim(),
                        moduleId = trimmedId,
                        moduleVersion = version,
                        directUrl = directUrl.trim(),
                        stopIfFail = stopIfFail,
                        rebootAfter = rebootAfter,
                        dependsOn = depends,
                        requirement = req
                    )
                )
            }, enabled = canSave) {
                Text(stringResource(R.string.preset_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun buildRequirement(
    susfs: String,
    kernelsu: String,
    android: String,
    metadata: String
): PresetRequirement? {
    val s = susfs.trim().ifBlank { null }
    val k = kernelsu.trim().ifBlank { null }
    val a = android.trim().ifBlank { null }
    val m = metadata.trim().ifBlank { null }
    if (s == null && k == null && a == null && m == null) return null
    return PresetRequirement(susfs = s, kernelsu = k, android = a, metadata = m)
}
