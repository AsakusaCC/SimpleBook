package com.ebookreader.simplebook

import android.app.Application
import com.ebookreader.simplebook.di.appModule
import com.ebookreader.simplebook.di.dataModule
import com.ebookreader.simplebook.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SimpleBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SimpleBookApp)
            modules(appModule, dataModule, platformModule)
        }
    }
}
