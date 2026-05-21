package com.ebookreader.simplebook.ui.reader

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

@Composable
fun EpubReaderView(
    htmlContent: String,
    onScrollPercentageChanged: (Float) -> Unit,
    onChapterFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.builtInZoomControls = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Inject scroll tracking JS
                        view?.evaluateJavascript(
                            """
                            window.onscroll = function() {
                                var scrollHeight = document.documentElement.scrollHeight - document.documentElement.clientHeight;
                                var percentage = scrollHeight > 0 ? window.scrollY / scrollHeight : 0;
                                Android.onScrollPositionChanged(percentage);
                            };
                            """.trimIndent(),
                            null
                        )
                    }
                }
                addJavascriptInterface(
                    object {
                        @android.webkit.JavascriptInterface
                        fun onScrollPositionChanged(percentage: Float) {
                            onScrollPercentageChanged(percentage)
                        }
                    },
                    "Android"
                )
                webView = this
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            view.loadDataWithBaseURL(
                null,
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}
