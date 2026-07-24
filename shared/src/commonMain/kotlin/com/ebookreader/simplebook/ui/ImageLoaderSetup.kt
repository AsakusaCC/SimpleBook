package com.ebookreader.simplebook.ui

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder

/**
 * 注册全局 ImageLoader，启用 SVG 解码（SvgDecoder）。
 *
 * 幂等：可重复调用。在 App 启动时调一次（见 [com.ebookreader.simplebook.App]）。
 * Android 后端 = androidsvg，桌面 JVM 后端 = Skiko（均由 coil-svg 提供）。
 */
fun setupImageLoader() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context).components {
            add(DataUriMapper())       // data: URI → ByteArray（恢复 Coil3 未内置的 data URI 支持）
            add(SvgDecoder.Factory())  // SVG 矢量解码
        }.build()
    }
}
