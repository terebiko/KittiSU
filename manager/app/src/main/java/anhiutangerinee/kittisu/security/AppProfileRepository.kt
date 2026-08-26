package anhiutangerinee.kittisu.security

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.os.IBinder
import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.KsuService
import com.resukisu.zako.IKsuInterface
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val uid: Int,
)

object AppProfileRepository {

    suspend fun listInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val binder = connectKsuService() ?: return@withContext emptyList()
        try {
            val service = IKsuInterface.Stub.asInterface(binder)
            val total = service.packageCount
            val result = mutableListOf<InstalledApp>()
            var start = 0
            val pageSize = 100
            while (start < total) {
                val page: List<PackageInfo> = service.getPackages(start, pageSize)
                if (page.isEmpty()) break
                page.forEach { info ->
                    info.applicationInfo?.let { appInfo ->
                        result += InstalledApp(info.packageName, appInfo.uid)
                    }
                }
                start += page.size
            }
            result.filter { it.packageName != ksuApp.packageName }
        } finally {
            // libsu requires bind/unbind on the main thread; stopping from an IO
            // thread crashes the process (RootService.checkThread).
            runCatching {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    RootService.stop(Intent(ksuApp, KsuService::class.java))
                }
            }
        }
    }

    fun currentProfile(app: InstalledApp): Natives.Profile? = runCatching {
        Natives.getAppProfile(app.packageName, app.uid)
    }.getOrNull()

    fun setAllowSu(app: InstalledApp, allowSu: Boolean): Boolean = runCatching {
        val profile = Natives.getAppProfile(app.packageName, app.uid)
        if (profile.allowSu == allowSu) return@runCatching true
        Natives.setAppProfile(profile.copy(allowSu = allowSu))
    }.getOrDefault(false)

    private suspend fun connectKsuService(): IBinder? = suspendCoroutine { continuation ->
        val connection = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {}
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                continuation.resume(binder)
            }
        }
        val intent = Intent(ksuApp, KsuService::class.java)
        try {
            val task = RootService.bindOrTask(intent, Shell.EXECUTOR, connection)
            task?.let { Shell.getShell().execTask(it) }
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }
}
