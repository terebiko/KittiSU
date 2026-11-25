package anhiutangerinee.kittisu.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.settings.AppBackButton
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.blurEffect
import anhiutangerinee.kittisu.ui.theme.m3CardContainerColor
import anhiutangerinee.kittisu.ui.util.getRootShell
import anhiutangerinee.kittisu.ui.util.shellQuote
import com.topjohnwu.superuser.CallbackList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SavedRootScript(
    val id: String,
    val name: String,
    val description: String,
    val content: String,
    val lastRunAt: Long = 0L,
    val lastExitCode: Int? = null,
)

private object RootScriptStore {
    private const val INDEX_FILE = "root_scripts_index.json"

    private fun indexFile(context: Context): File = File(context.filesDir, INDEX_FILE)

    fun load(context: Context): List<SavedRootScript> {
        val file = indexFile(context)
        if (!file.isFile) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").trim()
                val name = o.optString("name").trim()
                if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
                SavedRootScript(
                    id = id,
                    name = name,
                    description = o.optString("description", ""),
                    content = o.optString("content", ""),
                    lastRunAt = o.optLong("lastRunAt", 0L),
                    lastExitCode = if (o.has("lastExitCode") && !o.isNull("lastExitCode")) {
                        o.optInt("lastExitCode")
                    } else null
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, list: List<SavedRootScript>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(
                    JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("description", s.description)
                        put("content", s.content)
                        put("lastRunAt", s.lastRunAt)
                        if (s.lastExitCode != null) put("lastExitCode", s.lastExitCode)
                    }
                )
            }
            indexFile(context).writeText(arr.toString())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScriptRunnerScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var scripts by remember { mutableStateOf<List<SavedRootScript>>(emptyList()) }
    var editingScript by remember { mutableStateOf<SavedRootScript?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var deletingScript by remember { mutableStateOf<SavedRootScript?>(null) }

    // Execution dialog state
    var runningScript by remember { mutableStateOf<SavedRootScript?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var runExitCode by remember { mutableStateOf<Int?>(null) }
    var runDurationMs by remember { mutableStateOf(0L) }
    val runOutputLines = remember { mutableStateListOf<String>() }

    fun persist(updated: List<SavedRootScript>) {
        scripts = updated
        scope.launch(Dispatchers.IO) { RootScriptStore.save(context, updated) }
    }

    LaunchedEffect(Unit) {
        scripts = withContext(Dispatchers.IO) { RootScriptStore.load(context) }
    }

    fun resolveUriName(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "script.sh"
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                val name = resolveUriName(uri).removeSuffix(".sh")
                withContext(Dispatchers.Main) {
                    if (text != null) {
                        editingScript = SavedRootScript(
                            id = UUID.randomUUID().toString(),
                            name = name.ifBlank { "script" },
                            description = "",
                            content = text
                        )
                        showEditorDialog = true
                    } else {
                        Toast.makeText(context, R.string.script_import_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun executeScript(script: SavedRootScript) {
        runningScript = script
        isRunning = true
        runExitCode = null
        runDurationMs = 0L
        runOutputLines.clear()

        scope.launch(Dispatchers.IO) {
            val start = SystemClock.elapsedRealtime()
            val tempScript = File(context.cacheDir, "run_${script.id.take(8)}.sh")
            val code = try {
                tempScript.writeText(script.content.replace("\r\n", "\n"))
                tempScript.setReadable(true, false)
                val outCb = object : CallbackList<String?>() {
                    override fun onAddElement(s: String?) {
                        if (s != null) {
                            scope.launch(Dispatchers.Main) {
                                runOutputLines.add(s)
                            }
                        }
                    }
                }
                val errCb = object : CallbackList<String?>() {
                    override fun onAddElement(s: String?) {
                        if (s != null) {
                            scope.launch(Dispatchers.Main) {
                                runOutputLines.add("[stderr] $s")
                            }
                        }
                    }
                }
                val res = getRootShell().newJob()
                    .add("sh ${tempScript.absolutePath.shellQuote()}")
                    .to(outCb, errCb)
                    .exec()
                res.code
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    runOutputLines.add("[error] ${e.message}")
                }
                1
            } finally {
                tempScript.delete()
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            val now = System.currentTimeMillis()
            withContext(Dispatchers.Main) {
                runExitCode = code
                runDurationMs = elapsed
                isRunning = false
                val updated = scripts.map {
                    if (it.id == script.id) it.copy(lastRunAt = now, lastExitCode = code) else it
                }
                persist(updated)
            }
        }
    }

    // Create / Edit Script Dialog
    if (showEditorDialog) {
        val initial = editingScript
        var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
        var desc by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
        var body by remember(initial) { mutableStateOf(initial?.content.orEmpty().ifBlank { "#!/system/bin/sh\n" }) }

        AlertDialog(
            onDismissRequest = { showEditorDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Text(
                    text = stringResource(
                        if (initial == null || scripts.none { it.id == initial.id }) {
                            R.string.script_runner_add
                        } else {
                            R.string.script_runner_edit
                        }
                    ),
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.script_runner_name)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text(stringResource(R.string.script_runner_desc_optional)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text(stringResource(R.string.script_runner_content)) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        minLines = 6,
                        maxLines = 12,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && body.isNotBlank(),
                    onClick = {
                        val trimmedName = name.trim()
                        val exists = initial != null && scripts.any { it.id == initial.id }
                        val updated = if (exists && initial != null) {
                            scripts.map {
                                if (it.id == initial.id) {
                                    it.copy(name = trimmedName, description = desc.trim(), content = body)
                                } else it
                            }
                        } else {
                            listOf(
                                SavedRootScript(
                                    id = initial?.id ?: UUID.randomUUID().toString(),
                                    name = trimmedName,
                                    description = desc.trim(),
                                    content = body
                                )
                            ) + scripts
                        }
                        persist(updated)
                        showEditorDialog = false
                    }
                ) {
                    Text(stringResource(R.string.preset_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditorDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deletingScript?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingScript = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.script_runner_delete_title)) },
            text = { Text(stringResource(R.string.script_runner_delete_msg, target.name)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        persist(scripts.filterNot { it.id == target.id })
                        deletingScript = null
                    }
                ) {
                    Text(stringResource(R.string.uninstall))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingScript = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Script Execution Live Output Dialog
    runningScript?.let { script ->
        val copiedToast = stringResource(R.string.root_console_copied)
        AlertDialog(
            onDismissRequest = { if (!isRunning) runningScript = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = script.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    runExitCode?.let { code ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (code == 0) Color(0xFF1F3A26) else Color(0xFF3E1F22)
                        ) {
                            Text(
                                text = "exit $code · ${runDurationMs}ms",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                ),
                                color = if (code == 0) Color(0xFF7EE787) else Color(0xFFFF7B72),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRunning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0D1117),
                        border = BorderStroke(0.6.dp, Color(0xFF30363D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 340.dp)
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp)
                            ) {
                                val text = if (runOutputLines.isEmpty() && !isRunning) {
                                    stringResource(R.string.script_runner_no_output)
                                } else {
                                    runOutputLines.joinToString("\n")
                                }
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.5.sp
                                    ),
                                    color = Color(0xFFE6EDF3)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isRunning,
                    onClick = { runningScript = null }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                if (runOutputLines.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(
                                ClipData.newPlainText("SovaSU Script Output", runOutputLines.joinToString("\n"))
                            )
                            Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.root_console_copy))
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = {
                    Text(
                        text = stringResource(R.string.script_runner_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    AppBackButton(onClick = { navigator.pop() })
                },
                actions = {
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("text/*", "application/x-sh", "application/octet-stream"))
                    }) {
                        Icon(
                            imageVector = Icons.TwoTone.FileUpload,
                            contentDescription = stringResource(R.string.script_runner_import)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (ThemeConfig.isEnableBlur || ThemeConfig.isFullScreenBackgroundEnabled) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    scrolledContainerColor = if (ThemeConfig.isEnableBlur || ThemeConfig.isFullScreenBackgroundEnabled) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                ),
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingScript = null
                    showEditorDialog = true
                },
                icon = { Icon(Icons.TwoTone.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.script_runner_add)) }
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        if (scripts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(m3CardContainerColor()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.script_runner_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.script_runner_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = {
                                importLauncher.launch(arrayOf("text/*", "application/x-sh", "application/octet-stream"))
                            }
                        ) {
                            Text(stringResource(R.string.script_runner_import))
                        }
                        Button(
                            onClick = {
                                editingScript = null
                                showEditorDialog = true
                            }
                        ) {
                            Text(stringResource(R.string.script_runner_add))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(scripts, key = { it.id }) { script ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = m3CardContainerColor().copy(alpha = CardConfig.cardAlpha),
                        border = BorderStroke(
                            0.6.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = script.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (script.description.isNotBlank()) {
                                        Text(
                                            text = script.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (script.lastRunAt > 0L) {
                                        val relTime = DateUtils.getRelativeTimeSpanString(
                                            script.lastRunAt,
                                            System.currentTimeMillis(),
                                            DateUtils.MINUTE_IN_MILLIS
                                        )
                                        val codeTag = script.lastExitCode?.let { " · exit $it" }.orEmpty()
                                        Text(
                                            text = "$relTime$codeTag",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (script.lastExitCode == 0) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            }
                                        )
                                    }
                                }
                                FilledTonalIconButton(
                                    onClick = { executeScript(script) },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.TwoTone.PlayArrow,
                                        contentDescription = stringResource(R.string.preset_run_script)
                                    )
                                }
                            }

                            // 2-line monospace preview of script body
                            val preview = remember(script.content) {
                                script.content.lines()
                                    .filter { it.isNotBlank() && !it.trimStart().startsWith("#!") }
                                    .take(2)
                                    .joinToString("\n")
                                    .ifBlank { script.content.trim() }
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = preview,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        editingScript = script
                                        showEditorDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.script_runner_edit))
                                }
                                TextButton(
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    onClick = { deletingScript = script }
                                ) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.uninstall))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
