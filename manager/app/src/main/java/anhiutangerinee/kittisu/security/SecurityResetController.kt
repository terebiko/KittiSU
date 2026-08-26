package anhiutangerinee.kittisu.security

import android.util.Log
import anhiutangerinee.kittisu.ui.util.clearDynamicManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Destructive password reset: revokes every root grant, uninstalls every module,
 * wipes the dynamic manager config permanently and only then removes the lock secret.
 * Progress is persisted so an interrupted reset resumes instead of leaving a half-reset device.
 */
object SecurityResetController {

    private const val TAG = "SecurityReset"
    private val PHASES = listOf("revoke", "dynamic-manager", "modules", "wipe-lock")

    suspend fun begin(store: RootSecurityStore): Boolean = withContext(Dispatchers.IO) {
        val bootId = BootIdReader.currentBootId()
        if (!store.writeOperation(
                OperationCodec.encode(
                    OperationDocument(OperationState.RESETTING, phase = PHASES.first(), bootId = bootId)
                )
            )
        ) {
            return@withContext false
        }
        run(store)
    }

    /**
     * Executes or resumes the destructive reset.
     * @return true when the device reached REBOOT_REQUIRED.
     */
    suspend fun run(store: RootSecurityStore): Boolean = withContext(Dispatchers.IO) {
        var document = readOperationDocument(store)
            ?.takeIf { it.state == OperationState.RESETTING }
            ?: return@withContext false

        if (atLeast(document.phase, "revoke")) {
            val apps = AppProfileRepository.listInstalledApps()
                .filter { AppProfileRepository.currentProfile(it)?.allowSu == true }
            apps.forEach { app ->
                if (!AppProfileRepository.setAllowSu(app, false)) {
                    Log.e(TAG, "reset: failed to revoke ${app.packageName}")
                    return@withContext false // retry next launch; state still RESETTING/revoke
                }
            }
            ModuleSecurityRepository.saveFeatures()
            document = persistPhase(store, document, "dynamic-manager")
        }

        if (atLeast(document.phase, "dynamic-manager")) {
            Natives.closeDynamicManagerSession()
            if (!clearDynamicManager()) {
                Log.e(TAG, "reset: failed to clear dynamic manager")
                return@withContext false
            }
            document = persistPhase(store, document, "modules")
        }

        if (atLeast(document.phase, "modules")) {
            val modules = ModuleSecurityRepository.listModules().filter { !it.remove }
            modules.forEach { module ->
                if (!ModuleSecurityRepository.uninstall(module.dirId)) {
                    Log.e(TAG, "reset: failed to uninstall ${module.dirId}")
                    return@withContext false
                }
            }
            document = persistPhase(store, document, "wipe-lock")
        }

        if (document.phase == "wipe-lock") {
            // Only now, after all destructive work succeeded, drop the secret itself.
            // operation.json is intentionally kept so the reboot gate survives.
            if (!store.deleteConfigAndRuntime()) return@withContext false
            store.writeOperation(
                OperationCodec.encode(
                    OperationDocument(OperationState.REBOOT_REQUIRED, bootId = document.bootId)
                )
            )
        }
        true
    }

    /** True once the device has actually rebooted after a completed reset. */
    suspend fun isRebootCompleted(store: RootSecurityStore): Boolean =
        withContext(Dispatchers.IO) {
            val document = readOperationDocument(store) ?: return@withContext true
            if (document.state != OperationState.REBOOT_REQUIRED) return@withContext true
            val currentBootId = BootIdReader.currentBootId()
            currentBootId.isNotEmpty() && document.bootId.isNotEmpty() &&
                currentBootId != document.bootId
        }

    private fun atLeast(phase: String, target: String): Boolean {
        val index = PHASES.indexOf(phase)
        if (index < 0) return false
        return index <= PHASES.indexOf(target)
    }

    private fun persistPhase(
        store: RootSecurityStore,
        document: OperationDocument,
        phase: String,
    ): OperationDocument {
        val updated = document.copy(phase = phase)
        store.writeOperation(OperationCodec.encode(updated))
        return updated
    }
}
