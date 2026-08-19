package com.ebookreader.simplebook.data.parser

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

/**
 * PDF 文档渲染接口。固定版式：页面按请求宽度渲染为位图（ImageBitmap 为 common 类型，
 * 平台实现内部负责 Bitmap/BufferedImage → ImageBitmap 转换）。
 *
 * 实现约定：
 * - [renderPage] / [pageSizePts] 是阻塞 IO/计算，调用方负责调度到后台线程（见 PdfPageLoader）；
 * - 实现内部需自行保证线程安全（同一实例的并发调用）；
 * - 0 ≤ index < pageCount，越界行为未定义（调用方已校验）。
 */
interface PdfDocument : AutoCloseable {
    val pageCount: Int

    /** 第 index 页的原始尺寸（PDF point，1/72 inch）。 */
    fun pageSizePts(index: Int): Size

    /** 以 widthPx 宽度渲染整页，返回位图（高度按页宽高比）。 */
    fun renderPage(index: Int, widthPx: Int): ImageBitmap
}

/** 打开 PDF；损坏 / 加密 / IO 失败返回 null。 */
expect fun openPdf(file: File): PdfDocument?

/** 编码为 PNG 字节；失败返回 null。用于封面落盘。 */
expect fun ImageBitmap.encodeToPngBytes(): ByteArray?
