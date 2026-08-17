package anhiutangerinee.kittisu.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import anhiutangerinee.kittisu.ui.screen.FlashIt
import anhiutangerinee.kittisu.ui.util.downloader.DownloadService
import java.security.SecureRandom

private const val MODULE_LINK_SCHEME = "ksu"
private const val MODULE_LINK_TOKEN_KEY = "module_intent_token"
private val MODULE_ID_PATTERN = Regex("^[A-Za-z][A-Za-z0-9._-]+$")

internal fun isValidModuleId(moduleId: String): Boolean = MODULE_ID_PATTERN.matches(moduleId)

internal sealed interface ModuleDeepLink {
    data class Action(val moduleId: String) : ModuleDeepLink
    data class WebUi(val moduleId: String) : ModuleDeepLink
}

/**
 * Deep link resolution: maps external Intent/Uri to an initial back stack.
 * Call resolve(intent) at Activity start to seed the back stack.
 */
object DeepLinkResolver {
    internal const val INTENT_TOKEN_EXTRA = "module_intent_token"

    @Synchronized
    internal fun intentToken(context: Context): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.getString(MODULE_LINK_TOKEN_KEY, null)?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        val token = Base64.encodeToString(
            ByteArray(32).also { SecureRandom().nextBytes(it) },
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
        )
        prefs.edit().putString(MODULE_LINK_TOKEN_KEY, token).apply()
        return token
    }

    private fun buildModuleUri(context: Context, host: String, moduleId: String): Uri {
        require(isValidModuleId(moduleId)) { "Invalid module ID" }
        return Uri.Builder()
            .scheme(MODULE_LINK_SCHEME)
            .authority(host)
            .appendQueryParameter("id", moduleId)
            .appendQueryParameter("token", intentToken(context))
            .build()
    }

    fun buildActionUri(context: Context, moduleId: String): Uri =
        buildModuleUri(context, "action", moduleId)

    fun buildWebUiUri(context: Context, moduleId: String): Uri =
        buildModuleUri(context, "webui", moduleId)

    internal fun parseModuleDeepLink(context: Context, uri: Uri?): ModuleDeepLink? {
        if (uri?.scheme != MODULE_LINK_SCHEME || !uri.isHierarchical) return null
        val moduleId = uri.getQueryParameter("id")?.takeIf(::isValidModuleId) ?: return null
        if (uri.getQueryParameter("token") != intentToken(context)) return null
        return when (uri.host) {
            "action" -> ModuleDeepLink.Action(moduleId)
            "webui" -> ModuleDeepLink.WebUi(moduleId)
            else -> null
        }
    }

    fun resolve(context: Context, intent: Intent?): List<Route> {
        if (intent == null) return emptyList()
        if (intent.action == DownloadService.ACTION_INSTALL_MODULE) {
            if (intent.getStringExtra(INTENT_TOKEN_EXTRA) != intentToken(context)) {
                return emptyList()
            }
            val uriString = intent.getStringExtra(DownloadService.EXTRA_MODULE_URI)
                ?: return emptyList()
            val uri = uriString.toUri()
            return listOf(Route.Main, Route.Flash(FlashIt.FlashModule(uri)))
        }

        return when (val link = parseModuleDeepLink(context, intent.data)) {
            is ModuleDeepLink.Action -> listOf(Route.Main, Route.ExecuteModuleAction(link.moduleId))
            else -> emptyList()
        }
    }
}

/**
 * Composable that handles deep link intents and updates the back stack accordingly.
 * Should be placed at the root of the NavHost.
 */
@Composable
fun HandleDeepLink(
    intentState: State<Int>,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val currentIntentId by intentState
    val navigator = LocalNavigator.current
    var lastHandledIntentId by rememberSaveable { mutableIntStateOf(-1) }

    LaunchedEffect(currentIntentId) {
        if (currentIntentId != lastHandledIntentId) {
            val intent = activity?.intent
            val initialStack = DeepLinkResolver.resolve(context, intent)
            if (initialStack.isNotEmpty()) {
                navigator.replaceAll(initialStack)
            }
            lastHandledIntentId = currentIntentId
        }
    }
}
