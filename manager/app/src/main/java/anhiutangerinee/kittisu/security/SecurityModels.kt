package anhiutangerinee.kittisu.security

import org.json.JSONObject

enum class LockMethod {
    PASSWORD,
    PIN,
    PATTERN,
}

data class LockConfig(
    val method: LockMethod,
    val encodedCredential: String,
    val relockTimeoutMillis: Long,
    val maxFailedAttempts: Int,
    val biometricEnabled: Boolean,
)

data class LockRuntime(
    val failedAttempts: Int = 0,
    val cooldownLevel: Int = 0,
    val cooldownDeadlineMillis: Long = 0,
    val cooldownBootId: String = "",
    val lockdown: Boolean = false,
) {
    fun clearFailures() = copy(
        failedAttempts = 0,
        cooldownLevel = 0,
        cooldownDeadlineMillis = 0,
        cooldownBootId = "",
    )
}

data class Cooldown(val level: Int, val durationMillis: Long)

object CooldownPolicy {
    private const val INITIAL_MILLIS = 30_000L
    private const val MAX_MILLIS = 10 * 60_000L

    fun next(failedAttempts: Int, maxFailedAttempts: Int, currentLevel: Int): Cooldown {
        require(failedAttempts >= 0)
        require(maxFailedAttempts > 0)
        require(currentLevel >= 0)
        if (failedAttempts < maxFailedAttempts) return Cooldown(0, 0)

        val level = if (currentLevel == Int.MAX_VALUE) currentLevel else currentLevel + 1
        return Cooldown(level, durationFor(level))
    }

    fun durationFor(level: Int): Long {
        var duration = INITIAL_MILLIS
        repeat(minOf(level - 1, 5)) {
            duration = minOf(duration * 2, MAX_MILLIS)
        }
        return duration
    }
}

object SecurityJson {
    private const val VERSION = 1
    private val configKeys = setOf(
        "version",
        "method",
        "encodedCredential",
        "relockTimeoutMillis",
        "maxFailedAttempts",
        "biometricEnabled",
    )
    private val runtimeKeys = setOf(
        "version",
        "failedAttempts",
        "cooldownLevel",
        "cooldownDeadlineMillis",
        "cooldownBootId",
        "lockdown",
    )

    fun encode(config: LockConfig): String {
        validate(config)
        return JSONObject()
            .put("version", VERSION)
            .put("method", config.method.name)
            .put("encodedCredential", config.encodedCredential)
            .put("relockTimeoutMillis", config.relockTimeoutMillis)
            .put("maxFailedAttempts", config.maxFailedAttempts)
            .put("biometricEnabled", config.biometricEnabled)
            .toString()
    }

    fun decodeConfig(json: String): LockConfig = decode(json) { objectValue ->
        requireKeys(objectValue, configKeys)
        require(strictInt(objectValue, "version") == VERSION) { "Unsupported config version" }
        val config = LockConfig(
            method = runCatching { LockMethod.valueOf(strictString(objectValue, "method")) }
                .getOrElse { throw IllegalArgumentException("Unsupported lock method", it) },
            encodedCredential = strictString(objectValue, "encodedCredential"),
            relockTimeoutMillis = strictLong(objectValue, "relockTimeoutMillis"),
            maxFailedAttempts = strictInt(objectValue, "maxFailedAttempts"),
            biometricEnabled = strictBoolean(objectValue, "biometricEnabled"),
        )
        validate(config)
        config
    }

    fun encode(runtime: LockRuntime): String {
        validate(runtime)
        return JSONObject()
            .put("version", VERSION)
            .put("failedAttempts", runtime.failedAttempts)
            .put("cooldownLevel", runtime.cooldownLevel)
            .put("cooldownDeadlineMillis", runtime.cooldownDeadlineMillis)
            .put("cooldownBootId", runtime.cooldownBootId)
            .put("lockdown", runtime.lockdown)
            .toString()
    }

    fun decodeRuntime(json: String): LockRuntime = decode(json) { objectValue ->
        requireKeys(objectValue, runtimeKeys)
        require(strictInt(objectValue, "version") == VERSION) { "Unsupported runtime version" }
        val runtime = LockRuntime(
            failedAttempts = strictInt(objectValue, "failedAttempts"),
            cooldownLevel = strictInt(objectValue, "cooldownLevel"),
            cooldownDeadlineMillis = strictLong(objectValue, "cooldownDeadlineMillis"),
            cooldownBootId = strictString(objectValue, "cooldownBootId"),
            lockdown = strictBoolean(objectValue, "lockdown"),
        )
        validate(runtime)
        runtime
    }

    internal fun requireVersionedObject(json: String) {
        decode(json) { objectValue ->
            require(strictInt(objectValue, "version") == VERSION) { "Unsupported document version" }
        }
    }

    private fun validate(config: LockConfig) {
        require(CredentialCodec.isSupported(config.encodedCredential)) { "Unsupported credential encoding" }
        require(config.relockTimeoutMillis >= 0) { "Negative relock timeout" }
        require(config.maxFailedAttempts > 0) { "Invalid maximum failed attempts" }
    }

    private fun validate(runtime: LockRuntime) {
        require(runtime.failedAttempts >= 0) { "Negative failed attempts" }
        require(runtime.cooldownLevel >= 0) { "Negative cooldown level" }
        require(runtime.cooldownDeadlineMillis >= 0) { "Negative cooldown deadline" }
        require(
            runtime.cooldownDeadlineMillis == 0L ||
                (runtime.cooldownLevel > 0 && runtime.cooldownBootId.isNotBlank())
        ) { "Cooldown deadline requires a level and boot ID" }
    }

    private inline fun <T> decode(json: String, block: (JSONObject) -> T): T = try {
        block(JSONObject(json))
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("Corrupt security JSON", error)
    }

    private fun requireKeys(objectValue: JSONObject, expected: Set<String>) {
        val actual = objectValue.keys().asSequence().toSet()
        require(actual == expected) { "Unexpected security JSON fields" }
    }

    private fun strictString(objectValue: JSONObject, key: String): String =
        (objectValue.get(key) as? String) ?: throw IllegalArgumentException("Invalid $key")

    private fun strictBoolean(objectValue: JSONObject, key: String): Boolean =
        (objectValue.get(key) as? Boolean) ?: throw IllegalArgumentException("Invalid $key")

    private fun strictInt(objectValue: JSONObject, key: String): Int {
        val value = strictLong(objectValue, key)
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "Invalid $key" }
        return value.toInt()
    }

    private fun strictLong(objectValue: JSONObject, key: String): Long =
        when (val value = objectValue.get(key)) {
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Int -> value.toLong()
            is Long -> value
            else -> throw IllegalArgumentException("Invalid $key")
        }
}
