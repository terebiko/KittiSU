package anhiutangerinee.kittisu.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.twotone.Send
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.KeyboardArrowDown
import androidx.compose.material.icons.twotone.KeyboardArrowUp
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Terminal
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anhiutangerinee.kittisu.KernelPatchManager
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.component.settings.AppBackButton
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.m3CardContainerColor
import anhiutangerinee.kittisu.ui.util.hasKsuDaemon
import anhiutangerinee.kittisu.ui.util.hasKsuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

private const val CWD_SENTINEL = "__SOVASU_CWD__:"
private const val UID_SENTINEL = "__SOVASU_UID__:"
private const val PID_SENTINEL = "__SOVASU_SHPID__:"
private const val MAX_CONSOLE_LINES = 2000

private data class ConsoleLine(
    val text: String,
    val isCommand: Boolean = false,
    val isSystem: Boolean = false,
    val isError: Boolean = false,
)

private class InteractiveRootShell(
    private val onOutputLine: (ConsoleLine) -> Unit,
    private val onStateChanged: (isRoot: Boolean, cwd: String, running: Boolean) -> Unit,
) {
    @Volatile private var process: Process? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var shellPid: Int = -1
    @Volatile var isRoot: Boolean = false
        private set
    @Volatile var cwd: String = "/"
        private set
    @Volatile private var alive: Boolean = false

    fun start() {
        stop()
        try {
            val cmd = buildShellLaunchCommand()
            val pb = ProcessBuilder(cmd)
            pb.environment()["PATH"] =
                "/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:/system/bin:/system/xbin:/vendor/bin:/product/bin:" +
                    (System.getenv("PATH") ?: "")
            pb.environment()["TERM"] = "xterm-256color"
            pb.environment()["HOME"] = "/data/local/tmp"
            pb.redirectErrorStream(true)

            val proc = pb.start()
            process = proc
            writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
            alive = true

            // Reader thread streams output live
            Thread({
                try {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val raw = line ?: continue
                            when {
                                raw.startsWith(PID_SENTINEL) -> {
                                    shellPid = raw.removePrefix(PID_SENTINEL).trim().toIntOrNull() ?: -1
                                }
                                raw.startsWith(UID_SENTINEL) -> {
                                    isRoot = raw.removePrefix(UID_SENTINEL).trim() == "0"
                                    onStateChanged(isRoot, cwd, true)
                                }
                                raw.startsWith(CWD_SENTINEL) -> {
                                    cwd = raw.removePrefix(CWD_SENTINEL).trim().ifEmpty { "/" }
                                    onStateChanged(isRoot, cwd, true)
                                }
                                else -> {
                                    onOutputLine(ConsoleLine(text = raw))
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    alive = false
                    onStateChanged(isRoot, cwd, false)
                    onOutputLine(ConsoleLine("[session ended — tap Restart to reconnect]", isSystem = true))
                }
            }, "SovaSU-ConsoleReader").apply { isDaemon = true }.start()

            // Probe shell PID, UID, and initial CWD immediately
            writeRaw("echo \"${PID_SENTINEL}\$\$\"; echo \"${UID_SENTINEL}\$(id -u)\"; cd / 2>/dev/null; echo \"${CWD_SENTINEL}\$(pwd)\"")
        } catch (e: Throwable) {
            alive = false
            onStateChanged(false, "/", false)
            onOutputLine(ConsoleLine("Failed to start shell: ${e.message}", isError = true))
        }
    }

    private fun buildShellLaunchCommand(): List<String> {
        val nativeDir = ksuApp.applicationInfo.nativeLibraryDir
        val ksud = File(nativeDir, "libksud.so")
        if (hasKsuManager && hasKsuDaemon() && ksud.isFile) {
            return listOf(ksud.absolutePath, "debug", "su")
        }
        if (KernelPatchManager.isKernelPatchActive) {
            val key = KernelPatchManager.superKey.ifBlank { "su" }
            val superCmd = File(KernelPatchManager.SUPERCMD)
            if (superCmd.exists()) {
                return listOf(
                    KernelPatchManager.SUPERCMD,
                    key,
                    "-Z",
                    KernelPatchManager.MAGISK_SCONTEXT
                )
            }
            val kpatch = File(nativeDir, "libkpatch.so")
            if (kpatch.isFile) {
                return listOf(
                    kpatch.absolutePath,
                    key,
                    "su",
                    "-Z",
                    KernelPatchManager.MAGISK_SCONTEXT
                )
            }
        }
        // Try standard su on PATH if present, else fallback to sh
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su")
        val existingSu = suPaths.firstOrNull { File(it).exists() }
        return if (existingSu != null) {
            listOf(existingSu)
        } else {
            listOf("/system/bin/sh")
        }
    }

    @Synchronized
    fun writeRaw(line: String) {
        try {
            val w = writer ?: return
            w.write(line)
            w.newLine()
            w.flush()
        } catch (e: Throwable) {
            onOutputLine(ConsoleLine("Write error: ${e.message}", isError = true))
        }
    }

    fun sendCommand(command: String) {
        if (!alive) {
            start()
        }
        val promptPrefix = if (isRoot) "#" else "$"
        onOutputLine(ConsoleLine("$cwd $promptPrefix $command", isCommand = true))
        if (command.isBlank()) return
        // Run command and refresh CWD marker afterward
        writeRaw("$command\necho \"${CWD_SENTINEL}\$(pwd)\"")
    }

    fun sendInterrupt() {
        onOutputLine(ConsoleLine("^C", isSystem = true))
        val pid = shellPid
        if (pid > 0) {
            Thread({
                runCatching {
                    // Kill any child process spawned by the interactive shell
                    val helper = ProcessBuilder(buildShellLaunchCommand()).start()
                    BufferedWriter(OutputStreamWriter(helper.outputStream)).use { w ->
                        w.write("pkill -INT -P $pid 2>/dev/null || true\n")
                        w.write("for p in /proc/[0-9]*/stat; do pp=\$(awk '{print \$4}' \$p 2>/dev/null); [ \"\$pp\" = \"$pid\" ] && kill -2 \$(basename \$(dirname \$p)) 2>/dev/null; done\n")
                        w.write("exit\n")
                        w.flush()
                    }
                    helper.waitFor()
                }
            }, "SovaSU-SIGINT").start()
        }
        // Also send raw ETX byte to stdin in case foreground program reads stdin
        runCatching {
            process?.outputStream?.apply {
                write(3)
                flush()
            }
        }
    }

    fun completePath(prefix: String): List<String> {
        if (!alive) return emptyList()
        return runCatching {
            val dir = if (prefix.contains('/')) prefix.substringBeforeLast('/') + "/" else ""
            val base = prefix.substringAfterLast('/')
            val searchDir = when {
                dir.startsWith("/") -> dir
                dir.isNotEmpty() -> "$cwd/$dir"
                else -> cwd
            }
            val helper = ProcessBuilder(buildShellLaunchCommand()).start()
            BufferedWriter(OutputStreamWriter(helper.outputStream)).use { w ->
                w.write("ls -1a \"${searchDir.replace("\"", "\\\"")}\" 2>/dev/null\n")
                w.write("exit\n")
                w.flush()
            }
            val matches = BufferedReader(InputStreamReader(helper.inputStream)).useLines { seq ->
                seq.map { it.trim() }
                    .filter { it.isNotEmpty() && it != "." && it != ".." && it.startsWith(base) }
                    .take(12)
                    .map { dir + it }
                    .toList()
            }
            helper.destroy()
            matches
        }.getOrDefault(emptyList())
    }

    fun stop() {
        alive = false
        runCatching { writer?.close() }
        runCatching { process?.destroy() }
        writer = null
        process = null
        shellPid = -1
    }
}

/**
 * Lightweight ANSI SGR color parser so terminal output with color codes renders cleanly
 * without raw escape sequences cluttering the screen.
 */
private fun parseAnsiText(input: String, defaultColor: Color): AnnotatedString {
    if (!input.contains('\u001b')) {
        return AnnotatedString(input)
    }
    val regex = Regex("\u001b\\[([0-9;]*)([A-Za-z])")
    return buildAnnotatedString {
        var currentColor = defaultColor
        var isBold = false
        var lastIdx = 0
        for (match in regex.findAll(input)) {
            if (match.range.first > lastIdx) {
                val segment = input.substring(lastIdx, match.range.first)
                pushStyle(
                    SpanStyle(
                        color = currentColor,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                    )
                )
                append(segment)
                pop()
            }
            val cmd = match.groupValues[2]
            if (cmd == "m") {
                val codes = match.groupValues[1]
                    .split(';')
                    .mapNotNull { it.toIntOrNull() }
                    .ifEmpty { listOf(0) }
                for (code in codes) {
                    when (code) {
                        0 -> {
                            currentColor = defaultColor
                            isBold = false
                        }
                        1 -> isBold = true
                        30 -> currentColor = Color(0xFF6B7280)
                        31, 91 -> currentColor = Color(0xFFF87171)
                        32, 92 -> currentColor = Color(0xFF4ADE80)
                        33, 93 -> currentColor = Color(0xFFFACC15)
                        34, 94 -> currentColor = Color(0xFF60A5FA)
                        35, 95 -> currentColor = Color(0xFFC084FC)
                        36, 96 -> currentColor = Color(0xFF22D3EE)
                        37, 97 -> currentColor = Color(0xFFF3F4F6)
                    }
                }
            }
            lastIdx = match.range.last + 1
        }
        if (lastIdx < input.length) {
            val tail = input.substring(lastIdx)
            pushStyle(
                SpanStyle(
                    color = currentColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                )
            )
            append(tail)
            pop()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RootConsoleScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var warningAccepted by remember { mutableStateOf(false) }
    var isRoot by remember { mutableStateOf(false) }
    var currentDir by remember { mutableStateOf("/") }
    var isShellRunning by remember { mutableStateOf(false) }
    var fontSizeSp by remember { mutableFloatStateOf(13f) }

    val lines = remember { mutableStateListOf<ConsoleLine>() }
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var draftBeforeHistory by remember { mutableStateOf("") }

    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var shiftActive by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val welcomeBanner = stringResource(R.string.root_console_welcome)
    val copiedToast = stringResource(R.string.root_console_copied)

    val shell = remember {
        InteractiveRootShell(
            onOutputLine = { line ->
                scope.launch(Dispatchers.Main) {
                    if (lines.size >= MAX_CONSOLE_LINES) {
                        lines.removeAt(0)
                    }
                    lines.add(line)
                }
            },
            onStateChanged = { root, cwd, running ->
                scope.launch(Dispatchers.Main) {
                    isRoot = root
                    currentDir = cwd
                    isShellRunning = running
                }
            }
        )
    }

    LaunchedEffect(warningAccepted) {
        if (warningAccepted) {
            lines.clear()
            lines.add(ConsoleLine(welcomeBanner, isSystem = true))
            withContext(Dispatchers.IO) {
                shell.start()
            }
            runCatching { focusRequester.requestFocus() }
        }
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            shell.stop()
        }
    }

    // Mandatory safety warning dialog on opening Root Console
    if (!warningAccepted) {
        AlertDialog(
            onDismissRequest = { navigator.pop() },
            icon = {
                Icon(
                    imageVector = Icons.TwoTone.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.root_console_warning_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.root_console_warning_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = { warningAccepted = true }
                ) {
                    Text(stringResource(R.string.root_console_warning_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { navigator.pop() }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    fun submitCurrentCommand() {
        val cmd = inputValue.text
        inputValue = TextFieldValue("")
        historyIndex = -1
        draftBeforeHistory = ""
        ctrlActive = false
        altActive = false
        shiftActive = false

        val trimmed = cmd.trim()
        if (trimmed == "clear") {
            lines.clear()
            return
        }
        if (trimmed.isNotEmpty() && history.lastOrNull() != trimmed) {
            history.add(trimmed)
        }
        scope.launch(Dispatchers.IO) {
            shell.sendCommand(cmd)
        }
    }

    fun insertAtCursor(str: String) {
        val text = inputValue.text
        val start = inputValue.selection.start.coerceIn(0, text.length)
        val end = inputValue.selection.end.coerceIn(0, text.length)
        val newText = text.substring(0, start) + str + text.substring(end)
        val newCursor = start + str.length
        inputValue = TextFieldValue(newText, TextRange(newCursor))
    }

    fun handleHistoryUp() {
        if (history.isEmpty()) return
        if (historyIndex == -1) {
            draftBeforeHistory = inputValue.text
            historyIndex = history.lastIndex
        } else if (historyIndex > 0) {
            historyIndex--
        }
        val entry = history[historyIndex]
        inputValue = TextFieldValue(entry, TextRange(entry.length))
    }

    fun handleHistoryDown() {
        if (historyIndex == -1) return
        if (historyIndex < history.lastIndex) {
            historyIndex++
            val entry = history[historyIndex]
            inputValue = TextFieldValue(entry, TextRange(entry.length))
        } else {
            historyIndex = -1
            inputValue = TextFieldValue(draftBeforeHistory, TextRange(draftBeforeHistory.length))
        }
    }

    fun handleCursorMove(delta: Int) {
        val len = inputValue.text.length
        val pos = (inputValue.selection.end + delta).coerceIn(0, len)
        inputValue = inputValue.copy(selection = TextRange(pos))
    }

    fun handleTabComplete() {
        val text = inputValue.text
        val cursor = inputValue.selection.end.coerceIn(0, text.length)
        val before = text.substring(0, cursor)
        val tokenStart = before.lastIndexOf(' ') + 1
        val prefix = before.substring(tokenStart)
        scope.launch {
            val matches = withContext(Dispatchers.IO) { shell.completePath(prefix) }
            when {
                matches.isEmpty() -> Unit
                matches.size == 1 -> {
                    val completed = matches.first()
                    val after = text.substring(cursor)
                    val newText = before.substring(0, tokenStart) + completed + after
                    val newPos = tokenStart + completed.length
                    inputValue = TextFieldValue(newText, TextRange(newPos))
                }
                else -> {
                    lines.add(ConsoleLine(matches.joinToString("  "), isSystem = true))
                }
            }
        }
    }

    fun handleInputChange(newValue: TextFieldValue) {
        val oldText = inputValue.text
        val newText = newValue.text

        // Intercept single-character typing when CTRL / ALT / SHIFT modifiers are active
        if ((ctrlActive || altActive || shiftActive) && newText.length == oldText.length + 1) {
            val insertPos = (newValue.selection.end - 1).coerceIn(0, newText.lastIndex)
            val typedChar = newText[insertPos]

            if (ctrlActive) {
                ctrlActive = false
                when (typedChar.lowercaseChar()) {
                    'c' -> {
                        scope.launch(Dispatchers.IO) { shell.sendInterrupt() }
                        return
                    }
                    'l' -> {
                        lines.clear()
                        return
                    }
                    'd' -> {
                        scope.launch(Dispatchers.IO) { shell.start() }
                        return
                    }
                    'a' -> {
                        inputValue = inputValue.copy(selection = TextRange(0))
                        return
                    }
                    'e' -> {
                        inputValue = inputValue.copy(selection = TextRange(oldText.length))
                        return
                    }
                    'u' -> {
                        inputValue = TextFieldValue("")
                        return
                    }
                    else -> {
                        // Send raw control code to shell if running
                        val ctrlCode = typedChar.uppercaseChar().code - 64
                        if (ctrlCode in 1..26) {
                            lines.add(ConsoleLine("^${typedChar.uppercaseChar()}", isSystem = true))
                        }
                        return
                    }
                }
            }

            if (altActive) {
                altActive = false
                when (typedChar.lowercaseChar()) {
                    'b' -> {
                        // Jump word left
                        val cur = inputValue.selection.end
                        val prevSpace = oldText.substring(0, cur).trimEnd().lastIndexOf(' ')
                        val target = if (prevSpace >= 0) prevSpace + 1 else 0
                        inputValue = inputValue.copy(selection = TextRange(target))
                        return
                    }
                    'f' -> {
                        // Jump word right
                        val cur = inputValue.selection.end
                        val nextSpace = oldText.indexOf(' ', cur).let { if (it < 0) oldText.length else it + 1 }
                        inputValue = inputValue.copy(selection = TextRange(nextSpace))
                        return
                    }
                    else -> {
                        // Fall through with ESC-notation hint
                        lines.add(ConsoleLine("ALT+${typedChar.lowercaseChar()}", isSystem = true))
                        return
                    }
                }
            }

            if (shiftActive) {
                shiftActive = false
                val upper = typedChar.uppercaseChar()
                val modified = newText.substring(0, insertPos) + upper + newText.substring(insertPos + 1)
                inputValue = newValue.copy(text = modified)
                return
            }
        }

        inputValue = newValue
    }

    val terminalBg = Color(0xFF0D1117)
    val terminalHeaderBg = Color(0xFF161B22)
    val terminalInputBg = Color(0xFF131922)
    val terminalDivider = Color(0xFF30363D)
    val terminalFg = Color(0xFFE6EDF3)
    val terminalCmdColor = Color(0xFF7EE787)
    val terminalSysColor = Color(0xFF8B949E)
    val terminalErrColor = Color(0xFFFF7B72)
    val terminalWarnBg = Color(0xFF2A1D12)
    val terminalWarnFg = Color(0xFFF2CC60)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.root_console_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    AppBackButton(onClick = { navigator.pop() })
                },
                actions = {
                    IconButton(onClick = {
                        val allText = lines.joinToString("\n") { it.text }
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("SovaSU Console", allText))
                        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.TwoTone.ContentCopy,
                            contentDescription = stringResource(R.string.root_console_copy),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { lines.clear() }) {
                        Icon(
                            imageVector = Icons.TwoTone.DeleteSweep,
                            contentDescription = stringResource(R.string.root_console_clear),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) { shell.start() }
                    }) {
                        Icon(
                            imageVector = Icons.TwoTone.Refresh,
                            contentDescription = stringResource(R.string.root_console_restart),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (ThemeConfig.isEnableBlur || ThemeConfig.isFullScreenBackgroundEnabled) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                ),
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 4.dp))
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Unified M3 Expressive Terminal Window (Header + Inline Caution + Output + Prompt)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = terminalBg,
                border = BorderStroke(
                    width = 0.8.dp,
                    color = terminalDivider.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Integrated Terminal Status Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(terminalHeaderBg)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isRoot) Color(0xFF1F3A26) else Color(0xFF3E1F22)
                        ) {
                            Text(
                                text = if (isRoot) "ROOT #" else "USER $",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isRoot) terminalCmdColor else terminalErrColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = currentDir,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = terminalSysColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        // Active modifier status pills
                        if (ctrlActive || altActive || shiftActive) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (ctrlActive) ModifierStatusTag("CTRL")
                                if (altActive) ModifierStatusTag("ALT")
                                if (shiftActive) ModifierStatusTag("SHIFT")
                            }
                        }
                    }

                    // Slim 1-line inline caution reminder
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(terminalWarnBg)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Warning,
                            contentDescription = null,
                            tint = terminalWarnFg,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = stringResource(R.string.root_console_caution_banner),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = terminalWarnFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = terminalDivider.copy(alpha = 0.5f))

                    // Terminal Output Stream
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(1.5.dp)
                        ) {
                            itemsIndexed(lines) { _, line ->
                                val lineColor = when {
                                    line.isCommand -> terminalCmdColor
                                    line.isError -> terminalErrColor
                                    line.isSystem -> terminalSysColor
                                    else -> terminalFg
                                }
                                Text(
                                    text = parseAnsiText(line.text, lineColor),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = fontSizeSp.sp,
                                        lineHeight = (fontSizeSp + 3.5f).sp,
                                        fontWeight = if (line.isCommand) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = lineColor
                                )
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.6.dp, color = terminalDivider.copy(alpha = 0.7f))

                    // Integrated Inline Command Input Dock (uses user's native Android IME keyboard)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(terminalInputBg)
                            .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRoot) "#" else "$",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = if (isRoot) terminalCmdColor else terminalErrColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputValue.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.root_console_input_hint),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
                                    ),
                                    color = terminalSysColor.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = inputValue,
                                onValueChange = { handleInputChange(it) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = terminalFg
                                ),
                                cursorBrush = SolidColor(terminalCmdColor),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = { submitCurrentCommand() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        FilledIconButton(
                            onClick = { submitCurrentCommand() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.TwoTone.Send,
                                contentDescription = stringResource(R.string.root_console_run),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Sleek 2x7 Symmetrical M3 Expressive Extra Keys Dock (zero clipping, crisp proportions)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                shape = RoundedCornerShape(14.dp),
                color = m3CardContainerColor().copy(alpha = CardConfig.cardAlpha.coerceAtLeast(0.95f)),
                border = BorderStroke(
                    width = 0.6.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Row 1 (7 equal-width keys): ESC | CTRL | ALT | SHIFT | TAB | / | ^C
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConsoleExtraKey(
                            label = "ESC",
                            active = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                ctrlActive = false
                                altActive = false
                                shiftActive = false
                                inputValue = inputValue.copy(selection = TextRange(inputValue.text.length))
                            }
                        )
                        ConsoleExtraKey(
                            label = "CTRL",
                            active = ctrlActive,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                ctrlActive = !ctrlActive
                            }
                        )
                        ConsoleExtraKey(
                            label = "ALT",
                            active = altActive,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                altActive = !altActive
                            }
                        )
                        ConsoleExtraKey(
                            label = "SHIFT",
                            active = shiftActive,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                shiftActive = !shiftActive
                            }
                        )
                        ConsoleExtraKey(
                            label = "TAB",
                            active = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleTabComplete()
                            }
                        )
                        ConsoleExtraKey(
                            label = "/",
                            active = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                insertAtCursor("/")
                            }
                        )
                        ConsoleExtraKey(
                            label = "^C",
                            active = false,
                            isDanger = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                scope.launch(Dispatchers.IO) { shell.sendInterrupt() }
                            }
                        )
                    }

                    // Row 2 (7 equal-width keys): ◀ | ▲ | ▼ | ▶ | - | | | ~
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConsoleArrowKey(
                            icon = Icons.AutoMirrored.TwoTone.KeyboardArrowLeft,
                            contentDesc = "Left",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleCursorMove(-1)
                            }
                        )
                        ConsoleArrowKey(
                            icon = Icons.TwoTone.KeyboardArrowUp,
                            contentDesc = "Up",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleHistoryUp()
                            }
                        )
                        ConsoleArrowKey(
                            icon = Icons.TwoTone.KeyboardArrowDown,
                            contentDesc = "Down",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleHistoryDown()
                            }
                        )
                        ConsoleArrowKey(
                            icon = Icons.AutoMirrored.TwoTone.KeyboardArrowRight,
                            contentDesc = "Right",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleCursorMove(1)
                            }
                        )
                        ConsoleExtraKey(
                            label = "-",
                            active = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                insertAtCursor("-")
                            }
                        )
                        ConsoleExtraKey(
                            label = "|",
                            active = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                insertAtCursor("|")
                            }
                        )
                        ConsoleExtraKey(
                            label = "~",
                            active = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                insertAtCursor("~")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModifierStatusTag(label: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun ConsoleExtraKey(
    label: String,
    active: Boolean,
    isDanger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        isDanger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        isDanger -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = if (active) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier
            .height(32.dp)
            .semantics {
                role = Role.Button
                selected = active
                contentDescription = label
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
private fun ConsoleArrowKey(
    icon: ImageVector,
    contentDesc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.height(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
