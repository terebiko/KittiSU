package anhiutangerinee.kittisu.data.kernel

import android.app.Application
import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.Natives.KernelPatchImplementation
import anhiutangerinee.kittisu.data.shell.KsuCliRepository
import anhiutangerinee.kittisu.data.system.isSELinuxPermissive
import anhiutangerinee.kittisu.domain.model.KernelFeatureSettings
import anhiutangerinee.kittisu.domain.model.KernelStatus
import anhiutangerinee.kittisu.domain.model.ManagerRecord
import anhiutangerinee.kittisu.domain.model.ManagerRuntimeInfo
import anhiutangerinee.kittisu.getKernelVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KernelRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    suspend fun getStatus(): KernelStatus = withContext(Dispatchers.IO) {
        val kernelVersion = getKernelVersion()
        val isManager = runCatching { Natives.isManager }.getOrDefault(false)
        val ksuVersion = if (isManager) Natives.version else null
        val kernelUapi = if (isManager) Natives.kernelUAPIVersion else null
        val managerUapi = runCatching { Natives.managerUAPIVersion }.getOrDefault(1)
        val fullVersion = runCatching { Natives.getFullVersion() }.getOrDefault("Unknown")
        val isRootAvailable = runCatching { ksuCliRepository.rootAvailable() }.getOrDefault(false)
        KernelStatus(
            isManager = isManager,
            ksuVersion = ksuVersion,
            managerUAPIVersion = managerUapi,
            kernelUAPIVersion = kernelUapi,
            ksuFullVersion = "$fullVersion (${Natives.version}/$kernelUapi)",
            lkmMode = ksuVersion?.let { if (kernelVersion.isGKI()) Natives.isLkmMode else null },
            kernelVersion = kernelVersion,
            isRootAvailable = isRootAvailable,
            isFullFeatured = isRootAvailable && runCatching { Natives.isFullFeatured() }
                .getOrDefault(false),
            isSELinuxPermissive = runCatching { isSELinuxPermissive() }.getOrDefault(false),
            isOfficialSignature = runCatching {
                ksuCliRepository.isOfficialSignature(application.packageResourcePath)
            }.getOrDefault(false),
            kernelPatchImplementation = runCatching {
                Natives.getKernelPatchImplementation()
            }.getOrDefault(KernelPatchImplementation.NONE),
            hookType = runCatching { Natives.getHookType() }.getOrDefault(""),
            isSafeMode = runCatching { Natives.isSafeMode }.getOrDefault(false),
            isLateLoadMode = runCatching { Natives.isLateLoadMode }.getOrDefault(false),
            isPrBuild = runCatching { Natives.isPrBuild }.getOrDefault(false),
        )
    }

    suspend fun getManagerRuntimeInfo(): ManagerRuntimeInfo = withContext(Dispatchers.IO) {
        ManagerRuntimeInfo(
            managers = runCatching { Natives.getManagersList()?.managers.orEmpty() }
                .getOrDefault(emptyList())
                .map { ManagerRecord(it.uid, it.signatureIndex) },
            dynamicSignatureEnabled = runCatching {
                Natives.getDynamicManager()?.isValid() == true
            }.getOrDefault(false),
        )
    }

    suspend fun getFeatureSettings(): KernelFeatureSettings = withContext(Dispatchers.IO) {
        KernelFeatureSettings(
            suEnabled = runCatching { Natives.isSuEnabled() }.getOrDefault(false),
            kernelUmountEnabled = runCatching { Natives.isKernelUmountEnabled() }.getOrDefault(false),
            suLogEnabled = runCatching { Natives.isSuLogEnabled() }.getOrDefault(false),
            selinuxHideEnabled = runCatching { Natives.isSelinuxHideEnabled() }.getOrDefault(false),
            defaultUmountModules = runCatching { Natives.isDefaultUmountModules() }.getOrDefault(
                false,
            ),
        )
    }

    suspend fun setSuEnabled(enabled: Boolean): Boolean = saveFeature {
        Natives.setSuEnabled(enabled)
    }

    suspend fun setKernelUmountEnabled(enabled: Boolean): Boolean = saveFeature {
        Natives.setKernelUmountEnabled(enabled)
    }

    suspend fun setShellSuAdbOnlyEnabled(enabled: Boolean): Boolean = saveFeature {
        ksuCliRepository.execKsud("feature set shell_su_adb_only ${if (enabled) 1 else 0}", true)
    }

    suspend fun setSuLogEnabled(enabled: Boolean): Boolean = saveFeature {
        Natives.setSuLogEnabled(enabled)
    }

    suspend fun setSelinuxHideEnabled(enabled: Boolean): Int = withContext(Dispatchers.IO) {
        Natives.setSelinuxHideEnabled(enabled).also {
            ksuCliRepository.execKsud("feature save", true)
        }
    }

    suspend fun setDefaultUmountModules(enabled: Boolean): Boolean = withContext(Dispatchers.IO) { Natives.setDefaultUmountModules(enabled) }

    fun isLateLoadMode(): Boolean = runCatching { Natives.isLateLoadMode }.getOrDefault(false)

    private suspend fun saveFeature(block: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        block().also { success ->
            if (success) ksuCliRepository.execKsud("feature save", true)
        }
    }
}
