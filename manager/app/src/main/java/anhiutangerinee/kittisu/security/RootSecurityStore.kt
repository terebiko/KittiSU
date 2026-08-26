package anhiutangerinee.kittisu.security

import anhiutangerinee.kittisu.ui.util.getRootShell
import anhiutangerinee.kittisu.ui.util.shellQuote

const val LOCK_STORE_DIRECTORY = "/data/adb/ksu/.kittisu_manager_lock"
internal const val MISSING_EXIT_CODE = 44
private const val CORRUPT_EXIT_CODE = 45

enum class SecurityDocument(val fileName: String) {
    CONFIG("config.json"),
    RUNTIME("runtime.json"),
    OPERATION("operation.json"),
}

sealed interface StoreRead<out T> {
    data object Missing : StoreRead<Nothing>
    data class Valid<T>(val value: T) : StoreRead<T>
    data class Corrupt(val reason: String) : StoreRead<Nothing>
}

internal data class RootCommandResult(val code: Int, val output: String = "")

internal fun interface RootCommandExecutor {
    fun execute(command: String): RootCommandResult
}

class RootSecurityStore internal constructor(
    private val executor: RootCommandExecutor,
) {
    constructor() : this(RootCommandExecutor { executeRoot(it) })

    fun readConfig(): StoreRead<LockConfig> = read(SecurityDocument.CONFIG, SecurityJson::decodeConfig)

    fun readRuntime(): StoreRead<LockRuntime> = read(SecurityDocument.RUNTIME, SecurityJson::decodeRuntime)

    fun writeConfig(config: LockConfig): Boolean =
        write(SecurityDocument.CONFIG, SecurityJson.encode(config))

    fun writeRuntime(runtime: LockRuntime): Boolean =
        write(SecurityDocument.RUNTIME, SecurityJson.encode(runtime))

    internal fun readOperation(): StoreRead<String> = read(SecurityDocument.OPERATION) { json ->
        SecurityJson.requireVersionedObject(json)
        json
    }

    internal fun writeOperation(json: String): Boolean {
        SecurityJson.requireVersionedObject(json)
        return write(SecurityDocument.OPERATION, json)
    }

    internal fun deleteOperation(): Boolean {
        val directory = LOCK_STORE_DIRECTORY.shellQuote()
        val operation = "$LOCK_STORE_DIRECTORY/${SecurityDocument.OPERATION.fileName}".shellQuote()
        val command =
            "( if [ -L $directory ]; then exit $CORRUPT_EXIT_CODE; fi; rm -f $operation )"
        return runCatching { executor.execute(command).code == 0 }.getOrDefault(false)
    }

    /** Removes every security document; used exclusively by the destructive reset. */
    fun deleteAll(): Boolean {
        val directory = LOCK_STORE_DIRECTORY.shellQuote()
        val command = "( rm -rf $directory )"
        return runCatching { executor.execute(command).code == 0 }.getOrDefault(false)
    }

    /**
     * Removes the lock secret and runtime while keeping operation.json intact so the
     * reboot-required gate survives; used at the end of a destructive reset.
     */
    fun deleteConfigAndRuntime(): Boolean {
        val directory = LOCK_STORE_DIRECTORY.shellQuote()
        val config = "$LOCK_STORE_DIRECTORY/${SecurityDocument.CONFIG.fileName}".shellQuote()
        val runtime = "$LOCK_STORE_DIRECTORY/${SecurityDocument.RUNTIME.fileName}".shellQuote()
        val command =
            "( if [ -L $directory ]; then exit $CORRUPT_EXIT_CODE; fi; " +
                "rm -f $config $runtime )"
        return runCatching { executor.execute(command).code == 0 }.getOrDefault(false)
    }

    private fun <T> read(document: SecurityDocument, decode: (String) -> T): StoreRead<T> {
        val directory = LOCK_STORE_DIRECTORY.shellQuote()
        val target = "$LOCK_STORE_DIRECTORY/${document.fileName}".shellQuote()
        val result = runCatching {
            executor.execute(
                "( if [ -L $directory ]; then exit $CORRUPT_EXIT_CODE; fi; " +
                    "if [ ! -e $target ]; then exit $MISSING_EXIT_CODE; fi; " +
                    "if [ -L $target ] || [ ! -f $target ]; then exit $CORRUPT_EXIT_CODE; fi; " +
                    "if [ \"\$(stat -c '%u:%g:%a' $directory)\" != '0:0:700' ] || " +
                    "[ \"\$(stat -c '%u:%g:%a' $target)\" != '0:0:600' ]; then " +
                    "exit $CORRUPT_EXIT_CODE; fi; cat $target )"
            )
        }.getOrElse { return StoreRead.Corrupt("Root read failed") }
        if (result.code == MISSING_EXIT_CODE) return StoreRead.Missing
        if (result.code != 0) return StoreRead.Corrupt("Root read failed (${result.code})")
        return runCatching { StoreRead.Valid(decode(result.output)) }
            .getOrElse { StoreRead.Corrupt(it.message ?: "Corrupt security document") }
    }

    private fun write(document: SecurityDocument, json: String): Boolean {
        SecurityJson.requireVersionedObject(json)
        val directory = LOCK_STORE_DIRECTORY.shellQuote()
        val target = "$LOCK_STORE_DIRECTORY/${document.fileName}".shellQuote()
        val tempTemplate = "$LOCK_STORE_DIRECTORY/${document.fileName}.tmp.XXXXXX".shellQuote()
        val payload = json.shellQuote()
        val command = "( set -eu; umask 077; " +
            "if [ -L $directory ] || [ -L $target ]; then exit $CORRUPT_EXIT_CODE; fi; " +
            "mkdir -p $directory; chown 0:0 $directory; chmod 0700 $directory; " +
            "tmp=\$(mktemp $tempTemplate); " +
            "trap 'rm -f \"\$tmp\"' 0 1 2 3 15; " +
            "printf '%s' $payload > \"\$tmp\"; " +
            "chown 0:0 \"\$tmp\"; chmod 0600 \"\$tmp\"; " +
            "mv -f \"\$tmp\" $target; chown 0:0 $target; chmod 0600 $target; trap - 0 )"
        return runCatching { executor.execute(command).code == 0 }.getOrDefault(false)
    }

    private companion object {
        fun executeRoot(command: String): RootCommandResult {
            val stdout = mutableListOf<String>()
            val result = getRootShell().newJob().add(command).to(stdout, null).exec()
            return RootCommandResult(result.code, stdout.joinToString("\n"))
        }
    }
}
