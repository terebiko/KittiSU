package anhiutangerinee.kittisu.ui.util.downloader

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.domain.model.DownloadState
import anhiutangerinee.kittisu.domain.model.DownloadStatus
import anhiutangerinee.kittisu.domain.model.ExpectedFile
import anhiutangerinee.kittisu.domain.model.ManagerUpdateInfo
import anhiutangerinee.kittisu.domain.usecase.EnqueueDownloadUseCase
import anhiutangerinee.kittisu.domain.usecase.EnqueueManagerUpdateUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveDownloadUseCase
import anhiutangerinee.kittisu.ui.activity.PermissionRequestInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * @author weishu
 * @date 2023/6/22.
 */
fun download(
    context: Context,
    permissionRequestInterface: PermissionRequestInterface,
    url: String,
    fileName: String,
    enqueueDownload: EnqueueDownloadUseCase,
    observeDownload: ObserveDownloadUseCase,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    onProgress: (Int) -> Unit = {},
    canEnqueue: () -> Boolean = { true },
    expected: ExpectedFile? = null,
    onFailed: (DownloadState) -> Unit = {},
) {
    fun startDownloadFile(
        url: String,
        fileName: String,
        onDownloaded: (Uri) -> Unit,
        onDownloading: () -> Unit,
        onProgress: (Int) -> Unit,
    ) {
        if (!canEnqueue()) return
        onDownloading()

        val downloadId = enqueueDownload(url, fileName, expected)

        CoroutineScope(Dispatchers.Main).launch {
            observeDownload(downloadId).collect { state ->
                state ?: return@collect
                onProgress(state.progress)
                if (state.status == DownloadStatus.COMPLETED ||
                    state.status == DownloadStatus.FAILED
                ) {
                    if (state.status == DownloadStatus.COMPLETED) {
                        state.resultUri?.let { onDownloaded(it.toUri()) }
                    } else {
                        onFailed(state)
                    }
                    cancel()
                }
            }
        }
    }

    requestDownloadPermissions(context, permissionRequestInterface) {
        startDownloadFile(
            url = url,
            fileName = fileName,
            onDownloaded = onDownloaded,
            onDownloading = onDownloading,
            onProgress = onProgress,
        )
    }
}

fun downloadManagerUpdate(
    context: Context,
    permissionRequestInterface: PermissionRequestInterface,
    update: ManagerUpdateInfo,
    enqueueManagerUpdate: EnqueueManagerUpdateUseCase,
) {
    requestDownloadPermissions(context, permissionRequestInterface) {
        enqueueManagerUpdate(update)
    }
}

fun downloadFailureMessage(context: Context, state: DownloadState): String = context.getString(
    state.verificationFailure?.resId ?: R.string.download_failed_content,
    state.fileName,
)

private fun requestDownloadPermissions(
    context: Context,
    permissionRequestInterface: PermissionRequestInterface,
    onGranted: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // sdk 32+, require post_notifications permission
        permissionRequestInterface.requestPermission(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            callback = { success ->
                if (!success) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.notification_permission_denied),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@requestPermission
                }

                onGranted()
            },
            requestDescription = context.getString(R.string.notification_permission_description),
        )
    } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) {
        // sdk 32, no need any permission
        onGranted()
    } else {
        // sdk 32-, require write external storage
        permissionRequestInterface.requestPermissions(
            permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ),
            callback = { result ->
                val success = result.all { it.value }
                if (!success) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.storage_permission_denied),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@requestPermissions
                }
                onGranted()
            },
            requestDescription = mapOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE to context.getString(R.string.storage_permission_description),
            ),
        )
    }
}
