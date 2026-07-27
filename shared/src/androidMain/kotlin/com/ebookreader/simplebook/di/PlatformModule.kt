package com.ebookreader.simplebook.di

import com.ebookreader.simplebook.data.local.DatabaseMigrations
import com.ebookreader.simplebook.data.local.SimpleBookDatabase
import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.data.local.getRoomDatabaseBuilder
import com.ebookreader.simplebook.data.remote.AndroidDriveCredential
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.data.remote.GoogleDriveClient
import com.ebookreader.simplebook.platform.AuthProvider
import com.ebookreader.simplebook.platform.ForegroundSyncController
import com.ebookreader.simplebook.platform.SyncPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformModule: Module = module {
    // Android application Context is provided by `startKoin { androidContext(ctx) }` in
    // SimpleBookApp. Retrieve it here via the androidContext() function (or get<Context>()).
    // Do NOT re-register it as `single { androidContext() }`: that lambda calls androidContext(),
    // which resolves Context back to this very single → infinite recursion → StackOverflowError
    // (surfaced once Android was first run on-device — the app crashed on startup).

    // Android-specific: Auth
    singleOf(::AuthManager)
    singleOf(::AuthProvider)
    single {
        val context = androidContext()
        val authManager = get<AuthManager>()
        GoogleDriveClient {
            authManager.signedInAccount.value?.let { account ->
                AndroidDriveCredential(context, account)
            }
        }
    }

    // Android-specific: foreground sync notification service controller
    singleOf(::ForegroundSyncController)

    // Android-specific: Settings (DataStore) — uses Koin to get Context internally
    singleOf(::SettingsDataStore)
    singleOf(::SyncPreferences)

    // Override database with migrations (Android-only)
    single<SimpleBookDatabase> {
        getRoomDatabaseBuilder()
            .addMigrations(
                DatabaseMigrations.MIGRATION_1_2,
                DatabaseMigrations.MIGRATION_2_3,
                DatabaseMigrations.MIGRATION_3_4
            )
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()
    }
}
