package anhiutangerinee.kittisu.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.system.Os
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anhiutangerinee.kittisu.KernelPatchManager
import anhiutangerinee.kittisu.KernelVersion
import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.getKernelVersion
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.data.susfs.SuSFSConfigHelper
import anhiutangerinee.kittisu.ui.util.getDynamicManagerConfig
import anhiutangerinee.kittisu.ui.util.getMetaModuleImplement
import anhiutangerinee.kittisu.ui.util.getModuleCount
import anhiutangerinee.kittisu.ui.util.getSELinuxStatus
import anhiutangerinee.kittisu.ui.util.getSuperuserCount
import anhiutangerinee.kittisu.ui.util.getZygiskImplement
import anhiutangerinee.kittisu.ui.util.isSELinuxPermissive
import anhiutangerinee.kittisu.ui.util.rootAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {

    // 系统状态
    data class SystemStatus(
        val isManager: Boolean = false,
        val ksuVersion: Int? = null,
        val ksuFullVersion : String? = null,
        val lkmMode: Boolean? = null,
        val kernelVersion: KernelVersion = getKernelVersion(),
        val isRootAvailable: Boolean = false,
        val requireNewKernel: Boolean = false,
        val isSELinuxPermissive: Boolean = false,
        val isOfficialSignature: Boolean = true,
        val kernelPatchImplement: Natives.KernelPatchImplement = Natives.KernelPatchImplement.NO_KERNEL_PATCH_SUPPORT,
        val isKernelPatchActive: Boolean = false,
        val kernelPatchVersion: String? = null,
        val isApdInstalled: Boolean = false,
    )

    // 系统信息
    data class SystemInfo(
        val kernelRelease: String = "",
        val androidVersion: String = "",
        val deviceModel: String = "",
        val managerVersion: Pair<String, Long> = Pair("", 0L),
        val selinuxStatus: String = "",
        val susfsEnabled: Boolean = false,
        val susfsVersionSupported: Boolean = false,
        val susfsVersion: String = "",
        val susfsFeatures: String = "",
        val superuserCount: Int = 0,
        val moduleCount: Int = 0,
        val managersList: Natives.ManagersList? = null,
        val isDynamicSignEnabled: Boolean = false,
        val zygiskImplement: String = "",
        val metaModuleImplement: String = "",
        val seccompStatus: Int = -1,
    )

    // 状态变量
    var systemStatus by mutableStateOf(SystemStatus())
        private set

    var systemInfo by mutableStateOf(SystemInfo())
        private set

    var isSimpleMode by mutableStateOf(false)
        private set
    var isHideVersion by mutableStateOf(false)
        private set
    var isHideOtherInfo by mutableStateOf(false)
        private set
    var isHideSusfsStatus by mutableStateOf(false)
        private set
    var isHideZygiskImplement by mutableStateOf(false)
        private set
    var isHideMetaModuleImplement by mutableStateOf(false)
        private set
    var isHideLinkCard by mutableStateOf(false)
        private set
    var isCoreDataLoaded by mutableStateOf(false)
        private set
    var isExtendedDataLoaded by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    private var loadingJobs = mutableListOf<Job>()
    private var refreshInFlight = false

    fun loadUserSettings(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            isSimpleMode = settingsPrefs.getBoolean("is_simple_mode", false)
            isHideVersion = settingsPrefs.getBoolean("is_hide_version", false)
            isHideOtherInfo = settingsPrefs.getBoolean("is_hide_other_info", false)
            isHideSusfsStatus = settingsPrefs.getBoolean("is_hide_susfs_status", false)
            isHideLinkCard = settingsPrefs.getBoolean("is_hide_link_card", false)
            isHideZygiskImplement = settingsPrefs.getBoolean("is_hide_zygisk_Implement", false)
            isHideMetaModuleImplement = settingsPrefs.getBoolean("is_hide_meta_module_Implement", false)
        }
    }

    fun loadCoreData() {
        if (isCoreDataLoaded) return

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val kernelVersion = getKernelVersion()
                val isManager = try {
                    Natives.isManager
                } catch (_: Exception) {
                    false
                }

                val ksuVersion = if (isManager) Natives.version else null

                // If native KernelSU is not installed, auto-probe KernelPatch (APatch / FolkPatch)
                val isKpActive = if (ksuVersion == null) {
                    runCatching { KernelPatchManager.tryAutoActivate() }.getOrDefault(false)
                } else {
                    false
                }
                val kpVersionStr = if (isKpActive) {
                    KernelPatchManager.formatVersion().ifBlank { null }
                } else null
                val apdInstalled = if (isKpActive) KernelPatchManager.isApdInstalled else false

                val fullVersion = try {
                    Natives.getFullVersion()
                } catch (_: Exception) {
                    "Unknown"
                }

                val lkmMode = ksuVersion?.let {
                    if (kernelVersion.isGKI()) Natives.isLkmMode else null
                }

                val isRootAvailable = try {
                    rootAvailable()
                } catch (_: Exception) {
                    false
                }

                val requireNewKernel = try {
                    isManager && Natives.requireNewKernel()
                } catch (_: Exception) {
                    false
                }

                val isSELinuxPermissive = try {
                    isSELinuxPermissive()
                } catch (_: Exception) {
                    false
                }

                val isOfficialSignature = true

                systemStatus = SystemStatus(
                    isManager = isManager || isKpActive,
                    ksuVersion = ksuVersion,
                    ksuFullVersion = "$fullVersion (${Natives.version})",
                    lkmMode = lkmMode,
                    kernelVersion = kernelVersion,
                    isRootAvailable = isRootAvailable,
                    requireNewKernel = requireNewKernel,
                    isSELinuxPermissive = isSELinuxPermissive,
                    isOfficialSignature = isOfficialSignature,
                    kernelPatchImplement = Natives.getKernelPatchImplement(),
                    isKernelPatchActive = isKpActive,
                    kernelPatchVersion = kpVersionStr,
                    isApdInstalled = apdInstalled,
                )

                isCoreDataLoaded = true
            } catch (_: Exception) {
            }
        }
        loadingJobs.add(job)
    }

    fun loadExtendedData(context: Context) {
        if (isExtendedDataLoaded) return

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val (kernelRelease, androidVersion, deviceModel, managerVersion, selinuxStatus, seccompStatus) = loadBasicSystemInfo(
                    context
                )
                val moduleInfo = if (!isSimpleMode) loadModuleInfo() else null
                val susfsInfo = if (!isHideSusfsStatus) loadSuSFSInfo() else null
                val managerInfo = loadManagerInfo()

                // Apply all extended fields in a single snapshot update to avoid
                // four back-to-back HomePage recompositions during startup.
                val updated = systemInfo.copy(
                    kernelRelease = kernelRelease,
                    androidVersion = androidVersion,
                    deviceModel = deviceModel,
                    managerVersion = managerVersion,
                    selinuxStatus = selinuxStatus,
                    seccompStatus = seccompStatus,
                    superuserCount = moduleInfo?.first ?: systemInfo.superuserCount,
                    moduleCount = moduleInfo?.second ?: systemInfo.moduleCount,
                    zygiskImplement = moduleInfo?.third ?: systemInfo.zygiskImplement,
                    metaModuleImplement = moduleInfo?.fourth ?: systemInfo.metaModuleImplement,
                    susfsEnabled = susfsInfo?.first ?: systemInfo.susfsEnabled,
                    susfsVersionSupported = susfsInfo?.first ?: systemInfo.susfsVersionSupported,
                    susfsVersion = susfsInfo?.second ?: systemInfo.susfsVersion,
                    susfsFeatures = susfsInfo?.third ?: systemInfo.susfsFeatures,
                    managersList = managerInfo.first,
                    isDynamicSignEnabled = managerInfo.second,
                )
                withContext(Dispatchers.Main.immediate) {
                    systemInfo = updated
                    isExtendedDataLoaded = true
                }
            } catch (_: Exception) {}
        }
        loadingJobs.add(job)
    }

    fun refreshData(
        context: Context,
        refreshUI: Boolean = false
    ) {
        // Application.onCreate and HomePage both ask for data. Without this guard
        // the second call cancels and restarts the first one's jobs.
        if (!refreshUI && (refreshInFlight || isCoreDataLoaded)) return

        refreshInFlight = true
        viewModelScope.launch {
            if (refreshUI)
                isRefreshing = true

            try {
                // 取消正在进行的加载任务
                loadingJobs.forEach { it.cancel() }
                loadingJobs.clear()

                // 重置状态
                if (refreshUI) {
                    isCoreDataLoaded = false
                    isExtendedDataLoaded = false
                }

                // 重新加载用户设置
                loadUserSettings(context)

                // 重新加载核心数据
                loadCoreData()

                // 重新加载扩展数据
                loadExtendedData(context)

                // Keep the pull-to-refresh indicator visible until both jobs finish.
                loadingJobs.toList().joinAll()
            } catch (_: Exception) {
                // 静默处理错误
            } finally {
                refreshInFlight = false
                isRefreshing = false
            }
        }
    }

    fun unlockKernelPatch(
        context: Context,
        superKey: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                KernelPatchManager.activateWithSuperKey(superKey, persist = true)
            }.getOrDefault(false)
            if (ok) {
                withContext(Dispatchers.Main) {
                    refreshData(context, refreshUI = true)
                }
            }
            withContext(Dispatchers.Main) {
                onResult(ok)
            }
        }
    }

    fun installApdDaemon(
        context: Context,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching { KernelPatchManager.installApd() }.getOrDefault(false)
            if (ok) {
                withContext(Dispatchers.Main) {
                    refreshData(context, refreshUI = true)
                }
            }
            withContext(Dispatchers.Main) {
                onResult(ok)
            }
        }
    }

    private suspend fun loadBasicSystemInfo(context: Context): Tuple6<String, String, String, Pair<String, Long>, String, Int> {
        return withContext(Dispatchers.IO) {
            val uname = try {
                Os.uname()
            } catch (_: Exception) {
                null
            }

            val deviceModel = try {
                getDeviceModel()
            } catch (_: Exception) {
                "Unknown"
            }

            val managerVersion = try {
                getManagerVersion(context)
            } catch (_: Exception) {
                Pair("Unknown", 0L)
            }

            val selinuxStatus = try {
                getSELinuxStatus(ksuApp.applicationContext)
            } catch (_: Exception) {
                "Unknown"
            }

            val seccompStatus = runCatching {
                Os.prctl(21 /* PR_GET_SECCOMP */, 0, 0, 0, 0)
            }.getOrDefault(-1)

            Tuple6(
                uname?.release ?: "Unknown",
                Build.VERSION.RELEASE ?: "Unknown",
                deviceModel,
                managerVersion,
                selinuxStatus,
                seccompStatus
            )
        }
    }

    private suspend fun loadModuleInfo(): Tuple4<Int, Int, String, String> {
        return withContext(Dispatchers.IO) {
            val superuserCount = try {
                getSuperuserCount()
            } catch (_: Exception) {
                0
            }

            val moduleCount = try {
                getModuleCount()
            } catch (_: Exception) {
                0
            }

            val zygiskImplement = try {
                getZygiskImplement()
            } catch (_: Exception) {
                "None"
            }

            val metaModuleImplement = try {
                getMetaModuleImplement()
            } catch (_: Exception) {
                "None"
            }

            Tuple4(superuserCount, moduleCount, zygiskImplement, metaModuleImplement)
        }
    }

    private suspend fun loadSuSFSInfo(): Triple<Boolean, String, String> {
        return withContext(Dispatchers.IO) {
            val susfsVersion = runCatching { SuSFSConfigHelper.showVersion() }.getOrDefault("")
            val susfsEnabled = susfsVersion.isNotEmpty()

            if (!susfsEnabled) {
                return@withContext Triple(false, "", "")
            }

            val susfsFeatures =
                runCatching { SuSFSConfigHelper.showEnabledFeatures() }.getOrDefault("")

            Triple(true, susfsVersion, susfsFeatures)
        }
    }

    private suspend fun loadManagerInfo(): Pair<Natives.ManagersList?, Boolean> {
        return withContext(Dispatchers.IO) {
            val dynamicSignConfig = runCatching { getDynamicManagerConfig() }.getOrNull()
            val isDynamicSignEnabled = dynamicSignConfig?.isValid() == true

            val managersList = try {
                Natives.getManagersList()
            } catch (_: Exception) {
                null
            }

            Pair(managersList, isDynamicSignEnabled)
        }
    }

    @SuppressLint("PrivateApi")
    private fun getDeviceModel(): String {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
            val marketNameKeys = listOf(
                "ro.product.marketname",
                "ro.vendor.oplus.market.name",
                "ro.vivo.market.name",
                "ro.config.marketing_name"
            )
            var result = getDeviceInfo()
            for (key in marketNameKeys) {
                try {
                    val marketName = getMethod.invoke(null, key, "") as String
                    if (marketName.isNotEmpty()) {
                        result = marketName
                        break
                    }
                } catch (_: Exception) {
                }
            }
            result
        } catch (

            _: Exception) {
            getDeviceInfo()
        }
    }

    private fun getDeviceInfo(): String {
        return try {
            var manufacturer = Build.MANUFACTURER ?: "Unknown"
            manufacturer = manufacturer[0].uppercaseChar().toString() + manufacturer.substring(1)

            val brand = Build.BRAND ?: ""
            if (brand.isNotEmpty() && !brand.equals(Build.MANUFACTURER, ignoreCase = true)) {
                manufacturer += " " + brand[0].uppercaseChar() + brand.substring(1)
            }

            val model = Build.MODEL ?: ""
            if (model.isNotEmpty()) {
                manufacturer += " $model "
            }

            manufacturer
        } catch (_: Exception) {
            "Unknown Device"
        }
    }

    private fun getManagerVersion(context: Context): Pair<String, Long> {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo)
            val versionName = packageInfo.versionName ?: "Unknown"
            Pair(versionName, versionCode)
        } catch (_: Exception) {
            Pair("Unknown", 0L)
        }
    }

    data class Tuple6<T1, T2, T3, T4, T5, T6>(
        val first: T1,
        val second: T2,
        val third: T3,
        val fourth: T4,
        val fifth: T5,
        val sixth: T6
    )

    data class Tuple5<T1, T2, T3, T4, T5>(
        val first: T1,
        val second: T2,
        val third: T3,
        val fourth: T4,
        val fifth: T5
    )

    data class Tuple4<T1, T2, T3, T4>(
        val first: T1,
        val second: T2,
        val third: T3,
        val fourth: T4
    )

    override fun onCleared() {
        super.onCleared()
        loadingJobs.forEach { it.cancel() }
        loadingJobs.clear()
    }
}
