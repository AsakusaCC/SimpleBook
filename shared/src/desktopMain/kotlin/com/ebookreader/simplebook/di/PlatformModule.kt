package com.ebookreader.simplebook.di

import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.data.remote.DesktopDriveCredential
import com.ebookreader.simplebook.data.remote.GoogleDriveClient
import com.ebookreader.simplebook.platform.AuthProvider
import com.ebookreader.simplebook.platform.ForegroundSyncController
import com.ebookreader.simplebook.platform.SyncPreferences
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformModule: Module = module {
    // Desktop-specific: Settings (Properties-based)
    singleOf(::SettingsDataStore)
    // SyncPreferences 有 internal(File) 测试缝隙构造器，::SyncPreferences 重载歧义，
    // 故用 single { ... } 显式调用无参构造器（生产环境语义）。
    single { SyncPreferences() }

    // Desktop-specific: OAuth 2.0 PKCE auth provider
    singleOf(::AuthProvider)

    // Desktop-specific: foreground sync controller (no-op)
    singleOf(::ForegroundSyncController)

    // Desktop-specific: GoogleDriveClient using OAuth bearer token
    single {
        val authProvider = get<AuthProvider>()
        GoogleDriveClient {
            authProvider.getAccessToken()?.let { token ->
                DesktopDriveCredential(token)
            }
        }
    }
}
