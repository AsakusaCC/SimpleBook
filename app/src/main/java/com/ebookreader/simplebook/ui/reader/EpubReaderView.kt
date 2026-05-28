package com.ebookreader.simplebook.ui.reader

import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val TAG = "EpubReaderView"

@Composable
fun EpubReaderView(
    htmlContent: String,
    onScrollPercentageChanged: (Float) -> Unit,
    onChapterFinished: () -> Unit,
    backgroundColor: Long = 0xFFFFFFFF,
    textColor: Long = 0xFF000000,
    accentColor: Long = 0xFF6750A4,
    fontSize: Float = 16f,
    lineHeight: Float = 1.5f,
    hasNextChapter: Boolean = true,
    nextChapterText: String = "下一章 →",
    allReadText: String = "已读完全部章节",
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    val styledContent = remember(htmlContent, backgroundColor, textColor, accentColor, fontSize, lineHeight, hasNextChapter, nextChapterText, allReadText) {
        buildStyledHtml(htmlContent, backgroundColor, textColor, accentColor, fontSize, lineHeight, hasNextChapter, nextChapterText, allReadText)
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    builtInZoomControls = false
                    allowFileAccess = true
                    allowContentAccess = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(
                            """
                            (function() {
                                var touchStartX = 0, touchStartY = 0, touchStartTime = 0;
                                document.onscroll = function() {
                                    var scrollHeight = document.documentElement.scrollHeight - document.documentElement.clientHeight;
                                    var percentage = scrollHeight > 0 ? window.scrollY / scrollHeight : 0;
                                    Android.onScrollPositionChanged(percentage);
                                };
                                document.onscroll();
                                document.addEventListener('touchstart', function(e) {
                                    touchStartX = e.touches[0].clientX;
                                    touchStartY = e.touches[0].clientY;
                                    touchStartTime = Date.now();
                                }, {passive: true});
                                document.addEventListener('touchend', function(e) {
                                    var dx = Math.abs(e.changedTouches[0].clientX - touchStartX);
                                    var dy = Math.abs(e.changedTouches[0].clientY - touchStartY);
                                    var dt = Date.now() - touchStartTime;
                                    if (dx < 20 && dy < 20 && dt < 300) {
                                        Android.onTap();
                                    }
                                }, {passive: true});
                            })();
                            """.trimIndent(),
                            null
                        )
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        Log.e(TAG, "WebView error: code=$errorCode desc=$description")
                        super.onReceivedError(view, errorCode, description, failingUrl)
                    }
                }
                addJavascriptInterface(
                    object {
                        @android.webkit.JavascriptInterface
                        fun onScrollPositionChanged(percentage: Float) {
                            onScrollPercentageChanged(percentage)
                        }
                        @android.webkit.JavascriptInterface
                        fun onTap() {
                            onTap()
                        }
                        @android.webkit.JavascriptInterface
                        fun nextChapter() {
                            onChapterFinished()
                        }
                    },
                    "Android"
                )
                webView = this
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            view.loadDataWithBaseURL(null, styledContent, "text/html; charset=utf-8", "UTF-8", null)
        }
    )
}

private fun buildStyledHtml(
    html: String,
    bgColor: Long,
    textColor: Long,
    accentColor: Long,
    fontSize: Float,
    lineHeight: Float,
    hasNextChapter: Boolean,
    nextChapterText: String,
    allReadText: String
): String {
    val bgColorHex = String.format("#%06X", 0x00FFFFFF and bgColor.toInt())
    val textColorHex = String.format("#%06X", 0x00FFFFFF and textColor.toInt())
    val accentColorHex = String.format("#%06X", 0x00FFFFFF and accentColor.toInt())

    // Strip XML declaration and DOCTYPE
    var cleaned = html
        .replace(Regex("<\\?xml[^?]*\\?>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE), "")

    // Extract <body> content
    val bodyPatterns = listOf(
        Regex("(?i)<body[^>]*>(.*)</body>", RegexOption.DOT_MATCHES_ALL),
        Regex("(?i)<body[^>]*>(.*)</html>", RegexOption.DOT_MATCHES_ALL),
        Regex("(?i)<body[^>]*>(.*)", RegexOption.DOT_MATCHES_ALL)
    )

    var bodyContent: String? = null
    for (pattern in bodyPatterns) {
        val match = pattern.find(cleaned)
        if (match != null) {
            val extracted = match.groupValues[1].trim()
            if (extracted.isNotEmpty() && extracted.any { it.isLetterOrDigit() }) {
                bodyContent = extracted
                break
            }
        }
    }

    // Fallback to plain text if body extraction failed
    if (bodyContent == null) {
        val plainText = cleaned
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
        bodyContent = if (plainText.isNotBlank()) {
            "<p>${plainText.replace("\n", "</p><p>")}</p>"
        } else {
            "<p></p>"
        }
    }

    // Clean XHTML namespace attributes
    bodyContent = bodyContent
        .replace(Regex("\\s+xmlns[^\"]*\"[^\"]*\""), "")
        .replace(Regex("\\s+xml:lang=\"[^\"]*\""), "")
        .replace(Regex("\\s+xmlns:\\w+=\"[^\"]*\""), "")

    // Next chapter navigation link
    val nextChapterHtml = if (hasNextChapter) {
        """<div style="text-align:center;padding:32px 16px;border-top:1px solid #e0e0e0;margin-top:40px;">
        <a href="javascript:void(0)" onclick="Android.nextChapter()" style="font-size:16px;color:$accentColorHex;text-decoration:none;padding:12px 32px;display:inline-block;">$nextChapterText</a>
        </div>"""
    } else {
        """<div style="text-align:center;padding:32px 16px;color:#999;font-size:14px;">$allReadText</div>"""
    }

    return """<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{color:$textColorHex;background-color:$bgColorHex;font-size:${fontSize.toInt()}px;line-height:$lineHeight;padding:16px;margin:0;word-wrap:break-word;-webkit-text-size-adjust:100%;}
img{max-width:100%;height:auto;display:block;margin:8px auto;}
svg{max-width:100%;height:auto;}
p,div,h1,h2,h3,h4,h5,h6,span,li,td,th{color:inherit;}
</style></head><body>$bodyContent$nextChapterHtml</body></html>"""
}
