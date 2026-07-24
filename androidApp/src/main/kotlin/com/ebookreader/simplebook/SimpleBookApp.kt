package com.ebookreader.simplebook

import android.app.Application
import com.ebookreader.simplebook.di.appModule
import com.ebookreader.simplebook.di.dataModule
import com.ebookreader.simplebook.di.platformModule
import com.ebookreader.simplebook.ui.setupImageLoader
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SimpleBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SimpleBookApp)
            modules(appModule, dataModule, platformModule)
        }
        // 注册全局 Coil ImageLoader（DataUriMapper + SvgDecoder）。Android 不走 shared 的 App()，
        // 必须在进程入口注册，否则 data URI 图片无法解码（Coil3 无内置 data: URI fetcher）。
        setupImageLoader()
    }
}
