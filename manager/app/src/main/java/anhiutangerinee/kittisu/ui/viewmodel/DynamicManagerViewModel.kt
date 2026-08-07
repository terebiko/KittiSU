package anhiutangerinee.kittisu.ui.viewmodel

import android.content.pm.PackageInfo
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.util.clearDynamicManager
import anhiutangerinee.kittisu.ui.util.getDynamicManagerConfig
import anhiutangerinee.kittisu.ui.util.setDynamicManagerApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class DynamicManagerApp(
    val label: String,
    val packageName: String,
    val packageInfo: PackageInfo,
    val apkPath: String,
    val selected: Boolean,
    val changeable: Boolean,
)

class DynamicManagerViewModel : ViewModel() {
    var config by mutableStateOf<Natives.DynamicManagerConfig?>(null)
        private set
    var apps by mutableStateOf<List<DynamicManagerApp>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    suspend fun refresh() = withContext(Dispatchers.IO) {
        loading = true
        if (SuperUserViewModel.getCachedApps(includeManager = true).isEmpty()) {
            ViewModelProvider(ksuApp)[SuperUserViewModel::class.java].fetchAppList()
        }

        config = getDynamicManagerConfig()
        val managerIndexes = Natives.getManagersList()?.managers
            ?.associate { it.uid to it.signatureIndex }
            .orEmpty()

        apps = SuperUserViewModel.getCachedApps(includeManager = true).mapNotNull { app ->
            val info = app.packageInfo.applicationInfo ?: return@mapNotNull null
            if (!File(info.nativeLibraryDir, "libksud.so").isFile) return@mapNotNull null
            val index = managerIndexes[info.uid % 100000]
            DynamicManagerApp(
                label = app.label,
                packageName = app.packageName,
                packageInfo = app.packageInfo,
                apkPath = info.sourceDir,
                selected = index == DYNAMIC_SIGNATURE_INDEX,
                changeable = index == null || index == DYNAMIC_SIGNATURE_INDEX,
            )
        }.sortedWith(compareByDescending<DynamicManagerApp> { it.selected }.thenBy { it.label.lowercase() })
        loading = false
    }

    suspend fun select(app: DynamicManagerApp): Boolean = withContext(Dispatchers.IO) {
        setDynamicManagerApk(app.apkPath).also { success ->
            if (success) {
                config = getDynamicManagerConfig()
                updateSelection(app.packageName)
            }
        }
    }

    suspend fun clearSelection(): Boolean = withContext(Dispatchers.IO) {
        clearDynamicManager().also { success ->
            if (success) {
                config = null
                updateSelection(null)
            }
        }
    }

    private fun updateSelection(packageName: String?) {
        apps = apps.map { app ->
            if (app.selected || app.packageName == packageName) {
                app.copy(selected = app.packageName == packageName, changeable = true)
            } else {
                app
            }
        }.sortedWith(compareByDescending<DynamicManagerApp> { it.selected }.thenBy { it.label.lowercase() })
    }

    private companion object {
        const val DYNAMIC_SIGNATURE_INDEX = 255
    }
}
