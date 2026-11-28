package anhiutangerinee.kittisu.ui.util

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import java.io.File
import java.util.zip.ZipInputStream

data class AntiBrickFinding(
    val source: String,
    val lineNumber: Int,
    val reason: String,
    val snippet: String,
)

data class AntiBrickScanResult(
    val findings: List<AntiBrickFinding> = emptyList(),
) {
    val isDangerous: Boolean
        get() = findings.isNotEmpty()
}

/**
 * SovaSU Anti-Brick Guard.
 *
 * Scans root shell commands, `.sh` scripts, and module `.zip` packages for destructive
 * partition/system operations (e.g. `dd if=/dev/zero`, `rm -rf /`, `wipefs`, writes to
 * `edl`, `bootloader`, `tz`, `ocdt`, `abl`, `xbl`, `preloader`, `efs`, `nvram`, `persist`,
 * raw `/dev/block/...`, etc.).
 */
object AntiBrickGuard {
    const val PREF_KEY = "anti_brick_enabled"
    const val DISABLED_FLAG_PATH = "/data/adb/ksu/.anti_brick_disabled"
    const val CONFIRMED_ENV = "SOVASU_ANTI_BRICK_CONFIRMED"
    const val CONFIRM_TIMEOUT_SECONDS = 10

    private const val MAX_SCAN_FILE_BYTES = 2 * 1024 * 1024L

    private val CRITICAL_PARTITIONS = listOf(
        "edl", "bootloader", "aboot", "abl", "xbl", "xbl_config", "xbl_ramdump",
        "sbl", "sbl1", "sbl2", "sbl3", "tz", "tzsdi", "ocdt", "cdt", "ddr",
        "rpm", "hyp", "devcfg", "devinfo", "keymaster", "keystore", "frp",
        "persist", "modem", "modemst1", "modemst2", "fsg", "fsc", "efs", "sec_efs",
        "nvram", "nvdata", "nvcfg", "protect1", "protect2", "proinfo", "seccfg",
        "lk", "lk2", "preloader", "preloader_raw", "tee", "scp", "sspm", "mcupm",
        "dpm", "spmfw", "pi_img", "boot_para", "misc", "para", "param",
        "apdp", "msadp", "cmnlib", "cmnlib64", "storsec", "uefisecapp", "qupfw",
        "shrm", "aop", "cpucp", "multiimgoem", "imagefv", "featenabler",
        "sec", "secdata", "vbmeta", "vbmeta_system", "vbmeta_vendor",
        "super", "userdata", "metadata", "dtbo", "dsp", "rawdump",
        "mmcblk0", "mmcblk0boot0", "mmcblk0boot1",
        "sda", "sdb", "sdc", "sdd", "sde", "sdf", "sdg", "nvme0n1",
    )

    private val RE_DD_OF_BLOCK = Regex(
        """\bdd\b[^\n]*\bof\s*=\s*['"]?/dev/(block|mtd|disk|mmc|sd|nvme)""",
        RegexOption.IGNORE_CASE
    )
    private val RE_DD_ZERO = Regex(
        """\bdd\b[^\n]*\bif\s*=\s*['"]?/dev/(zero|urandom|random)\b""",
        RegexOption.IGNORE_CASE
    )
    private val RE_DD_SAFE_OF = Regex(
        """\bof\s*=\s*['"]?/(sdcard|storage|data/local/tmp|tmp|cache)/""",
        RegexOption.IGNORE_CASE
    )
    private val RE_REDIR_BLOCK = Regex(
        """(>>?|tee\s+(-a\s+)?|cp\s+[^\n]+\s+|cat\s+[^\n]*>\s*|truncate\s+[^\n]*)/dev/(block|mtd|disk)""",
        RegexOption.IGNORE_CASE
    )
    private val RE_RM_ROOT = Regex(
        """\brm\b[^\n]*\s+-[a-zA-Z]*[rf][a-zA-Z]*[^\n]*\s+(/(\*|\s|$|;|&|\|)|/(data|system|vendor|product|odm|dev|persist|efs|metadata|apex|mnt/vendor/persist)(/(\*|\s|$|;|&|\|)|\s|$|;|&|\|))""",
        RegexOption.IGNORE_CASE
    )
    private val RE_RM_BLOCK = Regex(
        """\brm\b[^\n]*/dev/(block|mtd)""",
        RegexOption.IGNORE_CASE
    )
    private val RE_WIPE_TOOLS = Regex(
        """\b(wipefs|blkdiscard|mkfs(\.[a-z0-9]+)?|mke2fs|sgdisk|gdisk|fdisk|sfdisk|parted|flash_erase|nandwrite|ubiformat|shred)\b""",
        RegexOption.IGNORE_CASE
    )
    private val RE_FASTBOOT_ERASE = Regex(
        """\bfastboot\b[^\n]*\b(erase|format|flash)\b""",
        RegexOption.IGNORE_CASE
    )
    private val RE_EDL_TRIGGER = Regex(
        """(\breboot\b[^\n]*\bedl\b|sys\.powerctl[^\n]*\bedl\b|\bsvc\s+power\s+reboot\s+edl\b|/proc/sysrq-trigger)""",
        RegexOption.IGNORE_CASE
    )
    private val RE_OBFUSCATED_EXEC = Regex(
        """(base64\s+(-d|--decode)|xxd\s+-r|openssl\s+enc\s+-d)[^\n]*\|\s*(sh|bash|ash|su|eval)\b""",
        RegexOption.IGNORE_CASE
    )
    private val RE_DESTRUCTIVE_VERB = Regex(
        """(\bdd\b|\bof\s*=|\brm\b|\bwipe\b|\berase\b|\bformat\b|\bmkfs|\bzero\b|/dev/zero|/dev/urandom|>>?|\bcat\b|\becho\b|\btruncate\b|\bshred\b|\bblkdiscard\b|\bwipefs\b|\bsgdisk\b|\bfdisk\b|\bparted\b|\bflash_erase\b|\bnandwrite\b|\bcp\b|\btee\b)""",
        RegexOption.IGNORE_CASE
    )

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putBoolean(PREF_KEY, enabled)
        }
        // Sync native ksud flag file on rootfs
        runCatching {
            val cmd = if (enabled) {
                "rm -f $DISABLED_FLAG_PATH"
            } else {
                "mkdir -p /data/adb/ksu && touch $DISABLED_FLAG_PATH"
            }
            getRootShell().newJob().add(cmd).exec()
        }
    }

    fun scanText(source: String, content: String): AntiBrickScanResult {
        val findings = mutableListOf<AntiBrickFinding>()
        content.lineSequence().forEachIndexed { idx, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val lineNo = idx + 1
            val snippet = line.take(140)

            when {
                RE_DD_OF_BLOCK.containsMatchIn(line) -> {
                    findings += AntiBrickFinding(
                        source = source,
                        lineNumber = lineNo,
                        reason = "dd write to raw block device (/dev/block)",
                        snippet = snippet
                    )
                }

                RE_DD_ZERO.containsMatchIn(line) && !RE_DD_SAFE_OF.containsMatchIn(line) -> {
                    findings += AntiBrickFinding(
                        source = source,
                        lineNumber = lineNo,
                        reason = "dd zero/random overwrite (if=/dev/zero or urandom)",
                        snippet = snippet
                    )
                }

                RE_REDIR_BLOCK.containsMatchIn(line) -> {
                    findings += AntiBrickFinding(
                        source = source,
                        lineNumber = lineNo,
                        reason = "Direct write/redirect to block device (/dev/block)",
                        snippet = snippet
                    )
                }

                RE_RM_ROOT.containsMatchIn(line) || RE_RM_BLOCK.containsMatchIn(line) -> {
                    findings += AntiBrickFinding(
                        source = source,
                        lineNumber = lineNo,
                        reason = "Destructive rm targeting root/system/data/block partition",
                        snippet = snippet
                    )
                }

                RE_WIPE_TOOLS.containsMatchIn(line) || RE_FASTBOOT_ERASE.containsMatchIn(line) -> {
                    findings += AntiBrickFinding(
                        source = source,
                        lineNumber = lineNo,
                        reason = "Disk/partition wipe or format command",
                        snippet = snippet
                    )
                }

                RE_EDL_TRIGGER.containsMatchIn(line) -> {
                    findings += AntiBrickFinding(
                        source = source,
                        lineNumber = lineNo,
                        reason = "Forced EDL / sysrq-trigger command",
                        snippet = snippet
                    )
                }

                RE_OBFUSCATED_EXEC.containsMatchIn(line) -> {
                    findings += AntiBrickFinding(
                        source = source,
                        lineNumber = lineNo,
                        reason = "Obfuscated payload piped directly to shell (base64/xxd | sh)",
                        snippet = snippet
                    )
                }

                RE_DESTRUCTIVE_VERB.containsMatchIn(line) -> {
                    val isReadOnlyBackup = line.lowercase().contains("dd ") &&
                        RE_DD_SAFE_OF.containsMatchIn(line) &&
                        !RE_DD_ZERO.containsMatchIn(line)
                    if (!isReadOnlyBackup) {
                        val part = findCriticalPartition(line)
                        if (part != null) {
                            findings += AntiBrickFinding(
                                source = source,
                                lineNumber = lineNo,
                                reason = "Destructive operation targeting critical partition '$part'",
                                snippet = snippet
                            )
                        }
                    }
                }
            }
        }
        return AntiBrickScanResult(findings)
    }

    private fun findCriticalPartition(line: String): String? {
        val lower = line.lowercase()
        for (part in CRITICAL_PARTITIONS) {
            var start = 0
            while (true) {
                val pos = lower.indexOf(part, start)
                if (pos < 0) break
                val beforeOk = pos == 0 || (!lower[pos - 1].isLetterOrDigit() && lower[pos - 1] != '_')
                val endPos = pos + part.length
                val afterOk = if (endPos >= lower.length) {
                    true
                } else {
                    val next = lower[endPos]
                    if (!next.isLetterOrDigit() && next != '_') {
                        true
                    } else {
                        val rest = lower.substring(endPos)
                        (rest.startsWith("_a") || rest.startsWith("_b")) &&
                            (rest.length == 2 || (!rest[2].isLetterOrDigit() && rest[2] != '_'))
                    }
                }
                if (beforeOk && afterOk) {
                    val shortWord = part.length <= 4
                    val hasDeviceContext = lower.contains("/dev/") ||
                        lower.contains("by-name") ||
                        lower.contains("by-partlabel") ||
                        lower.contains("of=") ||
                        lower.contains("block") ||
                        lower.contains("/persist") ||
                        lower.contains("/efs") ||
                        lower.contains("partition")
                    if (!shortWord || hasDeviceContext) {
                        return part
                    }
                }
                start = pos + 1
            }
        }
        return null
    }

    /**
     * Scans a command line AND any referenced local `.sh` script file (if readable).
     */
    fun scanCommandLine(command: String): AntiBrickScanResult {
        val all = mutableListOf<AntiBrickFinding>()
        all += scanText("command", command).findings
        command.split(Regex("\\s+")).forEach { token ->
            val cleaned = token.trim('\'', '"', ';')
            if (cleaned.endsWith(".sh", ignoreCase = true) && cleaned.startsWith("/")) {
                all += scanFile(cleaned).findings
            }
        }
        return AntiBrickScanResult(all)
    }

    fun scanFile(path: String): AntiBrickScanResult {
        return runCatching {
            val f = File(path)
            if (f.isFile && f.length() <= MAX_SCAN_FILE_BYTES && f.canRead()) {
                return scanText(path, f.readText())
            }
            // Fallback via root shell for root-owned files like /data/adb/modules/<id>/action.sh
            val res = getRootShell().newJob()
                .add("head -c $MAX_SCAN_FILE_BYTES ${path.shellQuote()} 2>/dev/null")
                .to(mutableListOf())
                .exec()
            if (res.isSuccess && res.out.isNotEmpty()) {
                scanText(path, res.out.joinToString("\n"))
            } else {
                AntiBrickScanResult()
            }
        }.getOrDefault(AntiBrickScanResult())
    }

    /**
     * Scans all `.sh` files and `update-binary` inside a module ZIP URI before flashing.
     */
    fun scanModuleZip(context: Context, uri: Uri): AntiBrickScanResult {
        val all = mutableListOf<AntiBrickFinding>()
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        if (!entry.isDirectory) {
                            val name = entry.name
                            val lower = name.lowercase()
                            val shouldScan = lower.endsWith(".sh") ||
                                lower.endsWith("/update-binary") ||
                                lower == "update-binary"
                            if (shouldScan) {
                                val bytes = zis.readBytes()
                                if (bytes.size <= MAX_SCAN_FILE_BYTES) {
                                    all += scanText(name, bytes.decodeToString()).findings
                                }
                            }
                        }
                        zis.closeEntry()
                    }
                }
            }
        }
        return AntiBrickScanResult(all)
    }
}
