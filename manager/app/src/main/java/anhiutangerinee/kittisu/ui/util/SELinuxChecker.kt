package anhiutangerinee.kittisu.ui.util

import android.content.Context
import anhiutangerinee.kittisu.R
import com.topjohnwu.superuser.ShellUtils
import java.io.File

private fun readSelinuxEnforce(): Int? {
    val localFile = File("/sys/fs/selinux/enforce")
    val direct = runCatching {
        if (localFile.canRead()) localFile.readText().trim().toIntOrNull() else null
    }.getOrNull()
    if (direct != null) return direct

    val enforceStr = runCatching {
        ShellUtils.fastCmd(getRootShell(), "getenforce").trim()
    }.getOrDefault("")
    return when {
        enforceStr.equals("Enforcing", ignoreCase = true) -> 1
        enforceStr.equals("Permissive", ignoreCase = true) -> 0
        enforceStr.equals("Disabled", ignoreCase = true) -> -1
        else -> null
    }
}

fun isSELinuxPermissive(): Boolean {
    return readSelinuxEnforce() == 0
}

fun getSELinuxStatus(context: Context): String {
    return when (readSelinuxEnforce()) {
        1 -> context.getString(R.string.selinux_status_enforcing)
        0 -> context.getString(R.string.selinux_status_permissive)
        -1 -> context.getString(R.string.selinux_status_disabled)
        else -> {
            val localFile = File("/sys/fs/selinux/enforce")
            when {
                !localFile.exists() -> context.getString(R.string.selinux_status_disabled)
                !localFile.canRead() -> context.getString(R.string.selinux_status_enforcing)
                else -> context.getString(R.string.unknown)
            }
        }
    }
}