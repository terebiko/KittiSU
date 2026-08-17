package zako.zako.zako.zakoui.screen.moreSettings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.MainActivity
import anhiutangerinee.kittisu.ui.theme.BackgroundManager
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeColors
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.saveAndApplyCustomBackground
import anhiutangerinee.kittisu.ui.theme.saveCustomBackground
import anhiutangerinee.kittisu.ui.theme.saveDynamicColorSpec
import anhiutangerinee.kittisu.ui.theme.saveDynamicColorState
import anhiutangerinee.kittisu.ui.theme.saveDynamicPaletteStyle
import anhiutangerinee.kittisu.ui.theme.saveThemeColors
import anhiutangerinee.kittisu.ui.theme.saveThemeMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import zako.zako.zako.zakoui.screen.moreSettings.state.MoreSettingsState
import zako.zako.zako.zakoui.screen.moreSettings.util.toggleLauncherIcon

/**
 * 更多设置处理器
 */
class MoreSettingsHandlers(
    val activity: MainActivity,
    private val prefs: SharedPreferences,
    private val state: MoreSettingsState
) {

    /**
     * 初始化设置
     */
    fun initializeSettings() {
        // 加载设置
        CardConfig.load(activity)
        state.cardAlpha = CardConfig.cardAlpha
        state.isCustomBackgroundEnabled = ThemeConfig.customBackgroundUri != null
        state.isFullScreenBackgroundEnabled = ThemeConfig.isFullScreenBackgroundEnabled
        state.fullScreenBackgroundDim = ThemeConfig.fullScreenBackgroundDim
        state.fullScreenBackgroundBlur = ThemeConfig.fullScreenBackgroundBlur

        // 设置主题模式
        state.themeMode = when (ThemeConfig.forceDarkMode) {
            true -> 2
            false -> 1
            null -> 0
        }

        // 确保卡片样式跟随主题模式
        when (state.themeMode) {
            2 -> { // 深色
                CardConfig.isUserDarkModeEnabled = true
                CardConfig.isUserLightModeEnabled = false
            }
            1 -> { // 浅色
                CardConfig.isUserDarkModeEnabled = false
                CardConfig.isUserLightModeEnabled = true
            }
            0 -> { // 跟随系统
                CardConfig.isUserDarkModeEnabled = false
                CardConfig.isUserLightModeEnabled = false
            }
        }

        // 如果启用了系统跟随且系统是深色模式，应用深色模式默认值
        if (state.themeMode == 0 && state.systemIsDark) {
            CardConfig.setThemeDefaults(true)
        }

        state.currentDpi = prefs.getInt("app_dpi", state.systemDpi)
        state.tempDpi = state.currentDpi

        CardConfig.save(activity)
    }

    /**
     * 处理主题模式变更
     */
    fun handleThemeModeChange(index: Int) {
        state.themeMode = index
        val newThemeMode = when (index) {
            0 -> null // 跟随系统
            1 -> false // 浅色
            2 -> true // 深色
            else -> null
        }
        activity.saveThemeMode(newThemeMode)
        ThemeConfig.updateTheme(darkMode = newThemeMode)

        when (index) {
            2 -> { // 深色
                ThemeConfig.updateTheme(darkMode = true)
                CardConfig.updateThemePreference(darkMode = true, lightMode = false)
                CardConfig.setThemeDefaults(true)
                CardConfig.save(activity)
            }
            1 -> { // 浅色
                ThemeConfig.updateTheme(darkMode = false)
                CardConfig.updateThemePreference(darkMode = false, lightMode = true)
                CardConfig.setThemeDefaults(false)
                CardConfig.save(activity)
            }
            0 -> { // 跟随系统
                ThemeConfig.updateTheme(darkMode = null)
                CardConfig.updateThemePreference(darkMode = null, lightMode = null)
                val isNightModeActive = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                CardConfig.setThemeDefaults(isNightModeActive)
                CardConfig.save(activity)
            }
        }
    }

    /**
     * 处理主题色变更
     */
    fun handleThemeColorChange(theme: ThemeColors) {
        activity.saveThemeColors(when (theme) {
            ThemeColors.Green -> "green"
            ThemeColors.Purple -> "purple"
            ThemeColors.Orange -> "orange"
            ThemeColors.Pink -> "pink"
            ThemeColors.Gray -> "gray"
            ThemeColors.Yellow -> "yellow"
            else -> "default"
        })
        ThemeConfig.updateTheme(theme = theme)
    }

    /**
     * 处理动态颜色变更
     */
    fun handleDynamicColorChange(enabled: Boolean) {
        state.useDynamicColor = enabled
        activity.saveDynamicColorState(enabled)
        ThemeConfig.updateTheme(dynamicColor = enabled)
    }

    fun handleDynamicColorSpecChange(spec: ColorSpec.SpecVersion) {
        state.dynamicColorSpec = spec
        activity.saveDynamicColorSpec(spec)
    }

    fun handleDynamicPaletteStyleChange(style: PaletteStyle) {
        state.dynamicPaletteStyle = style
        activity.saveDynamicPaletteStyle(style)
    }

    /**
     * 获取DPI大小友好名称
     */
    @Composable
    fun getDpiFriendlyName(dpi: Int): String {
        return when (dpi) {
            240 -> stringResource(R.string.dpi_size_small)
            320 -> stringResource(R.string.dpi_size_medium)
            420 -> stringResource(R.string.dpi_size_large)
            560 -> stringResource(R.string.dpi_size_extra_large)
            else -> stringResource(R.string.dpi_size_custom)
        }
    }

    /**
     * 应用 DPI 设置
     */
    fun handleDpiApply() {
        if (state.tempDpi != state.currentDpi) {
            prefs.edit {
                putInt("app_dpi", state.tempDpi)
            }

            state.currentDpi = state.tempDpi
            Toast.makeText(
                activity,
                activity.getString(R.string.dpi_applied_success, state.tempDpi),
                Toast.LENGTH_SHORT
            ).show()

            activity.settingsStateFlow.value = activity.settingsStateFlow.value.copy(
                dpi = state.tempDpi
            )
        }
    }

    /**
     * 处理自定义背景
     */
    fun handleCustomBackground(transformedUri: Uri) {
        activity.saveAndApplyCustomBackground(transformedUri)
        state.isCustomBackgroundEnabled = true
        CardConfig.cardAlpha = 0.55f
        BackgroundManager.saveEnableBlur(activity, false)
        BackgroundManager.saveUseBackgroundSeedColor(activity, true)
        CardConfig.cardElevation = 0.dp
        CardConfig.isCustomBackgroundEnabled = true
        CardConfig.save(activity)

        state.cardAlpha = CardConfig.cardAlpha

        Toast.makeText(
            activity,
            activity.getString(R.string.background_set_success),
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * 处理移除自定义背景
     */
    fun handleRemoveCustomBackground() {
        activity.saveCustomBackground(null)
        state.isCustomBackgroundEnabled = false
        CardConfig.cardAlpha = 1f
        CardConfig.isCustomAlphaSet = false
        CardConfig.isCustomBackgroundEnabled = false
        CardConfig.save(activity)
        ThemeConfig.preventBackgroundRefresh = false

        state.cardAlpha = CardConfig.cardAlpha

        BackgroundManager.saveEnableBlur(activity, false)
        BackgroundManager.saveUseBackgroundSeedColor(activity, false)

        activity.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("prevent_background_refresh", false)
        }

        Toast.makeText(
            activity,
            activity.getString(R.string.background_removed),
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * 处理卡片透明度变更
     */
    fun handleCardAlphaChange(newValue: Float) {
        state.cardAlpha = newValue
        CardConfig.cardAlpha = newValue
        CardConfig.isCustomAlphaSet = true
        prefs.edit {
            putBoolean("is_custom_alpha_set", true)
            putFloat("card_alpha", newValue)
        }
    }

    /**
     * 处理全屏背景
     */
    fun handleFullScreenBackground(uri: Uri) {
        BackgroundManager.saveFullScreenBackground(activity, uri)
        state.isFullScreenBackgroundEnabled = true
        state.fullScreenBackgroundDim = 0.3f
        BackgroundManager.saveFullScreenBackgroundDim(activity, 0.3f)
        BackgroundManager.saveFullScreenBackgroundBlur(activity, false)

        CardConfig.updateFullScreenBackground(true)
        if (!CardConfig.isCustomAlphaSet) {
            CardConfig.cardAlpha = 0.55f
        }
        CardConfig.save(activity)
        state.cardAlpha = CardConfig.cardAlpha

        Toast.makeText(
            activity,
            activity.getString(R.string.background_set_success),
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * 处理移除全屏背景
     */
    fun handleRemoveFullScreenBackground() {
        BackgroundManager.clearFullScreenBackground(activity)
        state.isFullScreenBackgroundEnabled = false
        state.fullScreenBackgroundDim = 0f
        BackgroundManager.saveFullScreenBackgroundDim(activity, 0f)
        BackgroundManager.saveFullScreenBackgroundBlur(activity, false)

        CardConfig.updateFullScreenBackground(false)
        if (!CardConfig.isCustomBackgroundEnabled) {
            CardConfig.cardAlpha = 1f
            CardConfig.isCustomAlphaSet = false
        }
        CardConfig.save(activity)
        state.cardAlpha = CardConfig.cardAlpha

        Toast.makeText(
            activity,
            activity.getString(R.string.background_removed),
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * 处理全屏背景暗度变更
     */
    fun handleFullScreenDimChange(newValue: Float) {
        state.fullScreenBackgroundDim = newValue
        BackgroundManager.saveFullScreenBackgroundDim(activity, newValue)
    }

    /**
     * 处理全屏背景模糊变更
     */
    fun handleFullScreenBlurChange(newValue: Boolean) {
        state.fullScreenBackgroundBlur = newValue
        BackgroundManager.saveFullScreenBackgroundBlur(activity, newValue)
    }

    /**
     * 处理图标变更
     */
    fun handleIconChange(newValue: Boolean) {
        prefs.edit { putBoolean("use_alt_icon", newValue) }
        state.useAltIcon = newValue
        toggleLauncherIcon(activity, newValue)
        Toast.makeText(activity, activity.getString(R.string.icon_switched), Toast.LENGTH_SHORT).show()
    }

    /**
     * 处理简洁模式变更
     */
    fun handleSimpleModeChange(newValue: Boolean) {
        prefs.edit { putBoolean("is_simple_mode", newValue) }
        state.isSimpleMode = newValue
    }

    /**
     * 处理隐藏版本变更
     */
    fun handleHideVersionChange(newValue: Boolean) {
        prefs.edit { putBoolean("is_hide_version", newValue) }
        state.isHideVersion = newValue
    }

    /**
     * 处理隐藏其他信息变更
     */
    fun handleHideOtherInfoChange(newValue: Boolean) {
        prefs.edit { putBoolean("is_hide_other_info", newValue) }
        state.isHideOtherInfo = newValue
        activity.settingsStateFlow.value = activity.settingsStateFlow.value.copy(
            isHideOtherInfo = newValue
        )
    }

    /**
     * 处理隐藏SuSFS状态变更
     */
    fun handleHideSusfsStatusChange(newValue: Boolean) {
        prefs.edit { putBoolean("is_hide_susfs_status", newValue) }
        state.isHideSusfsStatus = newValue
    }

    /**
     * 处理隐藏Zygisk实现变更
     */
    fun handleHideZygiskImplementChange(newValue: Boolean) {
        prefs.edit { putBoolean("is_hide_zygisk_Implement", newValue) }
        state.isHideZygiskImplement = newValue
    }

    /**
     * 处理隐藏元模块实现变更
     */
    fun handleHideMetaModuleImplementChange(newValue: Boolean) {
        prefs.edit { putBoolean("is_hide_meta_module_Implement", newValue) }
        state.isHideMetaModuleImplement = newValue
    }

    /**
     * 处理隐藏链接卡片变更
     */
    fun handleHideLinkCardChange(newValue: Boolean) {
        prefs.edit { putBoolean("is_hide_link_card", newValue) }
        state.isHideLinkCard = newValue
    }

    /**
     * 处理隐藏标签行变更
     */
    fun handleHideTagRowChange(newValue: Boolean) {
        prefs.edit { putBoolean("is_hide_tag_row", newValue) }
        state.isHideTagRow = newValue
    }

    /**
     * 处理显示更多模块信息变更
     */
    fun handleShowMoreModuleInfoChange(newValue: Boolean) {
        prefs.edit { putBoolean("show_more_module_info", newValue) }
        state.showMoreModuleInfo = newValue
    }

    /**
     * 处理模块横幅背景变更
     */
    fun handleUseBannerChange(newValue: Boolean) {
        prefs.edit { putBoolean("use_banner", newValue) }
        state.useBanner = newValue
    }
}
