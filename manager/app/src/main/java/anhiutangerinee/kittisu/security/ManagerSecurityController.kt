package anhiutangerinee.kittisu.security

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.ksuApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SecurityUiState {
    data object Loading : SecurityUiState
    data object Unconfigured : SecurityUiState
    data class Locked(
        val method: LockMethod,
        val lockdown: Boolean,
        val biometricEnabled: Boolean,
    ) : SecurityUiState

    data class Working(val messageKey: String) : SecurityUiState
    data object Unlocked : SecurityUiState
    data object Resetting : SecurityUiState
    data object RebootRequired : SecurityUiState
    data object Corrupted : SecurityUiState
}

object ManagerSecurity {

    private const val TAG = "ManagerSecurity"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val store by lazy { RootSecurityStore() }

    private val _state = MutableStateFlow<SecurityUiState>(SecurityUiState.Loading)
    val state: StateFlow<SecurityUiState> = _state.asStateFlow()

    private val _lastMessageKey = MutableStateFlow<String?>(null)
    val lastMessageKey: StateFlow<String?> = _lastMessageKey.asStateFlow()

    @Volatile
    private var config: LockConfig? = null

    @Volatile
    private var runtime: LockRuntime = LockRuntime()

    /** Monotonic anchor for the persisted cooldown deadline, valid within one boot. */
    @Volatile
    private var cooldownDeadlineElapsedRealtime: Long = 0

    @Volatile
    private var backgroundDeadlineElapsedRealtime: Long = 0

    val isInitialized: Boolean get() = _state.value != SecurityUiState.Loading

    /** All security documents live behind a blocking root shell; keep them off Main. */
    private suspend fun <T> onIo(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }

    private sealed interface OperationRead {
        data object NONE : OperationRead
        data object CORRUPT : OperationRead
        data class State(val state: OperationState) : OperationRead
    }

    suspend fun initialize() = onIo {
        initializeInternal()
    }

    private suspend fun initializeInternal() {
        val bootId = BootIdReader.currentBootId()

        when (val read = readOperation()) {
            is OperationRead.CORRUPT -> {
                Log.e(TAG, "operation.json unreadable")
                _state.value = SecurityUiState.Corrupted
                return
            }

            is OperationRead.State -> when (read.state) {
                OperationState.REBOOT_REQUIRED -> {
                    val document = readOperationDocument(store)
                    val rebooted = BootIdReader.currentBootId().let { current ->
                        current.isNotEmpty() && document?.bootId?.isNotEmpty() == true &&
                            current != document.bootId
                    }
                    if (rebooted) {
                        store.deleteAll()
                        _state.value = SecurityUiState.Unconfigured
                        return
                    }
                    _state.value = SecurityUiState.RebootRequired
                    return
                }

                OperationState.RESETTING -> {
                    _state.value = SecurityUiState.Resetting
                    val finished =
                        runCatching { SecurityResetController.run(store) }.getOrDefault(false)
                    _state.value = if (finished) {
                        SecurityUiState.RebootRequired
                    } else {
                        Log.e(TAG, "resume reset failed")
                        // Stay gated either way; the next launch retries.
                        SecurityUiState.Resetting
                    }
                    return
                }

                OperationState.LOCKDOWN_ENTERING, OperationState.LOCKDOWN_ACTIVE -> Unit

                OperationState.LOCKDOWN_EXITING -> {
                    // Finish the interrupted exit before publishing any state.
                    val restored = runCatching { LockdownController.exit(store) }.getOrDefault(false)
                    if (!restored) {
                        Log.e(TAG, "interrupted lockdown exit incomplete")
                    }
                }
            }

            OperationRead.NONE -> Unit
        }

        val configRead = store.readConfig()
        if (configRead is StoreRead.Corrupt) {
            Log.e(TAG, "security config corrupt: ${configRead.reason}")
            _state.value = SecurityUiState.Corrupted
            return
        }
        val loadedConfig = (configRead as? StoreRead.Valid)?.value

        if (loadedConfig == null) {
            // No lock configured; clean up any stale runtime.
            _state.value = SecurityUiState.Unconfigured
            return
        }
        config = loadedConfig

        reloadRuntime(bootId)

        if (_state.value is SecurityUiState.Loading) {
            val lockdownPending =
                (readOperation() as? OperationRead.State)?.state?.let {
                    it == OperationState.LOCKDOWN_ENTERING || it == OperationState.LOCKDOWN_ACTIVE
                } == true
            if (lockdownPending) {
                runtime = runtime.copy(lockdown = true)
                scope.launch { LockdownController.resume(store) }
            }
            _state.value = SecurityUiState.Locked(
                method = loadedConfig.method,
                lockdown = runtime.lockdown,
                biometricEnabled = loadedConfig.biometricEnabled,
            )
        }
    }

    fun cooldownRemainingMs(): Long =
        (cooldownDeadlineElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    fun backgroundRemainingMs(): Long =
        (backgroundDeadlineElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    /**
     * Verifies the entered secret. Handles cooldown gating, failure accounting,
     * lockdown triggering and the full success path including lockdown exit.
     */
    suspend fun verify(credential: CharArray): Boolean {
        val currentConfig = config ?: return false

        if (cooldownRemainingMs() > 0) return false

        // PBKDF2 takes hundreds of ms; never run it on the caller (main) thread.
        val verified = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            CredentialCodec.verify(credential, currentConfig.encodedCredential)
        }
        if (!verified) {
            recordFailure()
            _lastMessageKey.value = "security_wrong_secret"
            return false
        }

        return completeUnlock()
    }

    /**
     * Success path shared by secret verification and biometric authentication.
     * The caller must have established user identity already.
     */
    private suspend fun completeUnlock(): Boolean {
        val operationState = (readOperation() as? OperationRead.State)?.state
        if (runtime.lockdown &&
            (operationState == OperationState.LOCKDOWN_ACTIVE ||
                operationState == OperationState.LOCKDOWN_ENTERING)
        ) {
            _state.value = SecurityUiState.Working("security_restoring")
            val restored = runCatching { LockdownController.exit(store) }.getOrDefault(false)
            if (!restored) {
                Log.e(TAG, "lockdown restore incomplete; staying locked")
                _lastMessageKey.value = "security_restore_failed"
                _state.value = lockedState()
                return true // credential itself was correct; no extra failure counted
            }
        }

        clearFailures()
        _lastMessageKey.value = null
        openDynamicSession()
        startTaskService()
        backgroundDeadlineElapsedRealtime = 0
        _state.value = SecurityUiState.Unlocked
        return true
    }

    /** Called after a successful BiometricPrompt result. */
    suspend fun unlockWithBiometric(): Boolean {
        if (_state.value != SecurityUiState.Unlocked &&
            _state.value is SecurityUiState.Locked && cooldownRemainingMs() <= 0
        ) {
            return completeUnlock()
        }
        return false
    }

    fun lock(reason: String = "") {
        if (_state.value == SecurityUiState.Unlocked || reason == "timeout") {
            closeDynamicSession()
            stopTaskService()
        }
        backgroundDeadlineElapsedRealtime = 0
        config?.let { current ->
            _state.value = SecurityUiState.Locked(
                method = current.method,
                lockdown = runtime.lockdown,
                biometricEnabled = current.biometricEnabled,
            )
        }
    }

    /** Called when the activity goes to background: arms the dynamic session timeout. */
    fun onEnterBackground() {
        if (_state.value != SecurityUiState.Unlocked) return
        val timeoutMs = config?.relockTimeoutMillis ?: 0
        backgroundDeadlineElapsedRealtime =
            SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(0)
        if (supportsDynamicSessions()) {
            Natives.armDynamicManagerSessionTimeout(timeoutMs.coerceAtLeast(1))
        }
    }

    /** Called when the activity returns to foreground. */
    fun onEnterForeground() {
        if (_state.value != SecurityUiState.Unlocked) return
        if (supportsDynamicSessions()) {
            Natives.cancelDynamicManagerSessionTimeout()
        }
        if (backgroundDeadlineElapsedRealtime in 1..SystemClock.elapsedRealtime()) {
            lock("timeout")
        }
        backgroundDeadlineElapsedRealtime = 0
    }

    /** Called from ManagerTaskService.onTaskRemoved(). */
    fun onTaskRemoved() {
        closeDynamicSession()
    }

    /** Non-recording credential check used by admin actions in Settings. */
    suspend fun verifySecret(credential: CharArray): Boolean {
        val current = config ?: return false
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            CredentialCodec.verify(credential, current.encodedCredential)
        }
    }

    suspend fun configureLock(newConfig: LockConfig): Boolean = onIo {
        if (!store.writeConfig(newConfig)) return@onIo false
        if (!store.writeRuntime(LockRuntime())) return@onIo false
        config = newConfig
        runtime = LockRuntime()
        cooldownDeadlineElapsedRealtime = 0
        _state.value = SecurityUiState.Unlocked
        true
    }

    suspend fun updateConfig(transform: (LockConfig) -> LockConfig): Boolean = onIo {
        val current = config ?: return@onIo false
        val updated = transform(current)
        if (!store.writeConfig(updated)) return@onIo false
        config = updated
        publishLockedStateIfNecessary(updated)
        true
    }

    suspend fun disableLock(): Boolean = onIo {
        // Refuse while an operation is in progress.
        if (readOperationDocument(store) != null) return@onIo false
        if (!store.deleteAll()) return@onIo false
        config = null
        runtime = LockRuntime()
        cooldownDeadlineElapsedRealtime = 0
        _state.value = SecurityUiState.Unconfigured
        true
    }

    suspend fun beginDestructiveReset(): Boolean {
        _state.value = SecurityUiState.Resetting
        closeDynamicSession()
        val finished = runCatching { SecurityResetController.begin(store) }.getOrDefault(false)
        _state.value = if (finished) {
            config = null
            SecurityUiState.RebootRequired
        } else {
            SecurityUiState.RebootRequired // gated either way; retried on next launch
        }
        return finished
    }

    private fun lockedState(): SecurityUiState {
        val current = config
        return if (current == null) {
            SecurityUiState.Corrupted
        } else {
            SecurityUiState.Locked(current.method, runtime.lockdown, current.biometricEnabled)
        }
    }

    private fun publishLockedStateIfNecessary(updated: LockConfig) {
        val value = _state.value
        if (value is SecurityUiState.Locked) {
            _state.value = SecurityUiState.Locked(
                updated.method,
                runtime.lockdown,
                updated.biometricEnabled,
            )
        }
    }

    private suspend fun recordFailure() = onIo {
        val current = config ?: return@onIo
        val attempts = runtime.failedAttempts + 1
        val cooldown = CooldownPolicy.next(attempts, current.maxFailedAttempts, runtime.cooldownLevel)
        var updated = runtime.copy(failedAttempts = attempts)

        if (cooldown.level > 0) {
            updated = updated.copy(
                cooldownLevel = cooldown.level,
                cooldownDeadlineMillis = SystemClock.elapsedRealtime() + cooldown.durationMillis,
                cooldownBootId = BootIdReader.currentBootId(),
            )
        }
        runtime = updated
        cooldownDeadlineElapsedRealtime = updated.cooldownDeadlineMillis

        val shouldLockdown = attempts >= current.maxFailedAttempts && !runtime.lockdown
        if (shouldLockdown) {
            runtime = updated.copy(lockdown = true)
            store.writeRuntime(runtime)
            closeDynamicSession()
            _state.value = SecurityUiState.Working("security_lockdown_entering")
            runCatching { LockdownController.enter(store) }
        } else {
            store.writeRuntime(runtime)
        }
        _state.value = lockedState()
        Unit
    }

    private suspend fun clearFailures() = onIo {
        runtime = runtime.clearFailures().copy(lockdown = false)
        cooldownDeadlineElapsedRealtime = 0
        store.writeRuntime(runtime)
        Unit
    }

    private suspend fun reloadRuntime(bootId: String) {
        when (val read = store.readRuntime()) {
            is StoreRead.Valid -> {
                var loaded = read.value
                if (loaded.cooldownDeadlineMillis > 0) {
                    if (loaded.cooldownBootId.isNotEmpty() && loaded.cooldownBootId != bootId) {
                        // Reboot during cooldown: fully re-apply the current penalty level.
                        val reapplied = SystemClock.elapsedRealtime() +
                            CooldownPolicy.durationFor(loaded.cooldownLevel)
                        loaded = loaded.copy(cooldownDeadlineMillis = reapplied, cooldownBootId = bootId)
                        store.writeRuntime(loaded)
                    }
                    cooldownDeadlineElapsedRealtime = loaded.cooldownDeadlineMillis
                } else {
                    cooldownDeadlineElapsedRealtime = 0
                }
                runtime = loaded
            }

            is StoreRead.Corrupt -> {
                Log.e(TAG, "runtime corrupt, resetting counters")
                runtime = LockRuntime()
                store.writeRuntime(runtime)
            }

            StoreRead.Missing -> {
                runtime = LockRuntime()
                store.writeRuntime(runtime)
            }
        }
    }

    private suspend fun readOperation(): OperationRead = onIo {
        when (val read = store.readOperation()) {
            is StoreRead.Valid -> {
                val decoded = runCatching { OperationCodec.decode(read.value) }.getOrNull()
                if (decoded != null) {
                    OperationRead.State(decoded.state)
                } else {
                    OperationRead.CORRUPT
                }
            }

            is StoreRead.Corrupt -> OperationRead.CORRUPT
            StoreRead.Missing -> OperationRead.NONE
        }
    }

    private fun supportsDynamicSessions(): Boolean =
        runCatching { Natives.getDynamicManagerSessionStatus() }.getOrNull() != null

    private fun openDynamicSession() {
        runCatching { Natives.openDynamicManagerSession() }
            .onFailure { Log.w(TAG, "openDynamicManagerSession failed", it) }
    }

    private fun closeDynamicSession() {
        runCatching { Natives.closeDynamicManagerSession() }
    }

    private fun startTaskService() {
        runCatching {
            ksuApp.startService(Intent(ksuApp, ManagerTaskService::class.java))
        }
    }

    private fun stopTaskService() {
        runCatching { ksuApp.stopService(Intent(ksuApp, ManagerTaskService::class.java)) }
    }
}
