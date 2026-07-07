package anhiutangerinee.kittisu.ui.util.module

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PresetIndex(
    val version: Int,
    val updatedAt: String,
    val files: List<String>
) : Parcelable

@Parcelize
data class PresetFile(
    val name: String,
    val preset: List<PresetEntry>
) : Parcelable

@Parcelize
data class PresetEntry(
    val id: String,
    val destination: String,
    val author: String? = null,
    val committer: String? = null,
    val team: String? = null,
    val requiresRebootAtEnd: Boolean = false,
    val modules: List<PresetModule>,
    val postInstallName: String? = null,
    val postInstall: String? = null
) : Parcelable

@Parcelize
data class PresetModule(
    val moduleName: String,
    val moduleId: String,
    val moduleVersion: String? = null,
    val directUrl: String,
    val stopIfFail: Boolean = true,
    val rebootAfter: Boolean = false,
    val dependsOn: List<String> = emptyList(),
    val requirement: PresetRequirement? = null
) : Parcelable

@Parcelize
data class PresetRequirement(
    val susfs: String? = null,
    val kernelsu: String? = null,
    val android: String? = null,
    val metadata: String? = null
) : Parcelable

@Parcelize
data class PresetSource(
    val id: String,
    val name: String,
    val baseUrl: String,
    val isOfficial: Boolean,
    val isEnabled: Boolean = true
) : Parcelable

@Parcelize
data class LoadedPreset(
    val sourceId: String,
    val fileName: String,
    val presetEntry: PresetEntry,
    val isLocal: Boolean = false
) : Parcelable

data class ModuleInstallPlan(
    val modules: List<PlanModule>,
    val requiresReboot: Boolean
)

data class PlanModule(
    val presetModule: PresetModule,
    val downloadUrl: String,
    val cacheUri: Uri? = null,
    val isInstalled: Boolean = false,
    val skip: Boolean = false
)
