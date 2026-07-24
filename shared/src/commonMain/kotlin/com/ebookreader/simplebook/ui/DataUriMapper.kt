package com.ebookreader.simplebook.ui

import coil3.map.Mapper
import coil3.request.Options
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 把 base64 data: URI（如 "data:image/jpeg;base64,..."）映射为 [ByteArray]，交给 Coil3 的
 * ByteArrayFetcher + 图片 Decoder 解码。
 *
 * 存在原因：Coil3（3.x）未内置 data: URI 的 fetcher（Coil2 的 DataUriFetcher 未移植到 Coil3），
 * 而本阅读器把 EPUB 内嵌图片转成 base64 data: URI 传给 AsyncImage。此 Mapper 恢复该支持，
 * 跨 Android + 桌面生效。仅处理 base64 形式（[ReaderViewModel.resolveImageReferences] 一律产出 base64）。
 */
@OptIn(ExperimentalEncodingApi::class)
class DataUriMapper : Mapper<String, ByteArray> {
    override fun map(data: String, options: Options): ByteArray? {
        if (!data.startsWith("data:", ignoreCase = true)) return null
        val commaIdx = data.indexOf(',')
        if (commaIdx < 0) return null
        // "data:" 后到 "," 前是 media type（可含 ";base64"）
        val mediaPart = data.substring(5, commaIdx)
        if (!mediaPart.contains("base64", ignoreCase = true)) return null
        return try {
            Base64.decode(data.substring(commaIdx + 1).encodeToByteArray())
        } catch (_: IllegalArgumentException) {
            // 畸形 base64：返回 null 让 Coil 链继续、优雅降级，而非崩溃
            null
        }
    }
}
