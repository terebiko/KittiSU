package anhiutangerinee.kittisu.ui.component

import androidx.compose.runtime.Composable
import anhiutangerinee.kittisu.domain.model.KernelStatus

@Composable
inline fun KsuIsValid(
    status: KernelStatus,
    content: @Composable () -> Unit,
) {
    if (status.isFullFeatured) {
        content()
    }
}
