package anhiutangerinee.kittisu.security

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Idempotent lockdown lifecycle persisted through operation.json so an interrupted
 * enter/exit can resume on the next launch without double-applying side effects.
 */
/** Returns the decoded operation document, or null when absent/corrupt. */
internal fun readOperationDocument(store: RootSecurityStore): OperationDocument? =
    when (val read = store.readOperation()) {
        is StoreRead.Valid -> runCatching { OperationCodec.decode(read.value) }.getOrNull()
        else -> null
    }

object LockdownController {

    private const val TAG = "LockdownController"

    suspend fun enter(store: RootSecurityStore): Boolean = withContext(Dispatchers.IO) {
        // Resume support: if an interrupted LOCKDOWN_ENTERING exists, keep its snapshot so
        // already-revoked apps are still restorable later; otherwise build one from scratch.
        val previous = readOperationDocument(store)
            ?.takeIf { it.state == OperationState.LOCKDOWN_ENTERING }
        val previouslySnapshottedApps = previous?.apps.orEmpty()
            .associateBy { it.uid }
        val previouslyEnabledModules = previous?.enabledModules.orEmpty().toSet()

        val grantedApps = AppProfileRepository.listInstalledApps()
            .filter { AppProfileRepository.currentProfile(it)?.allowSu == true }
            .filter { it.uid !in previouslySnapshottedApps }
        val enabledModules = ModuleSecurityRepository.listModules()
            .filter { it.enabled && !it.remove }
            .map { it.dirId }
            .filter { it !in previouslyEnabledModules }

        val snapshot = OperationDocument(
            state = OperationState.LOCKDOWN_ENTERING,
            apps = previouslySnapshottedApps.values + grantedApps.map {
                GrantedAppSnapshot(it.packageName, it.uid)
            },
            enabledModules = (previouslyEnabledModules + enabledModules).toList(),
        )
        if (!store.writeOperation(OperationCodec.encode(snapshot))) {
            Log.e(TAG, "failed to persist lockdown snapshot")
            return@withContext false
        }

        var allOk = true
        val failedIds = mutableListOf<String>()
        grantedApps.forEach { app ->
            if (!AppProfileRepository.setAllowSu(app, false)) {
                Log.e(TAG, "failed to revoke ${app.packageName}")
                failedIds += app.packageName
                allOk = false
            }
        }
        enabledModules.forEach { id ->
            if (!ModuleSecurityRepository.setEnabled(id, false)) {
                Log.e(TAG, "failed to disable module $id")
                failedIds += id
                allOk = false
            }
        }
        ModuleSecurityRepository.saveFeatures()

        if (allOk) {
            store.writeOperation(
                OperationCodec.encode(snapshot.copy(state = OperationState.LOCKDOWN_ACTIVE))
            )
        } else {
            // Keep LOCKDOWN_ENTERING with the failed ids recorded; resume() retries them.
            store.writeOperation(
                OperationCodec.encode(
                    snapshot.copy(state = OperationState.LOCKDOWN_ENTERING, failedIds = failedIds)
                )
            )
        }
        true
    }

    /** Returns true when the lockdown has fully ended. */
    suspend fun exit(store: RootSecurityStore): Boolean = withContext(Dispatchers.IO) {
        val document = readOperationDocument(store) ?: return@withContext false

        if (document.state != OperationState.LOCKDOWN_ACTIVE &&
            document.state != OperationState.LOCKDOWN_EXITING &&
            document.state != OperationState.LOCKDOWN_ENTERING
        ) {
            return@withContext true
        }

        store.writeOperation(
            OperationCodec.encode(document.copy(state = OperationState.LOCKDOWN_EXITING))
        )

        val installed = AppProfileRepository.listInstalledApps().associateBy { it.uid }
        var allOk = true
        document.apps.forEach { snap ->
            val current = installed[snap.uid]
            if (current == null || current.packageName != snap.packageName) {
                return@forEach // package uninstalled or uid changed: never re-grant blindly
            }
            if (!AppProfileRepository.setAllowSu(current, true)) {
                Log.e(TAG, "failed to restore ${snap.packageName}")
                allOk = false
            }
        }
        val existing = ModuleSecurityRepository.listModules().associateBy { it.dirId }
        document.enabledModules.forEach { id ->
            existing[id]?.let { module ->
                if (!module.remove && !module.enabled) {
                    if (!ModuleSecurityRepository.setEnabled(id, true)) {
                        Log.e(TAG, "failed to re-enable module $id")
                        allOk = false
                    }
                }
            }
        }
        ModuleSecurityRepository.saveFeatures()

        if (allOk && !store.deleteOperation()) {
            Log.e(TAG, "failed to clear completed lockdown operation")
            return@withContext false
        }
        allOk
    }

    /** Resumes an interrupted LOCKDOWN_ENTERING after process death mid-revocation. */
    suspend fun resume(store: RootSecurityStore) = withContext(Dispatchers.IO) {
        if (readOperationDocument(store)?.state == OperationState.LOCKDOWN_ENTERING) {
            enter(store)
        }
    }
}
