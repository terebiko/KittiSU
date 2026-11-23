package anhiutangerinee.kittisu

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import anhiutangerinee.kittisu.ui.component.resetKsuValidCache
import anhiutangerinee.kittisu.ui.util.KsuCli
import anhiutangerinee.kittisu.ui.util.getRootShell
import anhiutangerinee.kittisu.ui.util.shellQuote
import com.topjohnwu.superuser.ShellUtils
import java.io.File

object KernelPatchNatives {
    init {
        System.loadLibrary("kernelsu")
    }

    external fun nativeReady(superKey: String): Boolean
    external fun nativeKernelPatchVersion(superKey: String): Long
    external fun nativeSu(superKey: String, toUid: Int, scontext: String?): Long
    external fun nativeSuUids(superKey: String): IntArray
    external fun nativeGrantSu(superKey: String, uid: Int, toUid: Int, scontext: String?): Long
    external fun nativeRevokeSu(superKey: String, uid: Int): Long
    external fun nativeSetUidExclude(superKey: String, uid: Int, exclude: Int): Int
    external fun nativeGetUidExclude(superKey: String, uid: Int): Int
    external fun nativeIsSafeMode(superKey: String): Boolean
    external fun nativeSuPath(superKey: String): String
    external fun nativeResetSuPath(superKey: String, path: String): Boolean
}

/**
 * Full runtime bridge for KernelPatch / APatch / FolkPatch patched boot images.
 * Allows SovaSU to detect, authenticate with SuperKey, manage SuperUser grants,
 * manage module exclude/umount, and install/run `apd` modules seamlessly.
 */
object KernelPatchManager {
    private const val TAG = "KernelPatchManager"
    private const val PREFS_NAME = "kp_prefs"
    private const val KEY_SUPERKEY = "super_key"

    const val SUPERCMD = "/system/bin/truncate"
    const val APD_PATH = "/data/adb/apd"
    const val APATCH_FOLDER = "/data/adb/ap/"
    const val APATCH_BIN_FOLDER = "/data/adb/ap/bin/"
    const val PACKAGE_CONFIG_FILE = "/data/adb/ap/package_config"
    const val SU_PATH_FILE = "/data/adb/ap/su_path"
    const val MAGISK_SCONTEXT = "u:r:magisk:s0"
    const val DEFAULT_SU_PATH = "/system/bin/kp"
    const val LEGACY_SU_PATH = "/system/bin/su"

    private const val CSV_HEADER = "pkg,exclude,allow,uid,to_uid,sctx"

    var isKernelPatchActive by mutableStateOf(false)
        private set

    var kpVersion by mutableLongStateOf(0L)
        private set

    var isApdInstalled by mutableStateOf(false)
        private set

    var isSafeMode by mutableStateOf(false)
        private set

    @Volatile
    var superKey: String = ""
        private set

    private val packageConfigLock = Any()

    fun loadSavedSuperKey(): String {
        if (superKey.isNotEmpty()) return superKey
        val saved = runCatching {
            ksuApp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SUPERKEY, "")
                .orEmpty()
        }.getOrDefault("")
        superKey = saved
        return saved
    }

    fun saveSuperKey(key: String) {
        val trimmed = key.trim()
        superKey = trimmed
        runCatching {
            ksuApp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                if (trimmed.isNotEmpty() && trimmed != "su") {
                    putString(KEY_SUPERKEY, trimmed)
                } else {
                    remove(KEY_SUPERKEY)
                }
            }
        }
    }

    fun formatVersion(ver: Long = kpVersion): String {
        if (ver <= 0L) return ""
        val major = (ver shr 16) and 0xFF
        val minor = (ver shr 8) and 0xFF
        val patch = ver and 0xFF
        return "$major.$minor.$patch"
    }

    /**
     * Automatically checks whether KernelPatch is active using the saved SuperKey
     * or UID-authorized `"su"` supercall.
     */
    fun tryAutoActivate(): Boolean {
        val saved = loadSavedSuperKey()
        if (saved.isNotEmpty() && activateWithSuperKey(saved, persist = false)) {
            return true
        }
        return activateWithSuperKey("su", persist = false)
    }

    /**
     * Verifies [key] against the running KernelPatch kernel via `sc_ready`.
     * When valid, elevates current process/root shell and checks/installs `apd`.
     */
    fun activateWithSuperKey(key: String, persist: Boolean = true): Boolean {
        val trimmed = key.trim()
        val effective = trimmed.ifEmpty { "su" }
        val ready = runCatching { KernelPatchNatives.nativeReady(effective) }.getOrDefault(false)
        if (!ready) {
            return false
        }

        if (persist && trimmed.isNotEmpty() && trimmed != "su") {
            saveSuperKey(trimmed)
        } else {
            superKey = effective
        }

        val ver = runCatching { KernelPatchNatives.nativeKernelPatchVersion(effective) }.getOrDefault(0L)
        val safe = runCatching { KernelPatchNatives.nativeIsSafeMode(effective) }.getOrDefault(false)

        kpVersion = ver
        isSafeMode = safe
        isKernelPatchActive = true
        resetKsuValidCache()

        // Refresh root shell so it binds via KernelPatch SUPERCMD/kpatch
        runCatching { KsuCli.refresh() }

        // Check whether apd is already installed; if not, install bundled apd automatically
        val apdExists = checkApdInstalled()
        if (!apdExists) {
            installApd()
        } else {
            isApdInstalled = true
        }

        Log.i(TAG, "KernelPatch active: v${formatVersion(ver)}, apd=$isApdInstalled, safeMode=$safe")
        return true
    }

    fun checkApdInstalled(): Boolean {
        val installed = runCatching {
            ShellUtils.fastCmdResult(getRootShell(), "[ -x $APD_PATH ]")
        }.getOrDefault(false)
        isApdInstalled = installed
        return installed
    }

    /**
     * Installs bundled APatch/FolkPatch daemon (`libapd.so`) and helper tools
     * (`libbusybox.so`, `libresetprop.so`, `libkptools.so`) to `/data/adb/` so
     * module management & boot persistence work identically to FolkPatch/APatch.
     */
    fun installApd(): Boolean {
        if (!isKernelPatchActive) return false
        val nativeDir = ksuApp.applicationInfo.nativeLibraryDir
        val apdLib = File(nativeDir, "libapd.so")
        if (!apdLib.isFile) {
            Log.w(TAG, "libapd.so not present for current ABI")
            return false
        }

        val cmds = mutableListOf(
            "mkdir -p $APATCH_BIN_FOLDER ${APATCH_FOLDER}log",
            "rm -f $APD_PATH",
            "cp -f ${apdLib.absolutePath.shellQuote()} $APD_PATH",
            "chmod 0755 $APD_PATH",
            "ln -sf $APD_PATH ${APATCH_BIN_FOLDER}apd",
            "restorecon $APD_PATH 2>/dev/null || true",
        )

        val busyboxLib = File(nativeDir, "libbusybox.so")
        if (busyboxLib.isFile) {
            cmds += "rm -f ${APATCH_BIN_FOLDER}busybox"
            cmds += "cp -f ${busyboxLib.absolutePath.shellQuote()} ${APATCH_BIN_FOLDER}busybox"
            cmds += "chmod 0755 ${APATCH_BIN_FOLDER}busybox"
        }

        val resetpropLib = File(nativeDir, "libresetprop.so")
        if (resetpropLib.isFile) {
            cmds += "rm -f ${APATCH_BIN_FOLDER}resetprop"
            cmds += "cp -f ${resetpropLib.absolutePath.shellQuote()} ${APATCH_BIN_FOLDER}resetprop"
            cmds += "chmod 0755 ${APATCH_BIN_FOLDER}resetprop"
        }

        val kptoolsLib = File(nativeDir, "libkptools.so")
        if (kptoolsLib.isFile) {
            cmds += "cp -f ${kptoolsLib.absolutePath.shellQuote()} ${APATCH_BIN_FOLDER}kptools"
            cmds += "chmod 0755 ${APATCH_BIN_FOLDER}kptools"
        }

        cmds += "touch $PACKAGE_CONFIG_FILE"
        cmds += "touch $SU_PATH_FILE"
        cmds += "[ -s $SU_PATH_FILE ] || echo $LEGACY_SU_PATH > $SU_PATH_FILE"
        cmds += "restorecon -R $APATCH_FOLDER 2>/dev/null || true"

        val result = runCatching {
            getRootShell().newJob().add(*cmds.toTypedArray()).exec()
        }.getOrNull()

        val key = superKey.ifBlank { "su" }
        runCatching {
            KernelPatchNatives.nativeResetSuPath(key, DEFAULT_SU_PATH)
            KernelPatchNatives.nativeResetSuPath(key, LEGACY_SU_PATH)
        }

        val ok = result?.isSuccess == true && checkApdInstalled()
        Log.i(TAG, "installApd result=$ok")
        return ok
    }

    fun getSuperuserCount(): Int {
        if (!isKernelPatchActive) return 0
        val key = superKey.ifBlank { "su" }
        return runCatching { KernelPatchNatives.nativeSuUids(key).size }.getOrDefault(0)
    }

    fun uidShouldUmount(uid: Int): Boolean {
        if (!isKernelPatchActive) return false
        val key = superKey.ifBlank { "su" }
        return runCatching { KernelPatchNatives.nativeGetUidExclude(key, uid) == 1 }.getOrDefault(false)
    }

    fun getAppProfile(pkg: String, uid: Int): Natives.Profile {
        val key = superKey.ifBlank { "su" }
        val allowedUids = runCatching { KernelPatchNatives.nativeSuUids(key) }.getOrDefault(IntArray(0))
        val allowSu = allowedUids.contains(uid)
        val excluded = if (allowSu) {
            false
        } else {
            runCatching { KernelPatchNatives.nativeGetUidExclude(key, uid) == 1 }.getOrDefault(false)
        }
        return Natives.Profile(
            name = pkg,
            currentUid = uid,
            allowSu = allowSu,
            rootUseDefault = true,
            uid = 0,
            gid = 0,
            context = MAGISK_SCONTEXT,
            nonRootUseDefault = !excluded,
            umountModules = excluded,
        )
    }

    fun setAppProfile(profile: Natives.Profile): Boolean {
        if (!isKernelPatchActive) return false
        val key = superKey.ifBlank { "su" }
        val uid = profile.currentUid
        val sctx = profile.context.ifBlank { MAGISK_SCONTEXT }.let {
            if (it == Natives.KERNEL_SU_DOMAIN) MAGISK_SCONTEXT else it
        }

        val ok = if (profile.allowSu) {
            val grantRc = runCatching {
                KernelPatchNatives.nativeGrantSu(key, uid, profile.uid, sctx)
            }.getOrDefault(-1L)
            runCatching { KernelPatchNatives.nativeSetUidExclude(key, uid, 0) }
            grantRc == 0L
        } else {
            runCatching { KernelPatchNatives.nativeRevokeSu(key, uid) }
            val excludeFlag = if (profile.umountModules && !profile.nonRootUseDefault) 1 else 0
            val excludeRc = runCatching {
                KernelPatchNatives.nativeSetUidExclude(key, uid, excludeFlag)
            }.getOrDefault(0)
            excludeRc >= 0
        }

        if (ok) {
            persistPackageConfig(
                pkg = profile.name,
                uid = uid,
                allow = if (profile.allowSu) 1 else 0,
                exclude = if (!profile.allowSu && profile.umountModules && !profile.nonRootUseDefault) 1 else 0,
                toUid = profile.uid,
                sctx = sctx
            )
        }
        return ok
    }

    /**
     * Persists the UID grant/exclude entry in `/data/adb/ap/package_config`
     * (standard APatch / FolkPatch CSV format) so root grants survive reboot.
     */
    private fun persistPackageConfig(
        pkg: String,
        uid: Int,
        allow: Int,
        exclude: Int,
        toUid: Int,
        sctx: String,
    ) {
        synchronized(packageConfigLock) {
            runCatching {
                val shell = getRootShell()
                val existing = ArrayList<String>()
                shell.newJob()
                    .add("cat $PACKAGE_CONFIG_FILE 2>/dev/null || true")
                    .to(existing, null)
                    .exec()

                val rows = LinkedHashMap<Int, String>()
                for (line in existing) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("pkg,")) continue
                    val parts = trimmed.split(',', limit = 6)
                    if (parts.size < 6) continue
                    val rowUid = parts[3].trim().toIntOrNull() ?: continue
                    rows[rowUid] = trimmed
                }

                if (allow == 0 && exclude == 0) {
                    rows.remove(uid)
                } else {
                    val safePkg = pkg.replace(",", "_")
                    val safeSctx = sctx.replace(",", "_").ifBlank { MAGISK_SCONTEXT }
                    rows[uid] = "$safePkg,$exclude,$allow,$uid,$toUid,$safeSctx"
                }

                val content = buildString {
                    append(CSV_HEADER).append('\n')
                    rows.values.forEach { append(it).append('\n') }
                }
                val tmp = File(ksuApp.cacheDir, "ap_pkg_cfg.tmp")
                tmp.writeText(content)
                shell.newJob().add(
                    "mkdir -p $APATCH_FOLDER",
                    "cp -f ${tmp.absolutePath.shellQuote()} $PACKAGE_CONFIG_FILE",
                    "chmod 0644 $PACKAGE_CONFIG_FILE"
                ).exec()
                tmp.delete()
            }.onFailure {
                Log.w(TAG, "persistPackageConfig failed", it)
            }
        }
    }
}
