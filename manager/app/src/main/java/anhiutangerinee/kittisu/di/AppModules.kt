package anhiutangerinee.kittisu.di

import coil.ImageLoader
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import anhiutangerinee.kittisu.BuildConfig
import anhiutangerinee.kittisu.data.AppSettingsRepository
import anhiutangerinee.kittisu.data.application.ApplicationControlRepository
import anhiutangerinee.kittisu.data.application.DynamicManagerRepository
import anhiutangerinee.kittisu.data.count.CountRepository
import anhiutangerinee.kittisu.data.download.DownloadRepository
import anhiutangerinee.kittisu.data.file.ModuleFileRepository
import anhiutangerinee.kittisu.data.flash.FlashRepository
import anhiutangerinee.kittisu.data.kernel.KernelRepository
import anhiutangerinee.kittisu.data.kernel.UmountRepository
import anhiutangerinee.kittisu.data.logging.BugreportRepository
import anhiutangerinee.kittisu.data.logging.SulogRepository
import anhiutangerinee.kittisu.data.module.ModuleActionRepository
import anhiutangerinee.kittisu.data.module.ModuleCatalogRepository
import anhiutangerinee.kittisu.data.module.ModuleIconRepository
import anhiutangerinee.kittisu.data.module.ModulePreferencesRepository
import anhiutangerinee.kittisu.data.module.ModuleReleaseRepository
import anhiutangerinee.kittisu.data.module.ModuleRepository
import anhiutangerinee.kittisu.data.module.RepositorySourceStore
import anhiutangerinee.kittisu.data.module.registry.KernelSuRegistryRepository
import anhiutangerinee.kittisu.data.network.NetworkRequestRepository
import anhiutangerinee.kittisu.data.network.NetworkStatusRepository
import anhiutangerinee.kittisu.data.network.WebResourceRepository
import anhiutangerinee.kittisu.data.packageinfo.AppIconDataSource
import anhiutangerinee.kittisu.data.packageinfo.InstalledPackageCache
import anhiutangerinee.kittisu.data.packageinfo.InstalledPackageRepository
import anhiutangerinee.kittisu.data.packageinfo.RootServiceRepository
import anhiutangerinee.kittisu.data.packageinfo.SuperUserRepository
import anhiutangerinee.kittisu.data.profile.ProfileRepository
import anhiutangerinee.kittisu.data.profile.ProfileTemplateRepository
import anhiutangerinee.kittisu.data.script.ScriptRepository
import anhiutangerinee.kittisu.data.settings.LocaleHelper
import anhiutangerinee.kittisu.data.settings.LocaleRepository
import anhiutangerinee.kittisu.data.settings.SettingsPlatformRepository
import anhiutangerinee.kittisu.data.shell.KsuCliRepository
import anhiutangerinee.kittisu.data.shell.ShortcutRepository
import anhiutangerinee.kittisu.data.startup.ApplicationInitializationRepository
import anhiutangerinee.kittisu.data.startup.StartupRepository
import anhiutangerinee.kittisu.data.susfs.SuSFSConfigHelper
import anhiutangerinee.kittisu.data.susfs.SuSFSRepository
import anhiutangerinee.kittisu.data.system.HomeRuntimeRepository
import anhiutangerinee.kittisu.data.system.HomeStateRepository
import anhiutangerinee.kittisu.data.text.HanziToPinyin
import anhiutangerinee.kittisu.data.theme.MonetCompatColorSource
import anhiutangerinee.kittisu.data.theme.ThemeRepository
import anhiutangerinee.kittisu.data.update.ManagerUpdateRepository
import anhiutangerinee.kittisu.data.webui.WebUiRepository
import anhiutangerinee.kittisu.domain.text.TextTransliterator
import anhiutangerinee.kittisu.domain.usecase.AddModuleRepositoryUseCase
import anhiutangerinee.kittisu.domain.usecase.AddUmountPathUseCase
import anhiutangerinee.kittisu.domain.usecase.ApplyLanguageUseCase
import anhiutangerinee.kittisu.domain.usecase.BackupAllowlistUseCase
import anhiutangerinee.kittisu.domain.usecase.CalculateInstalledModuleSizeUseCase
import anhiutangerinee.kittisu.domain.usecase.CheckFlashModuleMountUseCase
import anhiutangerinee.kittisu.domain.usecase.CheckManagerUpdateUseCase
import anhiutangerinee.kittisu.domain.usecase.CleanSulogUseCase
import anhiutangerinee.kittisu.domain.usecase.ClearDynamicManagerUseCase
import anhiutangerinee.kittisu.domain.usecase.ConfigureSuLogUseCase
import anhiutangerinee.kittisu.domain.usecase.ControlAppUseCase
import anhiutangerinee.kittisu.domain.usecase.DeleteProfileTemplateUseCase
import anhiutangerinee.kittisu.domain.usecase.EnableSulogUseCase
import anhiutangerinee.kittisu.domain.usecase.EnqueueDownloadUseCase
import anhiutangerinee.kittisu.domain.usecase.EnqueueManagerUpdateUseCase
import anhiutangerinee.kittisu.domain.usecase.EnsureManagerInstalledUseCase
import anhiutangerinee.kittisu.domain.usecase.ExecuteFlashOperationUseCase
import anhiutangerinee.kittisu.domain.usecase.ExecuteModuleActionUseCase
import anhiutangerinee.kittisu.domain.usecase.ExportProfileTemplatesUseCase
import anhiutangerinee.kittisu.domain.usecase.ExtractModuleIdUseCase
import anhiutangerinee.kittisu.domain.usecase.ExtractModuleNameUseCase
import anhiutangerinee.kittisu.domain.usecase.FetchRemoteTextUseCase
import anhiutangerinee.kittisu.domain.usecase.GenerateBugreportUseCase
import anhiutangerinee.kittisu.domain.usecase.GetAppProfileUseCase
import anhiutangerinee.kittisu.domain.usecase.GetAppSepolicyUseCase
import anhiutangerinee.kittisu.domain.usecase.GetBooleanPreferenceUseCase
import anhiutangerinee.kittisu.domain.usecase.GetCatalogModuleUseCase
import anhiutangerinee.kittisu.domain.usecase.GetDefaultUmountModulesUseCase
import anhiutangerinee.kittisu.domain.usecase.GetHomeBasicInfoUseCase
import anhiutangerinee.kittisu.domain.usecase.GetInstallEnvironmentUseCase
import anhiutangerinee.kittisu.domain.usecase.GetKernelFeatureSettingsUseCase
import anhiutangerinee.kittisu.domain.usecase.GetKernelStatusUseCase
import anhiutangerinee.kittisu.domain.usecase.GetLastScriptRunUseCase
import anhiutangerinee.kittisu.domain.usecase.GetManagerRuntimeInfoUseCase
import anhiutangerinee.kittisu.domain.usecase.GetModuleReadmeUseCase
import anhiutangerinee.kittisu.domain.usecase.GetModuleReleaseDetailsUseCase
import anhiutangerinee.kittisu.domain.usecase.GetModuleUpdateChangelogUseCase
import anhiutangerinee.kittisu.domain.usecase.GetPlatformFeatureStatusUseCase
import anhiutangerinee.kittisu.domain.usecase.GetProfileTemplateUseCase
import anhiutangerinee.kittisu.domain.usecase.GetStringPreferenceUseCase
import anhiutangerinee.kittisu.domain.usecase.GetStringSetPreferenceUseCase
import anhiutangerinee.kittisu.domain.usecase.GetSuSFSStatusUseCase
import anhiutangerinee.kittisu.domain.usecase.GetSuperUserAppGroupUseCase
import anhiutangerinee.kittisu.domain.usecase.ImportAllowlistUseCase
import anhiutangerinee.kittisu.domain.usecase.ImportProfileTemplatesUseCase
import anhiutangerinee.kittisu.domain.usecase.InitializeApplicationUseCase
import anhiutangerinee.kittisu.domain.usecase.IsCatalogModuleCurrentUseCase
import anhiutangerinee.kittisu.domain.usecase.IsLateLoadModeUseCase
import anhiutangerinee.kittisu.domain.usecase.IsModuleUriAccessibleUseCase
import anhiutangerinee.kittisu.domain.usecase.IsNetworkAvailableUseCase
import anhiutangerinee.kittisu.domain.usecase.IsSoftRebootPreferredUseCase
import anhiutangerinee.kittisu.domain.usecase.IsSystemLanguageSettingsUseCase
import anhiutangerinee.kittisu.domain.usecase.LaunchSystemLanguageSettingsUseCase
import anhiutangerinee.kittisu.domain.usecase.LoadModuleIconUseCase
import anhiutangerinee.kittisu.domain.usecase.LoadSettingsPlatformUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveAssetDownloadUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveCatalogModulesUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveDownloadUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveDynamicManagerStateUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveInstalledModulesUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveKernelFlashUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveProfileTemplateOfflineUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveProfileTemplatesUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveRepositorySourcesUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveStartupStateUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveSulogStateUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveSuperUserStateUseCase
import anhiutangerinee.kittisu.domain.usecase.ObserveUmountStateUseCase
import anhiutangerinee.kittisu.domain.usecase.PeekOneshotScriptUseCase
import anhiutangerinee.kittisu.domain.usecase.PrepareScriptUseCase
import anhiutangerinee.kittisu.domain.usecase.RebootUseCase
import anhiutangerinee.kittisu.domain.usecase.RefreshDynamicManagerUseCase
import anhiutangerinee.kittisu.domain.usecase.RefreshInstalledModulesUseCase
import anhiutangerinee.kittisu.domain.usecase.RefreshModuleCatalogUseCase
import anhiutangerinee.kittisu.domain.usecase.RefreshProfileTemplatesUseCase
import anhiutangerinee.kittisu.domain.usecase.RefreshSulogUseCase
import anhiutangerinee.kittisu.domain.usecase.RefreshSuperUsersUseCase
import anhiutangerinee.kittisu.domain.usecase.RefreshUmountPathsUseCase
import anhiutangerinee.kittisu.domain.usecase.ReleaseScriptUseCase
import anhiutangerinee.kittisu.domain.usecase.RemoveModuleRepositoryUseCase
import anhiutangerinee.kittisu.domain.usecase.RemovePreferenceUseCase
import anhiutangerinee.kittisu.domain.usecase.RemoveUmountPathUseCase
import anhiutangerinee.kittisu.domain.usecase.RenameModuleRepositoryUseCase
import anhiutangerinee.kittisu.domain.usecase.ResolveModuleUpdateUseCase
import anhiutangerinee.kittisu.domain.usecase.RunScriptUseCase
import anhiutangerinee.kittisu.domain.usecase.SaveModuleActionLogUseCase
import anhiutangerinee.kittisu.domain.usecase.SaveProfileTemplateUseCase
import anhiutangerinee.kittisu.domain.usecase.SelectDynamicManagerUseCase
import anhiutangerinee.kittisu.domain.usecase.SetAppProfileUseCase
import anhiutangerinee.kittisu.domain.usecase.SetAppSepolicyUseCase
import anhiutangerinee.kittisu.domain.usecase.SetBooleanPreferenceUseCase
import anhiutangerinee.kittisu.domain.usecase.SetDefaultUmountModulesUseCase
import anhiutangerinee.kittisu.domain.usecase.SetKernelUmountEnabledUseCase
import anhiutangerinee.kittisu.domain.usecase.SetManualDynamicManagerUseCase
import anhiutangerinee.kittisu.domain.usecase.SetModuleEnabledUseCase
import anhiutangerinee.kittisu.domain.usecase.SetModuleRemovedUseCase
import anhiutangerinee.kittisu.domain.usecase.SetSelinuxHideEnabledUseCase
import anhiutangerinee.kittisu.domain.usecase.SetShellSuAdbOnlyEnabledUseCase
import anhiutangerinee.kittisu.domain.usecase.SetStringPreferenceUseCase
import anhiutangerinee.kittisu.domain.usecase.SetStringSetPreferenceUseCase
import anhiutangerinee.kittisu.domain.usecase.SetSuEnabledUseCase
import anhiutangerinee.kittisu.domain.usecase.StartKernelFlashUseCase
import anhiutangerinee.kittisu.domain.usecase.SuSFSConfigUseCase
import anhiutangerinee.kittisu.domain.usecase.TakeModuleUriPermissionUseCase
import anhiutangerinee.kittisu.domain.usecase.TransliterateTextUseCase
import anhiutangerinee.kittisu.domain.usecase.UpdateAppearanceUseCase
import anhiutangerinee.kittisu.domain.usecase.UpdateCachedModuleEnabledUseCase
import anhiutangerinee.kittisu.domain.usecase.UpdatePlatformSettingUseCase
import anhiutangerinee.kittisu.domain.usecase.ValidateSepolicyUseCase
import anhiutangerinee.kittisu.ui.activity.util.ThemeUtils
import anhiutangerinee.kittisu.ui.component.ZipFileDetector
import anhiutangerinee.kittisu.ui.theme.BackgroundManager
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.util.Shortcut
import anhiutangerinee.kittisu.ui.viewmodel.AppProfileViewModel
import anhiutangerinee.kittisu.ui.viewmodel.DynamicManagerViewModel
import anhiutangerinee.kittisu.ui.viewmodel.ExecuteModuleActionViewModel
import anhiutangerinee.kittisu.ui.viewmodel.FlashViewModel
import anhiutangerinee.kittisu.ui.viewmodel.HomeViewModel
import anhiutangerinee.kittisu.ui.viewmodel.InstallViewModel
import anhiutangerinee.kittisu.ui.viewmodel.KernelFlashViewModel
import anhiutangerinee.kittisu.ui.viewmodel.MainIntentViewModel
import anhiutangerinee.kittisu.ui.viewmodel.ModuleDetailViewModel
import anhiutangerinee.kittisu.ui.viewmodel.ModuleRepoViewModel
import anhiutangerinee.kittisu.ui.viewmodel.ModuleViewModel
import anhiutangerinee.kittisu.ui.viewmodel.RepositorySourcesViewModel
import anhiutangerinee.kittisu.ui.viewmodel.ScriptRunViewModel
import anhiutangerinee.kittisu.ui.viewmodel.SettingsViewModel
import anhiutangerinee.kittisu.ui.viewmodel.SuSFSViewModel
import anhiutangerinee.kittisu.ui.viewmodel.SulogViewModel
import anhiutangerinee.kittisu.ui.viewmodel.SuperUserViewModel
import anhiutangerinee.kittisu.ui.viewmodel.TemplateEditorViewModel
import anhiutangerinee.kittisu.ui.viewmodel.TemplateViewModel
import anhiutangerinee.kittisu.ui.viewmodel.UmountManagerScreenViewModel
import anhiutangerinee.kittisu.ui.webui.MonetColorsProvider
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val applicationScopeQualifier = named("applicationScope")

val coreModule = module {
    single<CoroutineScope>(applicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    single {
        OkHttpClient.Builder()
            .cache(Cache(File(androidApplication().cacheDir, "okhttp"), 10L * 1024L * 1024L))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "KittiSU/${BuildConfig.VERSION_CODE}")
                        .header("Accept-Language", Locale.getDefault().toLanguageTag())
                        .build(),
                )
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    single {
        val application = androidApplication()
        val iconSize = application.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        ImageLoader.Builder(application)
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(iconSize, false, application))
            }
            .build()
    }
}

val repositoryModule = module {
    single { KsuCliRepository(androidApplication()) }
    singleOf(::CountRepository)
    singleOf(::InstalledPackageCache)
    singleOf(::AppIconDataSource)
    singleOf(::RootServiceRepository)
    singleOf(::InstalledPackageRepository)
    single {
        SuperUserRepository(
            application = get(),
            cache = get(),
            installedPackageRepository = get(),
            profileRepository = get(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    single {
        AppSettingsRepository(
            context = androidApplication(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    singleOf(::StartupRepository)
    single {
        ApplicationInitializationRepository(
            application = get(),
            imageLoader = get(),
            applicationScope = get(applicationScopeQualifier),
            flashRepository = get(),
            ksuCliRepository = get(),
        )
    }
    singleOf(::ManagerUpdateRepository)
    singleOf(::ApplicationControlRepository)
    singleOf(::DownloadRepository)
    single { FlashRepository(get(), get(applicationScopeQualifier), get(), get()) }
    singleOf(::KernelRepository)
    singleOf(::HomeRuntimeRepository)
    singleOf(::HomeStateRepository)
    singleOf(::NetworkStatusRepository)
    singleOf(::NetworkRequestRepository)
    singleOf(::DynamicManagerRepository)
    singleOf(::SulogRepository)
    singleOf(::BugreportRepository)
    singleOf(::UmountRepository)
    singleOf(::ModuleCatalogRepository)
    single { ModuleReleaseRepository(get(), get(applicationScopeQualifier)) }
    singleOf(::ModuleIconRepository)
    single { KernelSuRegistryRepository(get(), get(), get(), get()) }
    singleOf(::RepositorySourceStore)
    singleOf(::ModuleRepository)
    singleOf(::ModulePreferencesRepository)
    singleOf(::ModuleActionRepository)
    single { ScriptRepository(androidApplication(), get(), get(applicationScopeQualifier)) }
    singleOf(::WebResourceRepository)
    singleOf(::WebUiRepository)
    singleOf(::ModuleFileRepository)
    singleOf(::ProfileRepository)
    singleOf(::ProfileTemplateRepository)
    singleOf(::SuSFSConfigHelper)
    singleOf(::SuSFSRepository)
    singleOf(::MonetCompatColorSource)
    singleOf(::ThemeRepository)
    single {
        val themeRepository = get<ThemeRepository>()
        ThemeConfig(themeRepository::defaultSeedColor)
    }
    singleOf(::CardConfig)
    singleOf(::BackgroundManager)
    singleOf(::ThemeUtils)
    singleOf(::LocaleHelper)
    singleOf(::LocaleRepository)
    singleOf(::SettingsPlatformRepository)
    singleOf(::ShortcutRepository)
    singleOf(::Shortcut)
    singleOf(::MonetColorsProvider)
    singleOf(::ZipFileDetector)
    single { HanziToPinyin.create() } bind TextTransliterator::class
}

val useCaseModule = module {
    factoryOf(::InitializeApplicationUseCase)
    factoryOf(::GetHomeBasicInfoUseCase)
    factoryOf(::IsNetworkAvailableUseCase)
    factoryOf(::LoadSettingsPlatformUseCase)
    factoryOf(::UpdateAppearanceUseCase)
    factoryOf(::UpdatePlatformSettingUseCase)
    factoryOf(::GetPlatformFeatureStatusUseCase)
    factoryOf(::IsSoftRebootPreferredUseCase)
    factoryOf(::CheckManagerUpdateUseCase)
    factoryOf(::EnsureManagerInstalledUseCase)
    factoryOf(::RebootUseCase)
    factoryOf(::EnqueueDownloadUseCase)
    factoryOf(::EnqueueManagerUpdateUseCase)
    factoryOf(::ObserveDownloadUseCase)
    factoryOf(::ObserveAssetDownloadUseCase)
    factoryOf(::GetKernelStatusUseCase)
    factoryOf(::GetInstallEnvironmentUseCase)
    factoryOf(::ExecuteFlashOperationUseCase)
    factoryOf(::CheckFlashModuleMountUseCase)
    factoryOf(::GetManagerRuntimeInfoUseCase)
    factoryOf(::GetKernelFeatureSettingsUseCase)
    factoryOf(::SetSuEnabledUseCase)
    factoryOf(::SetKernelUmountEnabledUseCase)
    factoryOf(::SetShellSuAdbOnlyEnabledUseCase)
    factoryOf(::ConfigureSuLogUseCase)
    factoryOf(::SetSelinuxHideEnabledUseCase)
    factoryOf(::SetDefaultUmountModulesUseCase)
    factoryOf(::IsLateLoadModeUseCase)
    factoryOf(::GetAppProfileUseCase)
    factoryOf(::SetAppProfileUseCase)
    factoryOf(::GetAppSepolicyUseCase)
    factoryOf(::SetAppSepolicyUseCase)
    factoryOf(::ControlAppUseCase)
    factoryOf(::ValidateSepolicyUseCase)
    factoryOf(::GetDefaultUmountModulesUseCase)
    factoryOf(::GetSuSFSStatusUseCase)
    factoryOf(::SuSFSConfigUseCase)
    factoryOf(::ApplyLanguageUseCase)
    factoryOf(::IsSystemLanguageSettingsUseCase)
    factoryOf(::LaunchSystemLanguageSettingsUseCase)
    factoryOf(::GenerateBugreportUseCase)
    factoryOf(::ObserveStartupStateUseCase)
    factoryOf(::GetSuperUserAppGroupUseCase)
    factoryOf(::ObserveCatalogModulesUseCase)
    factoryOf(::ObserveModuleCatalogRefreshingUseCase)
    factoryOf(::RefreshModuleCatalogUseCase)
    factoryOf(::GetCatalogModuleUseCase)
    factoryOf(::IsCatalogModuleCurrentUseCase)
    factoryOf(::GetModuleReadmeUseCase)
    factoryOf(::GetModuleReleaseDetailsUseCase)
    factoryOf(::LoadModuleIconUseCase)
    factoryOf(::ResolveModuleUpdateUseCase)
    factoryOf(::GetModuleUpdateChangelogUseCase)
    factoryOf(::ObserveRepositorySourcesUseCase)
    factoryOf(::AddModuleRepositoryUseCase)
    factoryOf(::RenameModuleRepositoryUseCase)
    factoryOf(::RemoveModuleRepositoryUseCase)
    factoryOf(::ObserveProfileTemplatesUseCase)
    factoryOf(::ObserveProfileTemplateRefreshingUseCase)
    factoryOf(::ObserveProfileTemplateOfflineUseCase)
    factoryOf(::RefreshProfileTemplatesUseCase)
    factoryOf(::GetProfileTemplateUseCase)
    factoryOf(::SaveProfileTemplateUseCase)
    factoryOf(::DeleteProfileTemplateUseCase)
    factoryOf(::ImportProfileTemplatesUseCase)
    factoryOf(::ExportProfileTemplatesUseCase)
    factoryOf(::GetBooleanPreferenceUseCase)
    factoryOf(::SetBooleanPreferenceUseCase)
    factoryOf(::GetStringPreferenceUseCase)
    factoryOf(::SetStringPreferenceUseCase)
    factoryOf(::GetStringSetPreferenceUseCase)
    factoryOf(::SetStringSetPreferenceUseCase)
    factoryOf(::ObserveDynamicManagerStateUseCase)
    factoryOf(::RefreshDynamicManagerUseCase)
    factoryOf(::SelectDynamicManagerUseCase)
    factoryOf(::SetManualDynamicManagerUseCase)
    factoryOf(::ClearDynamicManagerUseCase)
    factoryOf(::ObserveSulogStateUseCase)
    factoryOf(::RefreshSulogUseCase)
    factoryOf(::EnableSulogUseCase)
    factoryOf(::CleanSulogUseCase)
    factoryOf(::ObserveUmountStateUseCase)
    factoryOf(::RefreshUmountPathsUseCase)
    factoryOf(::AddUmountPathUseCase)
    factoryOf(::RemoveUmountPathUseCase)
    factoryOf(::ObserveKernelFlashUseCase)
    factoryOf(::StartKernelFlashUseCase)
    factoryOf(::RemovePreferenceUseCase)
    factoryOf(::ObserveSuperUserStateUseCase)
    factoryOf(::RefreshSuperUsersUseCase)
    factoryOf(::BackupAllowlistUseCase)
    factoryOf(::ImportAllowlistUseCase)
    factoryOf(::FetchRemoteTextUseCase)
    factoryOf(::IsModuleUriAccessibleUseCase)
    factoryOf(::TakeModuleUriPermissionUseCase)
    factoryOf(::ExtractModuleNameUseCase)
    factoryOf(::ExtractModuleIdUseCase)
    factoryOf(::ObserveInstalledModulesUseCase)
    factoryOf(::RefreshInstalledModulesUseCase)
    factoryOf(::CalculateInstalledModuleSizeUseCase)
    factoryOf(::UpdateCachedModuleEnabledUseCase)
    factoryOf(::ExecuteModuleActionUseCase)
    factoryOf(::PeekOneshotScriptUseCase)
    factoryOf(::PrepareScriptUseCase)
    factoryOf(::ReleaseScriptUseCase)
    factoryOf(::RunScriptUseCase)
    factoryOf(::GetLastScriptRunUseCase)
    factoryOf(::SaveModuleActionLogUseCase)
    factoryOf(::SetModuleEnabledUseCase)
    factoryOf(::SetModuleRemovedUseCase)
    factoryOf(::TransliterateTextUseCase)
}

val viewModelModule = module {
    viewModel { parameters -> ScriptRunViewModel(parameters[0], get(), get(), get(), get(), get()) }
    viewModel { parameters ->
        AppProfileViewModel(
            uid = parameters[0],
            packageName = parameters[1],
            getAppGroup = get(),
            getProfile = get(),
            getDefaultUmountModules = get(),
            setProfile = get(),
            getSepolicy = get(),
            setSepolicy = get(),
            controlApp = get(),
            validateSepolicy = get(),
        )
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::InstallViewModel)
    viewModelOf(::MainIntentViewModel)
    viewModelOf(::KernelFlashViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ModuleViewModel)
    viewModelOf(::SuperUserViewModel)
    viewModelOf(::SuSFSViewModel)
    viewModel { parameters -> ModuleRepoViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), parameters.getOrNull<String>()) }
    viewModel { parameters -> ModuleDetailViewModel(parameters[0], parameters[1], get(), get(), get(), get(), get(), get(), get()) }
    viewModelOf(::RepositorySourcesViewModel)
    viewModelOf(::TemplateViewModel)
    viewModel { parameters ->
        TemplateEditorViewModel(
            templateId = parameters[0],
            readOnly = parameters[1],
            isCreation = parameters[2],
            getTemplate = get(),
            saveTemplate = get(),
            deleteTemplate = get(),
        )
    }
    viewModelOf(::SulogViewModel)
    viewModelOf(::DynamicManagerViewModel)
    viewModelOf(::FlashViewModel)
    viewModelOf(::UmountManagerScreenViewModel)
    viewModel { parameters ->
        ExecuteModuleActionViewModel(
            moduleId = parameters[0],
            executeModuleAction = get(),
            saveModuleActionLog = get(),
        )
    }
}

val appModules = listOf(coreModule, repositoryModule, useCaseModule, viewModelModule)
